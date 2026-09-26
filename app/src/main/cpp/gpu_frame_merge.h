#pragma once

#include <cstring>
#include "android_host/gpudraw.h"

// A GPU packet is a delta. Preserve older updates when dropping its display,
// with newer updates winning independently for each plane and palette slot.
// CPU packets contain a complete RGB frame; entering GPU mode after a CPU
// frame also produces a full refresh, so neither transition needs old deltas.
inline void kairo98_merge_gpu_frames(kairo98_gpu_frame_t &newer,
                                     const kairo98_gpu_frame_t &older) {
    if (newer.mode < 0 || older.mode < 0) return;

    for (int y = 0; y < KAIRO98_GPU_ROWS; ++y) {
        const unsigned char missing = older.row_dirty[y] & ~newer.row_dirty[y];
        if (missing & KAIRO98_GPU_ROW_TEXT)
            std::memcpy(newer.text[y], older.text[y], KAIRO98_GPU_COLS);
        if (missing & KAIRO98_GPU_ROW_GRPH)
            std::memcpy(newer.grph[y], older.grph[y], KAIRO98_GPU_COLS);
        newer.row_dirty[y] |= older.row_dirty[y];
    }

    bool updated[KAIRO98_GPU_PALETTE_SLOTS] = {};
    for (int i = 0; i < newer.palette_updates; ++i)
        updated[newer.palette[i].slot] = true;
    for (int i = 0; i < older.palette_updates; ++i) {
        const auto &update = older.palette[i];
        if (!updated[update.slot]) {
            newer.palette[newer.palette_updates++] = update;
            updated[update.slot] = true;
        }
    }
    newer.full |= older.full;
    // The newest packet already has the complete line-to-palette map and
    // current display mode. Keep those, even for rows it did not redraw.
}
