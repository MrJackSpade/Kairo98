#include "ymfm_bridge.h"
#include <array>
#include <cstdint>
#include <cstdio>
#include <vector>

static void setup(void *chip, uint8_t *ram, uint32_t size) {
    kairo_ymfm_reset(chip, 1, 44100, ram, size, nullptr);
    kairo_ymfm_write(chip, 0, 0x00, 0x40);
    kairo_ymfm_write(chip, 0, 0x01, 0x00);
    kairo_ymfm_write(chip, 0, 0x07, 0x3e);
    kairo_ymfm_write(chip, 0, 0x08, 0x0f);
}
// Compare final mixed samples; this is an output-equivalence test, not a
// substitute for a race detector or the shared-buffer ownership rule.
static std::vector<int32_t> generate(bool batched) {
    std::array<uint8_t, 256 * 1024> ram{};
    void *chips[2] = {kairo_ymfm_create(), kairo_ymfm_create()};
    for (auto *chip : chips) setup(chip, ram.data(), static_cast<uint32_t>(ram.size()));
    constexpr unsigned chunks = 128, frames = 128;
    std::vector<int32_t> pcm(chunks * frames * 2, 0);
    for (unsigned n = 0; n < chunks; n++) {
        for (auto *chip : chips) {
            kairo_ymfm_write(chip, 0, 0x00, static_cast<uint8_t>(0x20 + (n % 32)));
            kairo_ymfm_mix(chip, pcm.data() + n * frames * 2, frames);
            if (!batched) kairo_ymfm_drain_all();
        }
        // Another synchronous stream callback contributes to this region.
        for (unsigned i = 0; i < frames * 2; i++) pcm[n * frames * 2 + i] += 37 + (i % 11);
    }
    kairo_ymfm_drain_all();
    for (auto *chip : chips) kairo_ymfm_destroy(chip);
    return pcm;
}
int main() {
    const auto expected = generate(false);
    unsigned long long mismatches = 0;
    for (int trial = 0; trial < 12; trial++) {
        const auto actual = generate(true);
        for (size_t i = 0; i < actual.size(); i++) if (actual[i] != expected[i]) mismatches++;
    }
    printf("mixed-buffer mismatches=%llu\n", mismatches);
    return mismatches ? 1 : 0;
}
