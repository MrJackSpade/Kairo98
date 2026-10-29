// The PC-98's picture composed on the GPU (kairo/gpu_picture.h, Kairo98#108).
//
// The core's scrndraw_draw records each redrawn line's 8-bit text and graphics indices and the
// palette slot it used (android_host/gpudraw.c) instead of converting them to RGB565 on the
// emulation thread. A packet carries what a frame redrew; the picture keeps a copy of the whole
// screen, uploads what packets changed, and composes 640x400 pixels in a fragment shader: index =
// base + text + graphics, colour = palette[line's slot][index]. Frames the core converted on the CPU
// (mode -1) are shown from an RGB565 texture instead. The frame host scales the composed picture to
// the window, through the player's shader preset or not.

#pragma once

#include "android_host/gpudraw.h"
#include "gpu_frame_merge.h"
#include "kairo/gpu_picture.h"
#include <GLES3/gl3.h>
#include <android/log.h>
#include <array>
#include <cstring>
#include <memory>
#include <vector>

// How many frames the core has redrawn (android_host/machine.c).
extern "C" unsigned long long kairo98_machine_draw_count(void);

class Pc98Picture : public kairo::GpuPicture {
public:
    struct Packet : kairo::GpuPicture::Packet {
        kairo98_gpu_frame_t data;
    };

    uint32_t width() const override { return KAIRO98_GPU_COLS; }
    uint32_t height() const override { return KAIRO98_GPU_ROWS; }

    // Emulation thread --------------------------------------------------------------------------

    std::unique_ptr<kairo::GpuPicture::Packet> new_packet() override { return std::make_unique<Packet>(); }

    // A frame that redrew nothing changed nothing.
    bool take(kairo::GpuPicture::Packet &packet) override {
        const unsigned long long draws = kairo98_machine_draw_count();
        if (draws == taken_draws_) return false;
        taken_draws_ = draws;
        kairo98_gpudraw_take(&own(packet).data);
        return true;
    }

    bool pictured(const kairo::GpuPicture::Packet &packet) override {
        return kairo98_gpudraw_pictured(&own(packet).data) != 0;
    }

    // Folds an older packet into a newer one (gpu_frame_merge.h).
    void merge(kairo::GpuPicture::Packet &newer, const kairo::GpuPicture::Packet &older) override {
        kairo98_merge_gpu_frames(own(newer).data, own(older).data);
    }

    // Presenter thread --------------------------------------------------------------------------

    void apply(const kairo::GpuPicture::Packet &packet) override {
        const kairo98_gpu_frame_t &frame = own(packet).data;
        if (frame.mode != mode_) upload_planes_ = true;
        mode_ = frame.mode;
        base_ = frame.base;
        if (frame.mode < 0) {
            std::memcpy(rgb_.data(), frame.rgb, sizeof(frame.rgb));
            upload_rgb_ = true;
            return;
        }
        for (int i = 0; i < frame.palette_updates; ++i) {
            const auto &update = frame.palette[i];
            std::memcpy(palette_[update.slot].data(), update.entries, sizeof(update.entries));
            palette_dirty_[update.slot] = true;
        }
        std::memcpy(line_palette_.data(), frame.line_palette, sizeof(frame.line_palette));
        upload_line_palette_ = true;
        for (int y = 0; y < KAIRO98_GPU_ROWS; ++y) {
            if (!frame.row_dirty[y]) continue;
            if (frame.row_dirty[y] & KAIRO98_GPU_ROW_TEXT) std::memcpy(text_[y].data(), frame.text[y], KAIRO98_GPU_COLS);
            if (frame.row_dirty[y] & KAIRO98_GPU_ROW_GRPH) std::memcpy(grph_[y].data(), frame.grph[y], KAIRO98_GPU_COLS);
            rows_dirty_[y] |= frame.row_dirty[y];
        }
        if (frame.full) upload_planes_ = true;
#if defined(KAIRO98_GPU_VERIFY)
        static unsigned long long verified_frames = 0, bad_pixels = 0;
        bad_pixels += verify(frame);
        if (++verified_frames % 300 == 0)
            __android_log_print(ANDROID_LOG_INFO, "Kairo98Perf", "gpu verify frames=%llu badpixels=%llu",
                                verified_frames, bad_pixels);
#endif
    }

    void context_created() override {
        build_program();
        create_textures();
        upload_rgb_ = upload_planes_ = upload_line_palette_ = true;
        palette_dirty_.fill(true);
    }

    void context_ending() override {
        if (program_) glDeleteProgram(program_);
        glDeleteTextures(kTextures, textures_);
        program_ = 0;
        std::memset(textures_, 0, sizeof(textures_));
    }

    void draw(GLuint framebuffer, bool top_row_first) override {
        upload();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_STENCIL_TEST);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glBindVertexArray(0);
        glUseProgram(program_);
        glUniform1i(mode_location_, mode_);
        glUniform1i(base_location_, base_);
        glUniform1i(top_first_location_, top_row_first ? 1 : 0);
        for (int unit = 0; unit < kTextures; ++unit) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindSampler(unit, 0);
            glBindTexture(GL_TEXTURE_2D, textures_[unit]);
        }
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

private:
    // Text and graphics indices, the line-to-palette map, the palette slots, and the CPU frame.
    static constexpr int kTextures = 5;

    static const Packet &own(const kairo::GpuPicture::Packet &packet) { return static_cast<const Packet &>(packet); }
    static Packet &own(kairo::GpuPicture::Packet &packet) { return static_cast<Packet &>(packet); }

#if defined(KAIRO98_GPU_VERIFY)
    // The shader's composition on the CPU for the rows the frame redrew, against the CPU-converted
    // pixels the packet carries: how many differ.
    unsigned long long verify(const kairo98_gpu_frame_t &frame) const {
        if (frame.mode < 0) return 0;
        unsigned long long bad = 0;
        for (int y = 0; y < KAIRO98_GPU_ROWS; ++y) {
            if (!frame.row_dirty[y]) continue;
            const auto &palette = palette_[line_palette_[y]];
            for (int x = 0; x < KAIRO98_GPU_COLS; ++x) {
                int index = base_;
                if (mode_ & 1) index += text_[y][x];
                if (mode_ & 2) index += grph_[y][x];
                if (palette[index] != frame.rgb[y * KAIRO98_GPU_COLS + x]) ++bad;
            }
        }
        return bad;
    }
#endif

    static void require(bool ok, const char *what, const char *log = "") {
        if (!ok) __android_log_assert("picture", "Kairo98Picture", "Could not %s: %s", what, log);
    }

    static GLuint compile(GLenum type, const char *source) {
        const GLuint shader = glCreateShader(type);
        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);
        GLint ok = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
        char log[512] = {};
        if (!ok) glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        require(ok, "compile the picture shader", log);
        return shader;
    }

    void build_program() {
        static const char *vertex =
            "#version 300 es\n"
            "void main() {\n"
            "  vec2 p = vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0);\n"
            "  gl_Position = vec4(p, 0.0, 1.0);\n"
            "}\n";
        // One fragment per picture pixel; g is the pixel's column and row from the picture's top.
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
            "uniform bool uTopFirst;\n"
            "out vec4 oColor;\n"
            "void main() {\n"
            "  int row = int(gl_FragCoord.y);\n"
            "  ivec2 g = ivec2(int(gl_FragCoord.x), uTopFirst ? row : 399 - row);\n"
            "  if (uMode < 0) { oColor = texelFetch(uRgb, g, 0); return; }\n"
            "  int idx = uBase;\n"
            "  if ((uMode & 1) != 0) idx += int(texelFetch(uText, g, 0).r);\n"
            "  if ((uMode & 2) != 0) idx += int(texelFetch(uGrph, g, 0).r);\n"
            "  int pal = int(texelFetch(uLinePal, ivec2(g.y, 0), 0).r);\n"
            "  oColor = texelFetch(uPalette, ivec2(idx, pal), 0);\n"
            "}\n";
        const GLuint vs = compile(GL_VERTEX_SHADER, vertex);
        const GLuint fs = compile(GL_FRAGMENT_SHADER, fragment);
        program_ = glCreateProgram();
        glAttachShader(program_, vs);
        glAttachShader(program_, fs);
        glLinkProgram(program_);
        glDeleteShader(vs);
        glDeleteShader(fs);
        GLint ok = 0;
        glGetProgramiv(program_, GL_LINK_STATUS, &ok);
        char log[512] = {};
        if (!ok) glGetProgramInfoLog(program_, sizeof(log), nullptr, log);
        require(ok, "link the picture shader", log);
        glUseProgram(program_);
        glUniform1i(glGetUniformLocation(program_, "uText"), 0);
        glUniform1i(glGetUniformLocation(program_, "uGrph"), 1);
        glUniform1i(glGetUniformLocation(program_, "uLinePal"), 2);
        glUniform1i(glGetUniformLocation(program_, "uPalette"), 3);
        glUniform1i(glGetUniformLocation(program_, "uRgb"), 4);
        mode_location_ = glGetUniformLocation(program_, "uMode");
        base_location_ = glGetUniformLocation(program_, "uBase");
        top_first_location_ = glGetUniformLocation(program_, "uTopFirst");
    }

    static void nearest() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    void texture(int unit, GLenum internal, int width, int height, GLenum format, GLenum type) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textures_[unit]);
        glTexImage2D(GL_TEXTURE_2D, 0, internal, width, height, 0, format, type, nullptr);
        nearest();
    }

    void create_textures() {
        glGenTextures(kTextures, textures_);
        texture(0, GL_R8UI, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, GL_RED_INTEGER, GL_UNSIGNED_BYTE);
        texture(1, GL_R8UI, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, GL_RED_INTEGER, GL_UNSIGNED_BYTE);
        texture(2, GL_R8UI, KAIRO98_GPU_ROWS, 1, GL_RED_INTEGER, GL_UNSIGNED_BYTE);
        texture(3, GL_RGB565, KAIRO98_GPU_PALETTE_ENTRIES, KAIRO98_GPU_PALETTE_SLOTS, GL_RGB, GL_UNSIGNED_SHORT_5_6_5);
        texture(4, GL_RGB565, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, GL_RGB, GL_UNSIGNED_SHORT_5_6_5);
    }

    // Uploads what packets changed since the last draw.
    void upload() {
        // A shader preset in the same context can leave pixel-transfer state behind; the copies here
        // are tightly packed client memory.
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        if (upload_rgb_) {
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_2D, textures_[4]);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, KAIRO98_GPU_COLS, KAIRO98_GPU_ROWS, GL_RGB,
                            GL_UNSIGNED_SHORT_5_6_5, rgb_.data());
            upload_rgb_ = false;
        }
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, textures_[3]);
        for (int slot = 0; slot < KAIRO98_GPU_PALETTE_SLOTS; ++slot) {
            if (!palette_dirty_[slot]) continue;
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, slot, KAIRO98_GPU_PALETTE_ENTRIES, 1, GL_RGB,
                            GL_UNSIGNED_SHORT_5_6_5, palette_[slot].data());
            palette_dirty_[slot] = false;
        }
        if (upload_line_palette_) {
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, textures_[2]);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, KAIRO98_GPU_ROWS, 1, GL_RED_INTEGER, GL_UNSIGNED_BYTE,
                            line_palette_.data());
            upload_line_palette_ = false;
        }
        if (upload_planes_) {
            upload_rows(0, text_, 0, KAIRO98_GPU_ROWS);
            upload_rows(1, grph_, 0, KAIRO98_GPU_ROWS);
            upload_planes_ = false;
        } else {
            // Runs of changed rows, one copy each: the copy's rows are consecutive in memory.
            int y = 0;
            while (y < KAIRO98_GPU_ROWS) {
                if (!rows_dirty_[y]) { ++y; continue; }
                int end = y;
                while (end < KAIRO98_GPU_ROWS && rows_dirty_[end]) ++end;
                upload_rows(0, text_, y, end - y);
                upload_rows(1, grph_, y, end - y);
                y = end;
            }
        }
        rows_dirty_.fill(0);
    }

    void upload_rows(int unit, const std::vector<std::array<unsigned char, KAIRO98_GPU_COLS>> &plane, int first,
                     int count) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textures_[unit]);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, first, KAIRO98_GPU_COLS, count, GL_RED_INTEGER, GL_UNSIGNED_BYTE,
                        plane[first].data());
    }

    // The emulation thread's: the draw count of the last frame taken.
    unsigned long long taken_draws_ = ~0ull;

    // The presenter thread's: the GL objects, and the whole screen as packets made it.
    GLuint program_ = 0;
    GLuint textures_[kTextures] = {};
    GLint mode_location_ = -1, base_location_ = -1, top_first_location_ = -1;
    int mode_ = -1;
    int base_ = 0;
    std::vector<std::array<unsigned char, KAIRO98_GPU_COLS>> text_{KAIRO98_GPU_ROWS};
    std::vector<std::array<unsigned char, KAIRO98_GPU_COLS>> grph_{KAIRO98_GPU_ROWS};
    std::vector<unsigned char> line_palette_ = std::vector<unsigned char>(KAIRO98_GPU_ROWS, 0);
    std::vector<std::array<unsigned short, KAIRO98_GPU_PALETTE_ENTRIES>> palette_{KAIRO98_GPU_PALETTE_SLOTS};
    std::vector<unsigned short> rgb_ = std::vector<unsigned short>(KAIRO98_GPU_COLS * KAIRO98_GPU_ROWS, 0);
    // What the next draw uploads.
    bool upload_rgb_ = true, upload_planes_ = true, upload_line_palette_ = true;
    std::array<bool, KAIRO98_GPU_PALETTE_SLOTS> palette_dirty_{};
    std::array<unsigned char, KAIRO98_GPU_ROWS> rows_dirty_{};
};
