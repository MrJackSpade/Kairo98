#include "compiler.h"
#include "scrnmng.h"
#include "scrndraw.h"
#include "sdraw.h"
#include "palettes.h"
#include "gpudraw.h"

/* Hooks called from vram/scrndraw.c. */
int kairo98_gpudraw_begin(const SCRNSURF *surf, int variant, SDRAW sdraw);
void kairo98_gpudraw_range(SDRAW sdraw, int maxy);

const UINT16 *kairo98_frame_pixels(void);

static int gpu_enabled = 1;
static int gpu_active = 0;          /* current frame is being recorded */
static int gpu_was_active = 0;      /* previous drawn frame was recorded */
static int gpu_mode = 0;
static int gpu_base = 0;
static const UINT8 *gpu_grph = NULL;
static int gpu_full = 0;

static unsigned char row_dirty[KAIRO98_GPU_ROWS];
static unsigned char line_palette[KAIRO98_GPU_ROWS];
static unsigned short palette_slots[KAIRO98_GPU_PALETTE_SLOTS][KAIRO98_GPU_PALETTE_ENTRIES];
static unsigned char palette_dirty[KAIRO98_GPU_PALETTE_SLOTS];
static int palette_current = -1;
static int palette_next = 0;

void kairo98_gpudraw_set_enabled(int enabled) {
    gpu_enabled = enabled ? 1 : 0;
}

static int palette_slot(void) {
    if (palette_current >= 0 &&
        memcmp(palette_slots[palette_current], np2_pal16,
               sizeof(RGB16) * NP2PAL_MAX) == 0) {
        return palette_current;
    }
    palette_current = palette_next;
    palette_next = (palette_next + 1) % KAIRO98_GPU_PALETTE_SLOTS;
    memset(palette_slots[palette_current], 0, sizeof(palette_slots[palette_current]));
    memcpy(palette_slots[palette_current], np2_pal16, sizeof(RGB16) * NP2PAL_MAX);
    palette_dirty[palette_current] = 1;
    return palette_current;
}

int kairo98_gpudraw_begin(const SCRNSURF *surf, int variant, SDRAW sdraw) {
    int usable = gpu_enabled && variant >= 0 && variant < 4 &&
                 surf->width == KAIRO98_GPU_COLS && surf->height == KAIRO98_GPU_ROWS &&
                 surf->bpp == 16;
    int i;

    if (!usable) {
        if (gpu_was_active) {
            /* The RGB frame went stale while the GPU path was recording;
             * convert every line so the CPU frame is complete again. */
            for (i = 0; i < SURFACE_HEIGHT; i++) {
                sdraw->dirty[i] |= 0x80;
            }
            gpu_was_active = 0;
        }
        gpu_active = 0;
        return 0;
    }
    if (!gpu_was_active) {
        /* Entering the GPU path: refresh every row and its palette. */
        gpu_full = 1;
        for (i = 0; i < SURFACE_HEIGHT; i++) {
            sdraw->dirty[i] |= 0x80;
        }
        gpu_was_active = 1;
    }
    gpu_active = 1;
    gpu_mode = variant;
    gpu_base = (variant == 0) ? NP2PAL_TEXT2 : NP2PAL_GRPH;
    /* variant 1 draws text, 2 draws the displayed graphics page, 3 both. */
    gpu_grph = (variant == 1) ? NULL : sdraw->src;
    return 1;
}

void kairo98_gpudraw_range(SDRAW sdraw, int maxy) {
    int slot = palette_slot();
    int y = sdraw->y;
    int lines = maxy - y;

    for (; y < maxy && y < KAIRO98_GPU_ROWS; y++) {
        if (sdraw->dirty[y]) {
            row_dirty[y] = 1;
            line_palette[y] = (unsigned char)slot;
        }
    }
    /* Advance the draw context exactly as the pixel routines would. */
    if (sdraw->src) sdraw->src += SURFACE_WIDTH * lines;
    if (sdraw->src2) sdraw->src2 += SURFACE_WIDTH * lines;
    sdraw->dst += sdraw->yalign * lines;
    sdraw->y = maxy;
}

void kairo98_gpudraw_take(kairo98_gpu_frame_t *frame) {
    int y, slot;

    if (!gpu_active) {
        frame->mode = -1;
        frame->base = 0;
        frame->full = 1;
        frame->palette_updates = 0;
        memcpy(frame->rgb, kairo98_frame_pixels(), sizeof(frame->rgb));
        return;
    }
    frame->mode = gpu_mode;
    frame->base = gpu_base;
    frame->full = gpu_full;
    memcpy(frame->row_dirty, row_dirty, sizeof(row_dirty));
    memcpy(frame->line_palette, line_palette, sizeof(line_palette));
    for (y = 0; y < KAIRO98_GPU_ROWS; y++) {
        if (!row_dirty[y]) continue;
        if (gpu_mode & 1) {
            memcpy(frame->text[y], np2_tram + y * SURFACE_WIDTH, KAIRO98_GPU_COLS);
        }
        if ((gpu_mode & 2) && gpu_grph) {
            memcpy(frame->grph[y], gpu_grph + y * SURFACE_WIDTH, KAIRO98_GPU_COLS);
        }
    }
    frame->palette_updates = 0;
    for (slot = 0; slot < KAIRO98_GPU_PALETTE_SLOTS; slot++) {
        if (!palette_dirty[slot] && !gpu_full) continue;
        if (!palette_dirty[slot] && slot != palette_current) continue;
        frame->palette[frame->palette_updates].slot = (unsigned char)slot;
        memcpy(frame->palette[frame->palette_updates].entries, palette_slots[slot],
               sizeof(palette_slots[slot]));
        frame->palette_updates++;
        palette_dirty[slot] = 0;
    }
#if defined(KAIRO98_GPU_VERIFY)
    /* The CPU conversion also ran this frame; carry it for comparison. */
    memcpy(frame->rgb, kairo98_frame_pixels(), sizeof(frame->rgb));
#endif
    memset(row_dirty, 0, sizeof(row_dirty));
    gpu_full = 0;
}
