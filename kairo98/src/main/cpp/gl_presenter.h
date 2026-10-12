// GPU presenter for the guest screen.
//
// Runs entirely on the presenter thread. It owns an EGL context on the current
// ANativeWindow, keeps a CPU mirror of the latest complete screen state so a
// recreated surface can be repainted, and composes the PC-98 picture in a
// fragment shader from the core's 8-bit index planes, a per-scanline palette
// slot map, and the RGB565 palette slots recorded by android_host/gpudraw.c.
// Frames the core converted on the CPU (mode -1) are shown from an RGB565
// texture through the same scaling. Scaling reproduces the CPU presenter's
// nearest-neighbour mapping: source x = x * 640 / width, source y = y * 400 /
// height, in integer arithmetic.

#pragma once

#include "kairo/egl_context.h"
#include "kairo/slang_filter.h"
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window.h>
#include <array>
#include <cstring>
#include <string>
#include <vector>

#include "android_host/gpudraw.h"

class GlPresenter {
public:
    ~GlPresenter() { detach(); }

    // Takes its own reference on the window; safe to call with nullptr.
    bool attach(ANativeWindow *window) {
        detach();
        if (!window) return false;
        window_ = window;
        if (!init()) {
            detach();
            return false;
        }
        return true;
    }

    void detach() {
        if (egl_.ready() && egl_.makeCurrent()) {
            filter_.reset();
            if (composed_framebuffer_) glDeleteFramebuffers(1, &composed_framebuffer_);
            if (composed_texture_) glDeleteTextures(1, &composed_texture_);
            if (program_) glDeleteProgram(program_);
            glDeleteTextures(5, textures_);
        }
        egl_.destroy();
        display_ = EGL_NO_DISPLAY;
        surface_ = EGL_NO_SURFACE;
        program_ = 0;
        composed_framebuffer_ = composed_texture_ = 0;
        pixel_unpack_dirty_ = true;
        std::memset(textures_, 0, sizeof(textures_));
        window_ = nullptr;
    }

    void setShaderPreset(std::string path) {
        if (shader_path_ == path) return;
        shader_path_ = std::move(path);
        if (!ready()) return;
        filter_.reset();
        pixel_unpack_dirty_ = true;
        if (!shader_path_.empty() && create_composed_texture()) filter_.load(shader_path_);
    }

    bool ready() const { return egl_.ready(); }

    // Merges a frame packet into the mirror and uploads what changed.
    void apply(const kairo98_gpu_frame_t &frame) {
        if (ready() && pixel_unpack_dirty_) {
            prepare_uploads();
            pixel_unpack_dirty_ = false;
        }
        const bool mode_changed = frame.mode != mirror_mode_;
        mirror_mode_ = frame.mode;
        mirror_base_ = frame.base;
        if (frame.mode < 0) {
            std::memcpy(mirror_rgb_.data(), frame.rgb, sizeof(frame.rgb));
            if (ready()) upload_rgb();
            return;
        }
        for (int slot_index = 0; slot_index < frame.palette_updates; ++slot_index) {
            const auto &update = frame.palette[slot_index];
            std::memcpy(mirror_palette_[update.slot].data(), update.entries,
                        sizeof(update.entries));
            if (ready()) upload_palette_slot(update.slot);
        }
        std::memcpy(mirror_linepal_.data(), frame.line_palette, sizeof(frame.line_palette));
        for (int y = 0; y < KAIRO98_GPU_ROWS; ++y) {
            if (!frame.row_dirty[y]) continue;
            if (frame.row_dirty[y] & KAIRO98_GPU_ROW_TEXT)
                std::memcpy(mirror_text_[y].data(), frame.text[y], KAIRO98_GPU_COLS);
            if (frame.row_dirty[y] & KAIRO98_GPU_ROW_GRPH)
                std::memcpy(mirror_grph_[y].data(), frame.grph[y], KAIRO98_GPU_COLS);
        }
        if (!ready()) return;
        if (frame.full || mode_changed) {
            upload_planes_all();
        } else {
            upload_rows(frame.row_dirty, frame.mode);
        }
        upload_linepal();
    }

    // Verification: recompute the shader's composition on the CPU for the
    // rows a frame redrew and compare with the CPU-converted pixels the
    // packet carries. Returns the number of differing pixels.
    unsigned long long verify(const kairo98_gpu_frame_t &frame) const {
        if (frame.mode < 0) return 0;
        unsigned long long bad = 0;
        for (int y = 0; y < KAIRO98_GPU_ROWS; ++y) {
            if (!frame.row_dirty[y]) continue;
            const auto &palette = mirror_palette_[mirror_linepal_[y]];
            for (int x = 0; x < KAIRO98_GPU_COLS; ++x) {
                int idx = mirror_base_;
                if (mirror_mode_ & 1) idx += mirror_text_[y][x];
                if (mirror_mode_ & 2) idx += mirror_grph_[y][x];
                if (palette[idx] != frame.rgb[y * KAIRO98_GPU_COLS + x]) ++bad;
            }
        }
        return bad;
    }

    // Uploads the whole mirror; used after the context is (re)created.
    void upload_all() {
        if (!ready()) return;
        prepare_uploads();
        pixel_unpack_dirty_ = false;
        upload_rgb();
        for (int slot = 0; slot < KAIRO98_GPU_PALETTE_SLOTS; ++slot) upload_palette_slot(slot);
        upload_planes_all();
        upload_linepal();
    }

    bool draw() {
        if (!ready()) return false;
        EGLint width = 0, height = 0;
        eglQuerySurface(display_, surface_, EGL_WIDTH, &width);
        eglQuerySurface(display_, surface_, EGL_HEIGHT, &height);
        if (width <= 0 || height <= 0) return false;
        if (filter_.active()) {
            compose(KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, composed_framebuffer_);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            if (!filter_.draw(composed_texture_, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS,
                              width, height)) compose(width, height, 0);
            pixel_unpack_dirty_ = true;
        } else compose(width, height, 0);
        if (!eglSwapBuffers(display_, surface_)) {
            __android_log_print(ANDROID_LOG_WARN, "Kairo98Perf", "eglSwapBuffers failed: 0x%x",
                                eglGetError());
            return false;
        }
        return true;
    }

private:
    bool init() {
        if (!egl_.create(3, 0, EGL_NO_CONTEXT, true) || !egl_.attach(window_))
            return fail("EGL context/surface");
        display_ = egl_.display();
        surface_ = egl_.surface();
        if (!build_program()) return false;
        create_textures();
        upload_all();
        if (!shader_path_.empty() && create_composed_texture()) filter_.load(shader_path_);
        return true;
    }

    bool create_composed_texture() {
        if (composed_framebuffer_) return true;
        glGenTextures(1, &composed_texture_);
        glBindTexture(GL_TEXTURE_2D, composed_texture_);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS,
                     0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        nearest();
        glGenFramebuffers(1, &composed_framebuffer_);
        glBindFramebuffer(GL_FRAMEBUFFER, composed_framebuffer_);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                               composed_texture_, 0);
        const bool complete = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (!complete) {
            glDeleteFramebuffers(1, &composed_framebuffer_);
            glDeleteTextures(1, &composed_texture_);
            composed_framebuffer_ = composed_texture_ = 0;
        }
        return complete;
    }

    void compose(int width, int height, GLuint framebuffer) {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, width, height);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_STENCIL_TEST);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glBindVertexArray(0);
        glUseProgram(program_);
        glUniform1i(mode_location_, mirror_mode_);
        glUniform1i(base_location_, mirror_base_);
        glUniform2i(surface_location_, width, height);
        for (int unit = 0; unit < 5; ++unit) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindSampler(unit, 0);
            glBindTexture(GL_TEXTURE_2D, textures_[unit]);
        }
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    bool fail(const char *what) {
        __android_log_print(ANDROID_LOG_WARN, "Kairo98Perf", "GL presenter: %s failed: 0x%x",
                            what, eglGetError());
        return false;
    }

    static GLuint compile(GLenum type, const char *source) {
        GLuint shader = glCreateShader(type);
        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);
        GLint ok = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
        if (!ok) {
            char log[512] = {0};
            glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
            __android_log_print(ANDROID_LOG_WARN, "Kairo98Perf", "shader: %s", log);
            glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    bool build_program() {
        static const char *vertex =
            "#version 300 es\n"
            "void main() {\n"
            "  vec2 p = vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0);\n"
            "  gl_Position = vec4(p, 0.0, 1.0);\n"
            "}\n";
        static const char *fragment =
            "#version 300 es\n"
            "precision highp float;\n"
            "precision highp int;\n"
            "uniform highp usampler2D uText;\n"
            "uniform highp usampler2D uGrph;\n"
            "uniform highp usampler2D uLinePal;\n"
            "uniform sampler2D uPalette;\n"
            "uniform sampler2D uRgb;\n"
            "uniform int uMode;\n"
            "uniform int uBase;\n"
            "uniform ivec2 uSurface;\n"
            "out vec4 oColor;\n"
            "void main() {\n"
            "  ivec2 s = ivec2(int(gl_FragCoord.x), uSurface.y - 1 - int(gl_FragCoord.y));\n"
            "  ivec2 g = ivec2((s.x * 640) / uSurface.x, (s.y * 400) / uSurface.y);\n"
            "  if (uMode < 0) { oColor = texelFetch(uRgb, g, 0); return; }\n"
            "  int idx = uBase;\n"
            "  if ((uMode & 1) != 0) idx += int(texelFetch(uText, g, 0).r);\n"
            "  if ((uMode & 2) != 0) idx += int(texelFetch(uGrph, g, 0).r);\n"
            "  int pal = int(texelFetch(uLinePal, ivec2(g.y, 0), 0).r);\n"
            "  oColor = texelFetch(uPalette, ivec2(idx, pal), 0);\n"
            "}\n";
        GLuint vs = compile(GL_VERTEX_SHADER, vertex);
        GLuint fs = compile(GL_FRAGMENT_SHADER, fragment);
        if (!vs || !fs) return false;
        program_ = glCreateProgram();
        glAttachShader(program_, vs);
        glAttachShader(program_, fs);
        glLinkProgram(program_);
        glDeleteShader(vs);
        glDeleteShader(fs);
        GLint ok = 0;
        glGetProgramiv(program_, GL_LINK_STATUS, &ok);
        if (!ok) {
            char log[512] = {0};
            glGetProgramInfoLog(program_, sizeof(log), nullptr, log);
            __android_log_print(ANDROID_LOG_WARN, "Kairo98Perf", "program: %s", log);
            return false;
        }
        glUseProgram(program_);
        glUniform1i(glGetUniformLocation(program_, "uText"), 0);
        glUniform1i(glGetUniformLocation(program_, "uGrph"), 1);
        glUniform1i(glGetUniformLocation(program_, "uLinePal"), 2);
        glUniform1i(glGetUniformLocation(program_, "uPalette"), 3);
        glUniform1i(glGetUniformLocation(program_, "uRgb"), 4);
        mode_location_ = glGetUniformLocation(program_, "uMode");
        base_location_ = glGetUniformLocation(program_, "uBase");
        surface_location_ = glGetUniformLocation(program_, "uSurface");
        return true;
    }

    static void nearest() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    static void prepare_uploads() {
        // The filter chain uses the same GL context and can leave pixel-transfer
        // state behind. Guest planes are tightly packed client-memory arrays.
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
    }

    void create_textures() {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glGenTextures(5, textures_);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textures_[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8UI, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, 0,
                     GL_RED_INTEGER, GL_UNSIGNED_BYTE, nullptr);
        nearest();
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, textures_[1]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8UI, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, 0,
                     GL_RED_INTEGER, GL_UNSIGNED_BYTE, nullptr);
        nearest();
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, textures_[2]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8UI, KAIRO98_GPU_ROWS, 1, 0,
                     GL_RED_INTEGER, GL_UNSIGNED_BYTE, nullptr);
        nearest();
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, textures_[3]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB565, KAIRO98_GPU_PALETTE_ENTRIES,
                     KAIRO98_GPU_PALETTE_SLOTS, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, nullptr);
        nearest();
        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, textures_[4]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB565, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, 0,
                     GL_RGB, GL_UNSIGNED_SHORT_5_6_5, nullptr);
        nearest();
    }

    void upload_rgb() {
        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, textures_[4]);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, mirror_rgb_.data());
    }

    void upload_palette_slot(int slot) {
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, textures_[3]);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, slot, KAIRO98_GPU_PALETTE_ENTRIES, 1, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, mirror_palette_[slot].data());
    }

    void upload_linepal() {
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, textures_[2]);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, KAIRO98_GPU_ROWS, 1, GL_RED_INTEGER,
                        GL_UNSIGNED_BYTE, mirror_linepal_.data());
    }

    void upload_plane_rows(int unit, GLuint texture, const std::vector<std::array<unsigned char, KAIRO98_GPU_COLS>> &plane,
                           int first, int count) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, first, KAIRO98_GPU_COLS, count, GL_RED_INTEGER,
                        GL_UNSIGNED_BYTE, plane[first].data());
    }

    void upload_planes_all() {
        upload_plane_rows(0, textures_[0], mirror_text_, 0, KAIRO98_GPU_ROWS);
        upload_plane_rows(1, textures_[1], mirror_grph_, 0, KAIRO98_GPU_ROWS);
    }

    // Uploads contiguous runs of dirty rows; rows in the mirror are stored
    // consecutively so a run is one glTexSubImage2D call.
    void upload_rows(const unsigned char *dirty, int mode) {
        int y = 0;
        while (y < KAIRO98_GPU_ROWS) {
            if (!dirty[y]) { ++y; continue; }
            int end = y;
            while (end < KAIRO98_GPU_ROWS && dirty[end]) ++end;
            if (mode & 1) upload_plane_rows(0, textures_[0], mirror_text_, y, end - y);
            if (mode & 2) upload_plane_rows(1, textures_[1], mirror_grph_, y, end - y);
            y = end;
        }
    }

    ANativeWindow *window_ = nullptr;
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLSurface surface_ = EGL_NO_SURFACE;
    kairo::EglContext egl_;
    GLuint program_ = 0;
    GLuint composed_texture_ = 0, composed_framebuffer_ = 0;
    kairo::SlangFilter filter_;
    bool pixel_unpack_dirty_ = true;
    std::string shader_path_;
    GLuint textures_[5] = {0, 0, 0, 0, 0};
    GLint mode_location_ = -1, base_location_ = -1, surface_location_ = -1;

    int mirror_mode_ = -1;
    int mirror_base_ = 0;
    std::vector<std::array<unsigned char, KAIRO98_GPU_COLS>> mirror_text_{KAIRO98_GPU_ROWS};
    std::vector<std::array<unsigned char, KAIRO98_GPU_COLS>> mirror_grph_{KAIRO98_GPU_ROWS};
    std::vector<unsigned char> mirror_linepal_ = std::vector<unsigned char>(KAIRO98_GPU_ROWS, 0);
    std::vector<std::array<unsigned short, KAIRO98_GPU_PALETTE_ENTRIES>> mirror_palette_{
        KAIRO98_GPU_PALETTE_SLOTS};
    std::vector<unsigned short> mirror_rgb_ = std::vector<unsigned short>(
        KAIRO98_GPU_COLS * KAIRO98_GPU_ROWS, 0);
};
