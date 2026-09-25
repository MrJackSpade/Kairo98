#include "ymfm_bridge.h"

#include <algorithm>
#include <array>
#include <cstdio>
#include <cstdint>
#include <memory>

#include "ymfm_opn.h"

namespace {

class YmfmBridge final : public ymfm::ymfm_interface {
public:
    void reset(bool use_2608, uint32_t output_rate, uint8_t *ram, uint32_t ram_size,
               const char *rhythm_rom_path) {
        m_2608.reset();
        m_2203.reset();
        m_ram = ram;
        m_ram_size = ram_size;
        m_output_rate = output_rate ? output_rate : 44100;
        m_phase = 0;
        m_timers[0] = m_timers[1] = -1;
        m_rhythm_loaded = false;
        if (use_2608 && rhythm_rom_path && *rhythm_rom_path) {
            if (FILE *rom = std::fopen(rhythm_rom_path, "rb")) {
                const size_t read = std::fread(m_rhythm_rom.data(), 1, m_rhythm_rom.size(), rom);
                m_rhythm_loaded = read == m_rhythm_rom.size() && std::fgetc(rom) == EOF;
                std::fclose(rom);
            }
        }
        if (use_2608) {
            m_2608 = std::make_unique<ymfm::ym2608>(*this);
            m_2608->set_fidelity(ymfm::OPN_FIDELITY_MIN);
            m_2608->reset();
            m_clock_step = 48;
            m_native_rate = m_2608->sample_rate(7987200);
        } else {
            m_2203 = std::make_unique<ymfm::ym2203>(*this);
            m_2203->set_fidelity(ymfm::OPN_FIDELITY_MIN);
            m_2203->reset();
            m_clock_step = 24;
            m_native_rate = m_2203->sample_rate(3993600);
        }
    }

    void write(int bank, uint8_t address, uint8_t data) {
        if (m_2608) {
            m_2608->write(bank ? 2 : 0, address);
            m_2608->write(bank ? 3 : 1, data);
        } else if (m_2203 && !bank) {
            m_2203->write(0, address);
            m_2203->write(1, data);
        }
    }

    uint8_t read_status(int bank) {
        if (m_2608) return bank ? m_2608->read_status_hi() : m_2608->read_status();
        return m_2203 ? m_2203->read_status() : 0;
    }

    bool has_rhythm_rom() const { return m_rhythm_loaded; }

    void set_volume(uint32_t fm_volume, uint32_t ssg_volume) {
        m_fm_volume = std::min(fm_volume, uint32_t(128));
        m_ssg_volume = std::min(ssg_volume, uint32_t(128));
    }

    void mix(int32_t *pcm, uint32_t frames) {
        if (!pcm || !m_native_rate || !m_output_rate) return;
        for (uint32_t frame = 0; frame < frames; ++frame) {
            m_phase += m_native_rate;
            const uint32_t samples = static_cast<uint32_t>(m_phase / m_output_rate);
            m_phase %= m_output_rate;
            int64_t fm_left = 0;
            int64_t fm_right = 0;
            int64_t ssg = 0;
            for (uint32_t sample = 0; sample < samples; ++sample) {
                if (m_2608) {
                    ymfm::ym2608::output_data output{};
                    m_2608->generate(&output);
                    // ymfm keeps the mono SSG bus separate from stereo FM/ADPCM.
                    fm_left += output.data[0];
                    fm_right += output.data[1];
                    ssg += output.data[2];
                } else {
                    ymfm::ym2203::output_data output{};
                    m_2203->generate(&output);
                    // YM2203 has one FM bus and three separate SSG channels.
                    fm_left += output.data[0];
                    fm_right += output.data[0];
                    ssg += output.data[1] + output.data[2] + output.data[3];
                }
                advance_timers();
            }
            if (samples) {
                pcm[frame * 2] += static_cast<int32_t>(
                    (fm_left * m_fm_volume + ssg * m_ssg_volume) / (samples * 128));
                pcm[frame * 2 + 1] += static_cast<int32_t>(
                    (fm_right * m_fm_volume + ssg * m_ssg_volume) / (samples * 128));
            }
        }
    }

    void ymfm_set_timer(uint32_t timer, int32_t clocks) override {
        if (timer < 2) m_timers[timer] = clocks;
    }

    uint8_t ymfm_external_read(ymfm::access_class type, uint32_t address) override {
        if (type == ymfm::ACCESS_ADPCM_A && m_rhythm_loaded && address < m_rhythm_rom.size())
            return m_rhythm_rom[address];
        if (type == ymfm::ACCESS_ADPCM_B && m_ram && address < m_ram_size)
            return m_ram[address];
        return 0;
    }

    void ymfm_external_write(ymfm::access_class type, uint32_t address, uint8_t data) override {
        if (type == ymfm::ACCESS_ADPCM_B && m_ram && address < m_ram_size)
            m_ram[address] = data;
    }

private:
    void advance_timers() {
        for (uint32_t timer = 0; timer < 2; ++timer) {
            if (m_timers[timer] < 0) continue;
            m_timers[timer] -= m_clock_step;
            // The callback may reload the timer; bound this loop for malformed values.
            for (int repeat = 0; m_timers[timer] <= 0 && repeat < 4; ++repeat) {
                m_timers[timer] = -1;
                m_engine->engine_timer_expired(timer);
                if (m_timers[timer] < 0) break;
            }
        }
    }

    std::unique_ptr<ymfm::ym2608> m_2608;
    std::unique_ptr<ymfm::ym2203> m_2203;
    uint8_t *m_ram = nullptr;
    std::array<uint8_t, 8192> m_rhythm_rom{};
    bool m_rhythm_loaded = false;
    uint32_t m_ram_size = 0;
    uint32_t m_output_rate = 44100;
    uint32_t m_native_rate = 0;
    int32_t m_clock_step = 48;
    int64_t m_timers[2] = {-1, -1};
    uint64_t m_phase = 0;
    uint32_t m_fm_volume = 128;
    uint32_t m_ssg_volume = 128;
};

} // namespace

extern "C" void *kairo_ymfm_create(void) { return new YmfmBridge; }
extern "C" void kairo_ymfm_destroy(void *handle) { delete static_cast<YmfmBridge *>(handle); }
extern "C" void kairo_ymfm_reset(void *handle, int ym2608, uint32_t output_rate,
                                   uint8_t *adpcm_ram, uint32_t adpcm_ram_size,
                                   const char *rhythm_rom_path) {
    if (handle) static_cast<YmfmBridge *>(handle)->reset(ym2608 != 0, output_rate,
                                                        adpcm_ram, adpcm_ram_size,
                                                        rhythm_rom_path);
}
extern "C" int kairo_ymfm_has_rhythm_rom(void *handle) {
    return handle && static_cast<YmfmBridge *>(handle)->has_rhythm_rom();
}
extern "C" void kairo_ymfm_write(void *handle, int bank, uint8_t address, uint8_t data) {
    if (handle) static_cast<YmfmBridge *>(handle)->write(bank, address, data);
}
extern "C" uint8_t kairo_ymfm_read_status(void *handle, int bank) {
    return handle ? static_cast<YmfmBridge *>(handle)->read_status(bank) : 0;
}
extern "C" void kairo_ymfm_set_volume(void *handle, uint32_t fm_volume,
                                       uint32_t ssg_volume) {
    if (handle) static_cast<YmfmBridge *>(handle)->set_volume(fm_volume, ssg_volume);
}
extern "C" void kairo_ymfm_mix(void *handle, int32_t *pcm, uint32_t frames) {
    if (handle) static_cast<YmfmBridge *>(handle)->mix(pcm, frames);
}
