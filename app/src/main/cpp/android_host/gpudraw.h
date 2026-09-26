/*
 * GPU screen path for the Android host.
 *
 * The core's scrndraw_draw converts its 8-bit index planes (np2_tram and the
 * displayed np2_vram page) into RGB565 through np2_pal16 on the emulation
 * thread, one pixel at a time, honoring per-scanline palette changes by
 * drawing line ranges under successive palettes. When this path is active,
 * scrndraw_draw instead records which lines it would have drawn and which
 * palette each of them would have used, and the presenter thread performs the
 * same composition on the GPU: index = base + text + graphics, colour =
 * palette[line's palette][index], nearest-neighbour scaled exactly as the
 * CPU presenter did.
 *
 * The recorded state mirrors the CPU semantics: a line keeps the palette it
 * was last drawn with until it is redrawn, and the core already forces a
 * full redraw whenever a palette changes. Display modes outside the four
 * plain 400-line variants (interleave, skip-line, 15 kHz, 256-colour) and any
 * frame while the startup screen hash is being sampled fall back to the CPU
 * conversion, whose RGB565 frame is then shown by the same GPU presenter.
 */

#ifndef KAIRO98_GPUDRAW_H
#define KAIRO98_GPUDRAW_H

#ifdef __cplusplus
extern "C" {
#endif

#define KAIRO98_GPU_COLS 640
#define KAIRO98_GPU_ROWS 400
#define KAIRO98_GPU_PALETTE_ENTRIES 512
#define KAIRO98_GPU_PALETTE_SLOTS 256

typedef struct {
    unsigned char slot;
    unsigned short entries[KAIRO98_GPU_PALETTE_ENTRIES];
} kairo98_gpu_palette_update_t;

typedef struct {
    /* -1: rgb holds a CPU-converted RGB565 frame. 0..3: index planes.
     * Bit 0 of mode selects text, bit 1 selects graphics; mode 0 fills the
     * screen with palette entry `base`. */
    int mode;
    int base;
    /* 1 when every row and the whole palette were refreshed. */
    int full;
    unsigned char row_dirty[KAIRO98_GPU_ROWS];
    unsigned char line_palette[KAIRO98_GPU_ROWS];
    int palette_updates;
    kairo98_gpu_palette_update_t palette[KAIRO98_GPU_PALETTE_SLOTS];
    unsigned char text[KAIRO98_GPU_ROWS][KAIRO98_GPU_COLS];
    unsigned char grph[KAIRO98_GPU_ROWS][KAIRO98_GPU_COLS];
    unsigned short rgb[KAIRO98_GPU_ROWS * KAIRO98_GPU_COLS];
} kairo98_gpu_frame_t;

/* Host switch: 0 forces the CPU conversion for following frames. */
void kairo98_gpudraw_set_enabled(int enabled);

/* Called by the worker after pccore_exec when the core redrew. Fills the
 * packet from the recorded state or from the CPU frame and clears the
 * per-frame dirty state. */
void kairo98_gpudraw_take(kairo98_gpu_frame_t *frame);

#ifdef __cplusplus
}
#endif

#endif
