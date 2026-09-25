#include "ymfm_bridge.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <vector>

static void write(void *chip, int bank, uint8_t reg, uint8_t value) {
    kairo_ymfm_write(chip, bank, reg, value);
}

static int measure(void *chip, const char *name) {
    std::vector<int32_t> pcm(44100 * 2);
    kairo_ymfm_mix(chip, pcm.data(), 44100);
    int64_t energy = 0;
    int32_t peak = 0;
    for (int32_t sample : pcm) {
        energy += std::abs(static_cast<int64_t>(sample));
        peak = std::max(peak, static_cast<int32_t>(std::abs(static_cast<int64_t>(sample))));
    }
    std::printf("%s peak=%d mean=%lld\n", name, peak,
                static_cast<long long>(energy / pcm.size()));
    return energy > 0 && peak > 0 ? 0 : 1;
}

int main() {
    uint8_t ram[256 * 1024]{};
    void *chip = kairo_ymfm_create();
    int failures = 0;
    for (int extended = 0; extended <= 1; ++extended) {
        kairo_ymfm_reset(chip, extended, 44100, ram, sizeof(ram), nullptr);
        // SSG channel A: tone on, channel B/C and noise off.
        write(chip, 0, 0x00, 0x40);
        write(chip, 0, 0x01, 0x00);
        write(chip, 0, 0x07, 0x3e);
        write(chip, 0, 0x08, 0x0f);
        failures += measure(chip, extended ? "YM2608 SSG" : "YM2203 SSG");

        kairo_ymfm_reset(chip, extended, 44100, ram, sizeof(ram), nullptr);
        // Four active operators, algorithm 7, channel 0 key on.
        for (uint8_t offset : {uint8_t(0), uint8_t(4), uint8_t(8), uint8_t(12)}) {
            write(chip, 0, 0x30 + offset, 0x01);
            write(chip, 0, 0x40 + offset, 0x00);
            write(chip, 0, 0x50 + offset, 0x1f);
            write(chip, 0, 0x60 + offset, 0x00);
            write(chip, 0, 0x70 + offset, 0x00);
            write(chip, 0, 0x80 + offset, 0x0f);
        }
        write(chip, 0, 0xb0, 0x07);
        write(chip, 0, 0xa4, 0x22);
        write(chip, 0, 0xa0, 0x69);
        write(chip, 0, 0x28, 0xf0);
        failures += measure(chip, extended ? "YM2608 FM" : "YM2203 FM");

        kairo_ymfm_reset(chip, extended, 44100, ram, sizeof(ram), nullptr);
        write(chip, 0, 0x24, 0xff);
        write(chip, 0, 0x25, 0x03);
        write(chip, 0, 0x27, 0x05); // start Timer A and enable its status flag
        std::vector<int32_t> timerPcm(512 * 2);
        kairo_ymfm_mix(chip, timerPcm.data(), 512);
        const uint8_t timerStatus = kairo_ymfm_read_status(chip, 0);
        std::printf("%s Timer A status=%02x\n", extended ? "YM2608" : "YM2203", timerStatus);
        failures += (timerStatus & 0x01) == 0;
        kairo_ymfm_reset(chip, extended, 44100, ram, sizeof(ram), nullptr);
        failures += kairo_ymfm_read_status(chip, 0) != 0;
        write(chip, 0, 0x24, 0xff);
        write(chip, 0, 0x25, 0x03);
        write(chip, 0, 0x27, 0x05);
        kairo_ymfm_set_volume(chip, 0, 0);
        std::fill(timerPcm.begin(), timerPcm.end(), 0);
        kairo_ymfm_mix(chip, timerPcm.data(), 512);
        failures += (kairo_ymfm_read_status(chip, 0) & 0x01) == 0;
        kairo_ymfm_set_volume(chip, 128, 128);
    }
    std::fill(ram, ram + sizeof(ram), uint8_t(0x77));
    kairo_ymfm_reset(chip, 1, 44100, ram, sizeof(ram), nullptr);
    write(chip, 1, 0x01, 0xc0); // ADPCM-B pan both sides
    write(chip, 1, 0x02, 0x00); // start at zero
    write(chip, 1, 0x03, 0x00);
    write(chip, 1, 0x04, 0x01); // end after 64 bytes
    write(chip, 1, 0x05, 0x00);
    write(chip, 1, 0x09, 0xff); // fast decode rate
    write(chip, 1, 0x0a, 0xff);
    write(chip, 1, 0x0b, 0xff); // level
    write(chip, 1, 0x00, 0xb0); // execute, external RAM, repeat
    failures += measure(chip, "YM2608 ADPCM-B");

    const char *rhythmPath = "/data/local/tmp/kairo-ymfm-rhythm-smoke.bin";
    std::array<uint8_t, 8192> rhythm{};
    rhythm.fill(0x77);
    if (FILE *file = std::fopen(rhythmPath, "wb")) {
        failures += std::fwrite(rhythm.data(), 1, rhythm.size(), file) != rhythm.size();
        std::fclose(file);
    } else failures++;
    kairo_ymfm_reset(chip, 1, 44100, ram, sizeof(ram), rhythmPath);
    failures += !kairo_ymfm_has_rhythm_rom(chip);
    write(chip, 0, 0x11, 0x3f); // ADPCM-A total level
    write(chip, 0, 0x18, 0xdf); // both speakers, drum level
    write(chip, 0, 0x10, 0x01); // bass drum key on
    failures += measure(chip, "YM2608 rhythm ROM");
    std::remove(rhythmPath);
    kairo_ymfm_destroy(chip);
    return failures ? 1 : 0;
}
