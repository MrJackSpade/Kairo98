// FM synthesis for the OPNA/OPN through ymfm, generated on a worker thread.
//
// The core never reads the synthesizer: OPNA status and timer flags come from
// np2's own timer emulation, so ymfm is purely an output generator. Every
// input the core gives it (register writes, volume, guest writes to the ADPCM
// RAM) is logged with the stream position it applies at, and every stream
// region the core asks it to fill is queued as a segment. The worker thread
// consumes segments in order, applying the logged inputs at their exact
// positions, and adds the result into the region. The core only waits when
// it is about to read, reset, or free the stream (kairo_ymfm_drain_all), so
// the emulation thread no longer spends its frame budget on synthesis.
//
// KAIRO98_SYNTH_VERIFY keeps a second engine driven synchronously on the
// emulation thread and compares its output with the worker's, sample by
// sample.

#include "ymfm_bridge.h"

#include <algorithm>
#include <array>
#include <condition_variable>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <deque>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include "ymfm_opn.h"

namespace {

class YmfmEngine final : public ymfm::ymfm_interface {
public:
    void reset(bool use_2608, uint32_t output_rate, const uint8_t *ram, uint32_t ram_size,
               const char *rhythm_rom_path) {
        m_2608.reset();
        m_2203.reset();
        m_ram.assign(ram && ram_size ? ram : nullptr, ram && ram_size ? ram + ram_size : nullptr);
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

    void ram_write(uint32_t offset, uint8_t value) {
        if (offset < m_ram.size()) m_ram[offset] = value;
    }

    void ram_reload(const uint8_t *ram) {
        if (ram) std::copy(ram, ram + m_ram.size(), m_ram.begin());
    }

    // Adds `frames` output frames into pcm (stereo, interleaved).
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
        if (type == ymfm::ACCESS_ADPCM_B && address < m_ram.size())
            return m_ram[address];
        return 0;
    }

    void ymfm_external_write(ymfm::access_class type, uint32_t address, uint8_t data) override {
        if (type == ymfm::ACCESS_ADPCM_B && address < m_ram.size())
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
    std::vector<uint8_t> m_ram;
    std::array<uint8_t, 8192> m_rhythm_rom{};
    bool m_rhythm_loaded = false;
    uint32_t m_output_rate = 44100;
    uint32_t m_native_rate = 0;
    int32_t m_clock_step = 48;
    int64_t m_timers[2] = {-1, -1};
    uint64_t m_phase = 0;
    uint32_t m_fm_volume = 128;
    uint32_t m_ssg_volume = 128;
};

struct Command {
    enum Type { Write, Volume, Ram } type;
    uint64_t position;
    uint32_t a;
    uint32_t b;
    uint32_t c;
};

struct Segment {
    int32_t *pcm;
    uint64_t position;
    uint32_t frames;
#if defined(KAIRO98_SYNTH_VERIFY)
    std::vector<int32_t> expected;
#endif
};

unsigned long long g_segments = 0;
unsigned long long g_drain_waits = 0;
unsigned long long g_verify_mismatches = 0;

class SynthWorker {
public:
    SynthWorker() : m_thread([this] { run(); }) {}

    ~SynthWorker() {
        drain();
        {
            std::lock_guard<std::mutex> guard(m_mutex);
            m_stop = true;
        }
        m_work.notify_all();
        m_thread.join();
    }

    // Emulation thread. The engine is only touched here once the worker is
    // idle, which drain() guarantees.
    void reset(bool use_2608, uint32_t output_rate, uint8_t *ram, uint32_t ram_size,
               const char *rhythm_rom_path) {
        drain();
        std::lock_guard<std::mutex> guard(m_mutex);
        m_commands.clear();
        m_ram = ram;
        m_engine.reset(use_2608, output_rate, ram, ram_size, rhythm_rom_path);
#if defined(KAIRO98_SYNTH_VERIFY)
        m_shadow.reset(use_2608, output_rate, ram, ram_size, rhythm_rom_path);
#endif
    }

    bool has_rhythm_rom() {
        drain();
        std::lock_guard<std::mutex> guard(m_mutex);
        return m_engine.has_rhythm_rom();
    }

    uint8_t read_status(int bank) {
        drain();
        std::lock_guard<std::mutex> guard(m_mutex);
        return m_engine.read_status(bank);
    }

    const uint8_t *ram() const { return m_ram; }

    // Emulation thread, after a state load replaced the core's ADPCM RAM.
    void reload_ram() {
        drain();
        std::lock_guard<std::mutex> guard(m_mutex);
        m_engine.ram_reload(m_ram);
#if defined(KAIRO98_SYNTH_VERIFY)
        m_shadow.ram_reload(m_ram);
#endif
    }

    void write(int bank, uint8_t address, uint8_t data) {
        post({Command::Write, 0, static_cast<uint32_t>(bank), address, data});
#if defined(KAIRO98_SYNTH_VERIFY)
        m_shadow.write(bank, address, data);
#endif
    }

    void set_volume(uint32_t fm, uint32_t ssg) {
        post({Command::Volume, 0, fm, ssg, 0});
#if defined(KAIRO98_SYNTH_VERIFY)
        m_shadow.set_volume(fm, ssg);
#endif
    }

    void ram_changed(uint32_t offset, uint8_t value) {
        post({Command::Ram, 0, offset, value, 0});
#if defined(KAIRO98_SYNTH_VERIFY)
        m_shadow.ram_write(offset, value);
#endif
    }

    void mix(int32_t *pcm, uint32_t frames) {
        if (!pcm || frames == 0) return;
        Segment segment{pcm, 0, frames};
#if defined(KAIRO98_SYNTH_VERIFY)
        segment.expected.assign(frames * 2, 0);
        m_shadow.mix(segment.expected.data(), frames);
#endif
        {
            std::lock_guard<std::mutex> guard(m_mutex);
            segment.position = m_issued;
            m_issued += frames;
            m_segments.push_back(std::move(segment));
            ++g_segments;
        }
        m_work.notify_one();
    }

    void drain() {
        std::unique_lock<std::mutex> guard(m_mutex);
        if (!m_segments.empty() || m_busy) {
            ++g_drain_waits;
            m_done.wait(guard, [this] { return m_segments.empty() && !m_busy; });
        }
    }

private:
    void post(Command command) {
        std::lock_guard<std::mutex> guard(m_mutex);
        command.position = m_issued;
        m_commands.push_back(command);
    }

    void apply(const Command &command) {
        switch (command.type) {
            case Command::Write:
                m_engine.write(static_cast<int>(command.a), static_cast<uint8_t>(command.b),
                               static_cast<uint8_t>(command.c));
                break;
            case Command::Volume:
                m_engine.set_volume(command.a, command.b);
                break;
            case Command::Ram:
                m_engine.ram_write(command.a, static_cast<uint8_t>(command.b));
                break;
        }
    }

    void run() {
        std::vector<Command> pending;
        std::vector<int32_t> scratch;
        std::unique_lock<std::mutex> guard(m_mutex);
        for (;;) {
            m_work.wait(guard, [this] { return !m_segments.empty() || m_stop; });
            if (m_stop) return;
            Segment segment = std::move(m_segments.front());
            m_segments.pop_front();
            m_busy = true;
            // Every input that applies inside this segment was logged before
            // the segment was issued, so take them all now.
            pending.clear();
            const uint64_t end = segment.position + segment.frames;
            while (!m_commands.empty() && m_commands.front().position < end) {
                pending.push_back(m_commands.front());
                m_commands.pop_front();
            }
            guard.unlock();

            scratch.assign(segment.frames * 2, 0);
            size_t next = 0;
            uint32_t done = 0;
            while (done < segment.frames) {
                const uint64_t position = segment.position + done;
                while (next < pending.size() && pending[next].position <= position) {
                    apply(pending[next++]);
                }
                uint32_t run_frames = segment.frames - done;
                if (next < pending.size()) {
                    run_frames = static_cast<uint32_t>(
                        std::min<uint64_t>(run_frames, pending[next].position - position));
                }
                m_engine.mix(scratch.data() + done * 2, run_frames);
                done += run_frames;
            }
            for (size_t i = 0; i < scratch.size(); ++i) segment.pcm[i] += scratch[i];
#if defined(KAIRO98_SYNTH_VERIFY)
            for (size_t i = 0; i < scratch.size(); ++i) {
                if (scratch[i] != segment.expected[i]) ++g_verify_mismatches;
            }
#endif
            guard.lock();
            m_busy = false;
            if (m_segments.empty()) m_done.notify_all();
        }
    }

    YmfmEngine m_engine;
#if defined(KAIRO98_SYNTH_VERIFY)
    YmfmEngine m_shadow;
#endif
    const uint8_t *m_ram = nullptr;
    std::mutex m_mutex;
    std::condition_variable m_work;
    std::condition_variable m_done;
    std::deque<Command> m_commands;
    std::deque<Segment> m_segments;
    uint64_t m_issued = 0;
    bool m_busy = false;
    bool m_stop = false;
    std::thread m_thread;
};

std::mutex g_registry_mutex;
std::vector<SynthWorker *> g_registry;

} // namespace

extern "C" void *kairo_ymfm_create(void) {
    auto *worker = new SynthWorker;
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    g_registry.push_back(worker);
    return worker;
}

extern "C" void kairo_ymfm_destroy(void *handle) {
    auto *worker = static_cast<SynthWorker *>(handle);
    if (!worker) return;
    {
        std::lock_guard<std::mutex> guard(g_registry_mutex);
        g_registry.erase(std::remove(g_registry.begin(), g_registry.end(), worker),
                         g_registry.end());
    }
    delete worker;
}

extern "C" void kairo_ymfm_reset(void *handle, int ym2608, uint32_t output_rate,
                                   uint8_t *adpcm_ram, uint32_t adpcm_ram_size,
                                   const char *rhythm_rom_path) {
    if (handle) static_cast<SynthWorker *>(handle)->reset(ym2608 != 0, output_rate,
                                                        adpcm_ram, adpcm_ram_size,
                                                        rhythm_rom_path);
}

extern "C" int kairo_ymfm_has_rhythm_rom(void *handle) {
    return handle && static_cast<SynthWorker *>(handle)->has_rhythm_rom();
}

extern "C" void kairo_ymfm_write(void *handle, int bank, uint8_t address, uint8_t data) {
    if (handle) static_cast<SynthWorker *>(handle)->write(bank, address, data);
}

extern "C" uint8_t kairo_ymfm_read_status(void *handle, int bank) {
    return handle ? static_cast<SynthWorker *>(handle)->read_status(bank) : 0;
}

extern "C" void kairo_ymfm_set_volume(void *handle, uint32_t fm_volume,
                                       uint32_t ssg_volume) {
    if (handle) static_cast<SynthWorker *>(handle)->set_volume(fm_volume, ssg_volume);
}

extern "C" void kairo_ymfm_mix(void *handle, int32_t *pcm, uint32_t frames) {
    if (handle) static_cast<SynthWorker *>(handle)->mix(pcm, frames);
}

extern "C" void kairo_ymfm_drain_all(void) {
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    for (auto *worker : g_registry) worker->drain();
}

extern "C" void kairo_ymfm_reload_all_ram(void) {
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    for (auto *worker : g_registry) worker->reload_ram();
}

extern "C" void kairo_ymfm_ram_changed(const uint8_t *adpcm_ram, uint32_t offset, uint8_t value) {
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    for (auto *worker : g_registry) {
        if (worker->ram() == adpcm_ram) worker->ram_changed(offset, value);
    }
}

extern "C" void kairo_ymfm_stats(unsigned long long *segments, unsigned long long *drain_waits,
                                 unsigned long long *verify_mismatches) {
    *segments = g_segments;
    *drain_waits = g_drain_waits;
    *verify_mismatches = g_verify_mismatches;
}
