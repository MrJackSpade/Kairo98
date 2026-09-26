#include "gpu_frame_merge.h"

#include <cstdio>
#include <memory>

static int failures = 0;
static void check(bool condition, const char *message) {
    if (!condition) {
        std::fprintf(stderr, "%s\n", message);
        ++failures;
    }
}

int main() {
    auto older = std::make_unique<kairo98_gpu_frame_t>();
    auto newer = std::make_unique<kairo98_gpu_frame_t>();
    older->mode = newer->mode = 3;
    older->full = 1;
    older->row_dirty[10] = older->row_dirty[20] = 7;
    older->text[10][5] = 11;
    older->grph[10][5] = 12;
    older->text[20][5] = 21;
    older->grph[20][5] = 22;
    newer->row_dirty[20] = 7;
    newer->text[20][5] = 31;
    newer->grph[20][5] = 32;
    newer->line_palette[10] = 42;
    older->palette_updates = 2;
    older->palette[0].slot = 42;
    older->palette[0].entries[5] = 100;
    older->palette[1].slot = 43;
    older->palette[1].entries[5] = 200;
    newer->palette_updates = 1;
    newer->palette[0].slot = 43;
    newer->palette[0].entries[5] = 300;

    kairo98_merge_gpu_frames(*newer, *older);
    check(newer->text[10][5] == 11 && newer->grph[10][5] == 12,
          "Dropped row update was lost");
    check(newer->text[20][5] == 31 && newer->grph[20][5] == 32,
          "Older row overwrote a newer row");
    check(newer->row_dirty[10] == 7 && newer->full == 1,
          "Dirty/full state was lost");
    check(newer->palette_updates == 2 && newer->palette[0].entries[5] == 300 &&
          newer->palette[1].slot == 42 && newer->palette[1].entries[5] == 100,
          "Palette updates were lost or applied out of order");
    check(newer->line_palette[10] == 42, "Newest complete palette map changed");

    // A mode change can hide a plane, but its queued update still belongs in
    // the renderer's mirror. Updates to one plane must not overwrite the other.
    std::memset(older.get(), 0, sizeof(*older));
    std::memset(newer.get(), 0, sizeof(*newer));
    older->mode = 2;
    newer->mode = 1;
    older->row_dirty[0] = KAIRO98_GPU_ROW_DRAWN | KAIRO98_GPU_ROW_GRPH;
    newer->row_dirty[0] = KAIRO98_GPU_ROW_DRAWN | KAIRO98_GPU_ROW_TEXT;
    older->grph[0][0] = 9;
    newer->text[0][0] = 8;
    kairo98_merge_gpu_frames(*newer, *older);
    check(newer->mode == 1 && newer->row_dirty[0] == 7 &&
          newer->grph[0][0] == 9 && newer->text[0][0] == 8,
          "Plane-specific updates were not preserved across a mode change");

    // All slots can be updated repeatedly without exceeding packet capacity.
    std::memset(older.get(), 0, sizeof(*older));
    std::memset(newer.get(), 0, sizeof(*newer));
    older->mode = newer->mode = 3;
    older->palette_updates = newer->palette_updates = KAIRO98_GPU_PALETTE_SLOTS;
    for (int i = 0; i < KAIRO98_GPU_PALETTE_SLOTS; ++i) {
        older->palette[i].slot = newer->palette[i].slot = static_cast<unsigned char>(i);
        older->palette[i].entries[0] = 1;
        newer->palette[i].entries[0] = 2;
    }
    kairo98_merge_gpu_frames(*newer, *older);
    check(newer->palette_updates == KAIRO98_GPU_PALETTE_SLOTS,
          "Palette update capacity exceeded");
    for (const auto &update : newer->palette)
        check(update.entries[0] == 2, "Newer palette lost precedence");

    newer->mode = -1;
    newer->rgb[0] = 123;
    kairo98_merge_gpu_frames(*newer, *older);
    check(newer->mode == -1 && newer->rgb[0] == 123, "Complete CPU frame changed");
    older->mode = -1;
    newer->mode = 3;
    newer->full = 1;
    kairo98_merge_gpu_frames(*newer, *older);
    check(newer->mode == 3 && newer->full == 1, "CPU-to-GPU refresh changed");

    std::printf("GPU packet merge failures=%d\n", failures);
    return failures ? 1 : 0;
}
