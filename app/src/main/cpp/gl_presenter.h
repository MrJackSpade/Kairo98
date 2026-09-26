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

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window.h>
#include <array>
#include <cstring>
#include <vector>

#include "android_host/gpudraw.h"

class GlPresenter {
public:
    ~GlPresenter() { detach(); }

    // Takes its own reference on the window; safe to call with nullptr.
    bool attach(ANativeWindow *window) {
        detach();
        if (!window) return false;
        ANativeWindow_acquire(window);
        window_ = window;
        if (!init()) {
            detach();
            return false;
        }
        return true;
    }

    void detach() {
        if (display_ != EGL_NO_DISPLAY) {
            eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (surface_ != EGL_NO_SURFACE) eglDestroySurface(display_, surface_);
            if (context_ != EGL_NO_CONTEXT) eglDestroyContext(display_, context_);
            eglTerminate(display_);
        }
        display_ = EGL_NO_DISPLAY;
        surface_ = EGL_NO_SURFACE;
        context_ = EGL_NO_CONTEXT;
        program_ = 0;
        if (window_) {
            ANativeWindow_release(window_);
            window_ = nullptr;
        }
    }

    bool ready() const { return context_ != EGL_NO_CONTEXT; }

    // Merges a frame packet into the mirror and uploads what changed.
    void apply(const kairo98_gpu_frame_t &frame) {
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
            if (frame.mode & 1) std::memcpy(mirror_text_[y].data(), frame.text[y], KAIRO98_GPU_COLS);
            if (frame.mode & 2) std::memcpy(mirror_grph_[y].data(), frame.grph[y], KAIRO98_GPU_COLS);
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
        glViewport(0, 0, width, height);
        glUseProgram(program_);
        glUniform1i(mode_location_, mirror_mode_);
        glUniform1i(base_location_, mirror_base_);
        glUniform2i(surface_location_, width, height);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        if (!eglSwapBuffers(display_, surface_)) {
            __android_log_print(ANDROID_LOG_WARN, "Kairo98Perf", "eglSwapBuffers failed: 0x%x",
                                eglGetError());
            return false;
        }
        return true;
    }

private:
    bool init() {
        display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (display_ == EGL_NO_DISPLAY || !eglInitialize(display_, nullptr, nullptr)) {
            display_ = EGL_NO_DISPLAY;
            return fail("eglInitialize");
        }
        EGLConfig config = nullptr;
        EGLint count = 0;
        // Prefer a 5-6-5 surface so the palette's RGB565 values land unchanged.
        const EGLint attribs565[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                                     EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                                     EGL_RED_SIZE, 5, EGL_GREEN_SIZE, 6, EGL_BLUE_SIZE, 5,
                                     EGL_ALPHA_SIZE, 0, EGL_DEPTH_SIZE, 0, EGL_NONE};
        const EGLint attribs888[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                                     EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                                     EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
                                     EGL_DEPTH_SIZE, 0, EGL_NONE};
        if (!eglChooseConfig(display_, attribs565, &config, 1, &count) || count == 0) {
            if (!eglChooseConfig(display_, attribs888, &config, 1, &count) || count == 0) {
                return fail("eglChooseConfig");
            }
        }
        EGLint format = 0;
        eglGetConfigAttrib(display_, config, EGL_NATIVE_VISUAL_ID, &format);
        ANativeWindow_setBuffersGeometry(window_, 0, 0, format);
        const EGLint context_attribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
        context_ = eglCreateContext(display_, config, EGL_NO_CONTEXT, context_attribs);
        if (context_ == EGL_NO_CONTEXT) return fail("eglCreateContext");
        surface_ = eglCreateWindowSurface(display_, config, window_, nullptr);
        if (surface_ == EGL_NO_SURFACE) return fail("eglCreateWindowSurface");
        if (!eglMakeCurrent(display_, surface_, surface_, context_)) return fail("eglMakeCurrent");
        if (!build_program()) return false;
        create_textures();
        upload_all();
        return true;
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
    EGLContext context_ = EGL_NO_CONTEXT;
    GLuint program_ = 0;
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
