#include <jni.h>
#include <cstdio>
#include <android/log.h>
#include <android/native_window.h>
#include <sys/resource.h>
#include <android/native_window_jni.h>
#include <array>
#include "android_host/gpudraw.h"
#include "gl_presenter.h"
#include <aaudio/AAudio.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <future>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

extern "C" int kairo98_core_probe(unsigned short *code_segment,
                                  unsigned short *instruction_pointer);
extern "C" int kairo98_hdi_probe(const char *path, unsigned int *cylinders,
                                 unsigned int *surfaces, unsigned int *sectors,
                                 unsigned int *sector_size, unsigned int *first_word);
extern "C" int kairo98_machine_start(const char *image, const char *font_path,
                                      const char *bios_dir, int mhz_times_ten,
                                      int gdc_mhz_times_ten, int cpu_multiple, int floppy,
                                      const char *boot_floppy, const char *second_floppy);
extern "C" int kairo98_machine_dos_prompt(void);
extern "C" void kairo98_machine_exec(void);
extern "C" void kairo98_machine_counters(unsigned long long *slices, unsigned long long *insts,
                                         unsigned long long *sti, unsigned long long *skips);
extern "C" unsigned long long kairo98_machine_draw_count(void);
extern "C" void kairo_ymfm_stats(unsigned long long *segments, unsigned long long *drain_waits,
                                 unsigned long long *verify_mismatches);
extern "C" void kairo98_machine_egc_stats(unsigned long long *writes, unsigned long long *reads,
                                          unsigned long long *fast_reads,
                                          unsigned long long *mismatch);
extern "C" void kairo98_machine_egc_snapshot(unsigned int *snap, unsigned long long *dec_reads,
                                             unsigned long long *cpu_reads);
extern "C" void kairo98_machine_stage_times(unsigned long long *cpu_ns, unsigned long long *event_ns,
                                            unsigned long long *draw_ns, unsigned long long *fm_ns);
extern "C" int kairo98_machine_reset(void);
extern "C" int kairo98_machine_set_clock(int mhz_times_ten);
extern "C" void kairo98_machine_key(unsigned char code, int down);
extern "C" void kairo98_mouse_move(int dx, int dy);
extern "C" void kairo98_mouse_button(int button, int down);
extern "C" void kairo98_mouse_warp(int x, int y, unsigned generation);
extern "C" int kairo98_machine_save_state(const char *dir);
extern "C" int kairo98_machine_load_state(const char *dir);
extern "C" unsigned kairo98_mouse_warp_completed(void);
extern "C" void kairo98_joy_set(int control, int down);
extern "C" void kairo98_joy_release_all(void);
extern "C" void kairo98_input_telemetry_reset(void);
extern "C" void kairo98_input_telemetry_snapshot(uint64_t *, uint64_t *, uint64_t *, int *);
extern "C" void kairo98_machine_release_keys(void);
extern "C" int kairo98_machine_set_disk(const char *image);
extern "C" int kairo98_machine_set_floppy(int drive, const char *image);
extern "C" int kairo98_machine_stop(void);
extern "C" void kairo98_machine_location(unsigned short *cs, unsigned short *ip);
extern "C" const unsigned short *kairo98_frame_pixels(void);
extern "C" unsigned int kairo98_audio_buffer_frames(void);
extern "C" int kairo98_fill_audio(short *destination, unsigned int frames);

#if defined(KAIRO98_PGO_GENERATE)
extern "C" void __llvm_profile_set_filename(const char *name);
extern "C" int __llvm_profile_write_file(void);
#endif

namespace {
enum class CommandType { Pause, Resume, Reset, Stop, Key, Disk, Floppy, Clock, MouseMove, MouseWarp, MouseButton, Joystick, SaveState, LoadState };
struct Command {
    CommandType type;
    int key = 0;
    bool down = false;
    std::string path;
    int y = 0;
    std::shared_ptr<std::promise<int>> completion;
    unsigned generation = 0;
};
std::mutex command_mutex;
std::condition_variable command_ready;
std::deque<Command> commands;
std::thread worker;
std::mutex lifecycle_mutex;
bool active = false;
std::string machine_state = "Stopped";
std::string machine_error;
unsigned long long frame_count = 0;
unsigned short last_cs = 0, last_ip = 0;
unsigned long long audio_buffers = 0;
unsigned long long audible_buffers = 0;
int32_t audio_xruns = 0;
std::string audio_state = "off";
std::string audio_config;
std::string audio_profile;
std::atomic<bool> audio_muted{false};
// While set, frames run back to back instead of on the 60 Hz grid, and audio
// is written only as far as the output buffer has room.
std::atomic<bool> fast_forward{false};
std::atomic<bool> dos_prompt_ready{false};
std::atomic<unsigned> mouse_warp_requested{0};
// A passive host-side observation of the complete 640x400 RGB565 guest image.
// The emulator core neither knows about nor depends on startup automation.
std::atomic<bool> screen_hash_sampling{false};
std::atomic<uint64_t> screen_hash{0};
std::atomic<uint64_t> screen_hash_serial{0};

void clear_screen_hash() {
    screen_hash.store(0, std::memory_order_relaxed);
    screen_hash_serial.fetch_add(1, std::memory_order_release);
}

uint64_t hash_guest_frame() {
    const auto *pixels = kairo98_frame_pixels();
    uint64_t hash = 14695981039346656037ULL;
    for (size_t i = 0; i < 640 * 400; ++i) {
        const uint16_t pixel = pixels[i];
        hash = (hash ^ static_cast<uint8_t>(pixel)) * 1099511628211ULL;
        hash = (hash ^ static_cast<uint8_t>(pixel >> 8)) * 1099511628211ULL;
    }
    return hash;
}

// The UI thread hands the current ANativeWindow to the presenter through
// `window`; the presenter takes its own reference when it attaches, so the UI
// thread may release its reference at any time. `surface_generation` tells
// both threads that the window changed.
std::mutex window_mutex;
ANativeWindow *window = nullptr;
std::atomic<unsigned int> surface_generation{0};

// Presentation runs on its own thread with a GPU renderer (gl_presenter.h).
//
// ANativeWindow_lock and eglSwapBuffers both wait on the compositor, so doing
// them on the emulation thread makes guest time wait on the display. The
// worker hands each redrawn guest frame to the presenter as a packet from
// android_host/gpudraw.c and continues. Frames are shown as soon as they are
// ready; when the presenter falls behind, it shows the newest frame and drops
// the ones it could not show in time.
struct PresentFrame {
    kairo98_gpu_frame_t data;
    std::chrono::steady_clock::time_point due;
};
std::mutex present_mutex;
std::condition_variable present_ready;
std::deque<std::unique_ptr<PresentFrame>> present_queue;
std::vector<std::unique_ptr<PresentFrame>> present_pool;
bool present_stop = false;
unsigned int present_dropped = 0;

void present_loop() {
    GlPresenter renderer;
    unsigned int attached_generation = ~0u;
    for (;;) {
        std::unique_ptr<PresentFrame> frame;
        {
            std::unique_lock<std::mutex> guard(present_mutex);
            for (;;) {
                present_ready.wait(guard, [&] {
                    return !present_queue.empty() || present_stop ||
                           surface_generation.load(std::memory_order_relaxed) != attached_generation;
                });
                if (present_stop) return;
                if (surface_generation.load(std::memory_order_relaxed) != attached_generation) break;
                const auto due = present_queue.front()->due;
                if (present_ready.wait_until(guard, due, [] { return present_stop; })) return;
                // Past due. If the following frame is due as well, skip this one.
                if (present_queue.size() >= 2 &&
                    present_queue[1]->due <= std::chrono::steady_clock::now()) {
                    present_pool.push_back(std::move(present_queue.front()));
                    present_queue.pop_front();
                    ++present_dropped;
                    continue;
                }
                break;
            }
            if (!present_queue.empty() &&
                surface_generation.load(std::memory_order_relaxed) == attached_generation) {
                frame = std::move(present_queue.front());
                present_queue.pop_front();
            }
        }
        const unsigned int generation = surface_generation.load(std::memory_order_relaxed);
        if (generation != attached_generation) {
            ANativeWindow *target = nullptr;
            {
                std::lock_guard<std::mutex> guard(window_mutex);
                target = window;
            }
            attached_generation = generation;
            renderer.attach(target);
            if (renderer.ready()) renderer.draw();
        }
        if (frame) {
            renderer.apply(frame->data);
#if defined(KAIRO98_GPU_VERIFY)
            {
                static unsigned long long verified_frames = 0, bad_pixels = 0;
                bad_pixels += renderer.verify(frame->data);
                if (++verified_frames % 300 == 0) {
                    __android_log_print(ANDROID_LOG_INFO, "Kairo98Perf",
                        "gpu verify frames=%llu badpixels=%llu", verified_frames, bad_pixels);
                }
            }
#endif
            renderer.draw();
            std::lock_guard<std::mutex> guard(present_mutex);
            present_pool.push_back(std::move(frame));
        }
    }
}

void queue_present(std::chrono::steady_clock::time_point due) {
    std::unique_ptr<PresentFrame> frame;
    {
        std::lock_guard<std::mutex> guard(present_mutex);
        if (!present_pool.empty()) {
            frame = std::move(present_pool.back());
            present_pool.pop_back();
        } else if (present_queue.size() >= 3) {
            frame = std::move(present_queue.front());
            present_queue.pop_front();
            ++present_dropped;
        }
    }
    if (!frame) frame = std::make_unique<PresentFrame>();
    kairo98_gpudraw_take(&frame->data);
    frame->due = due;
    {
        std::lock_guard<std::mutex> guard(present_mutex);
        present_queue.push_back(std::move(frame));
    }
    present_ready.notify_one();
}

void report_state(const char *state, const char *error = "") {
    std::lock_guard<std::mutex> guard(command_mutex);
    machine_state = state;
    machine_error = error;
}

void run_machine(std::string image, std::string font_path, std::string bios_dir,
                 int mhz_times_ten, int gdc_mhz_times_ten, int cpu_multiple,
                 bool floppy, std::string boot_floppy, std::string second_floppy) {
    dos_prompt_ready.store(false);
    clear_screen_hash();
    kairo98_input_telemetry_reset();
    kairo98_joy_release_all();
    unsigned int prompt_frames = 0;
    int start_result = kairo98_machine_start(image.c_str(), font_path.c_str(), bios_dir.c_str(),
                                             mhz_times_ten,
                                             gdc_mhz_times_ten, cpu_multiple, floppy ? 1 : 0,
                                             boot_floppy.c_str(), second_floppy.c_str());
    if (start_result != 0) {
        report_state("Error", start_result == 2 ? "HDI did not mount" :
                              start_result == 3 ? "Invalid clock setting" :
                              start_result == 5 ? "Floppy A did not mount" :
                              start_result == 6 ? "Floppy B did not mount" : "Disk image path is too long");
        std::lock_guard<std::mutex> guard(command_mutex);
        active = false;
        return;
    }
    report_state("Running");
#if defined(KAIRO98_PGO_GENERATE)
    {
        const std::string profile = bios_dir + "/kairo98-%m.profraw";
        __llvm_profile_set_filename(profile.c_str());
        __android_log_print(ANDROID_LOG_INFO, "Kairo98Perf", "PGO profile: %s", profile.c_str());
    }
#endif
    // Emulation competes with the UI and compositor for four small cores;
    // give it the same standing Android gives urgent display work.
    if (setpriority(PRIO_PROCESS, 0, -16) != 0) {
        __android_log_print(ANDROID_LOG_INFO, "Kairo98Perf", "worker priority unchanged");
    }
    {
        std::lock_guard<std::mutex> guard(present_mutex);
        present_queue.clear();
        present_pool.clear();
        present_stop = false;
        present_dropped = 0;
    }
    kairo98_gpudraw_set_enabled(screen_hash_sampling.load(std::memory_order_relaxed) ? 0 : 1);
    std::thread presenter(present_loop);
    AAudioStream *audio = nullptr;
    AAudioStreamBuilder *builder = nullptr;
    if (kairo98_audio_buffer_frames() == 512 && AAudio_createStreamBuilder(&builder) == AAUDIO_OK) {
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setSampleRate(builder, 44100);
        AAudioStreamBuilder_setChannelCount(builder, 2);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        if (AAudioStreamBuilder_openStream(builder, &audio) != AAUDIO_OK ||
            AAudioStream_requestStart(audio) != AAUDIO_OK) {
            if (audio) AAudioStream_close(audio);
            audio = nullptr;
        }
        if (audio) {
            // Keep about three emulated frames (50 ms) of audio queued: enough
            // to ride out a game-logic frame that overruns its slot, without
            // the default 80 ms of output latency.
            const int32_t burst = AAudioStream_getFramesPerBurst(audio);
            const int32_t wanted = std::max<int32_t>(3 * 735, 2 * std::max<int32_t>(burst, 1));
            AAudioStream_setBufferSizeInFrames(audio, wanted);
        }
    }
    if (builder) AAudioStreamBuilder_delete(builder);
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        audio_state = audio ? "on" : "off";
        if (audio) {
            char config[80];
            std::snprintf(config, sizeof(config), "buf %d/%d/%d",
                AAudioStream_getBufferSizeInFrames(audio),
                AAudioStream_getBufferCapacityInFrames(audio),
                AAudioStream_getFramesPerBurst(audio));
            audio_config = config;
        } else audio_config.clear();
    }
    bool paused = false;
    bool stop = false;
    unsigned int audio_due = 0;
    short audio_samples[512 * 2];
    auto next_frame = std::chrono::steady_clock::now();
    auto next_hash = next_frame;
    unsigned int profile_frames = 0;
    int64_t profile_core_us = 0, profile_render_us = 0;
    int64_t profile_mix_us = 0, profile_write_us = 0;
    int64_t profile_max_core_us = 0, profile_max_render_us = 0;
    int64_t profile_max_late_us = 0;
    unsigned int profile_late_frames = 0;
    int32_t profile_previous_xruns = 0;
    int32_t profile_partial_writes = 0;
    int32_t profile_buffered_frames = 0;
    unsigned long long profile_previous_slices = 0, profile_previous_insts = 0;
    unsigned long long profile_previous_sti = 0, profile_previous_skips = 0;
    unsigned long long frame_previous_slices = 0, frame_previous_insts = 0;
    unsigned long long frame_previous_skips = 0;
    unsigned long long frame_previous_draws = ~0ull;
    unsigned int frame_previous_surface = ~0u;
    unsigned int profile_big_frames = 0;
    unsigned int profile_heavy_frames = 0;
    int64_t profile_heavy_us = 0;
    unsigned int profile_previous_dropped = 0;
    unsigned long long profile_previous_cpu_ns = 0, profile_previous_event_ns = 0;
    unsigned long long profile_previous_draw_ns = 0, profile_previous_fm_ns = 0;
    unsigned long long profile_previous_egc_writes = 0, profile_previous_egc_reads = 0;
    unsigned long long profile_previous_egc_fast = 0;
    unsigned long long profile_previous_synth_waits = 0;
    while (!stop) {
        std::deque<Command> pending;
        {
            std::unique_lock<std::mutex> guard(command_mutex);
            if (paused && commands.empty()) {
                command_ready.wait(guard, [] { return !commands.empty(); });
            }
            pending.swap(commands);
        }
        for (const auto &command : pending) {
            switch (command.type) {
                case CommandType::Pause:
                    paused = true;
                    kairo98_machine_release_keys();
                    kairo98_joy_release_all();
                    if (audio) AAudioStream_requestPause(audio);
                    report_state("Paused");
                    break;
                case CommandType::Resume:
                    paused = false;
                    if (audio) AAudioStream_requestStart(audio);
                    next_frame = std::chrono::steady_clock::now();
                    report_state("Running");
                    break;
                case CommandType::Reset:
                    kairo98_input_telemetry_reset();
                    kairo98_joy_release_all();
                    prompt_frames = 0;
                    dos_prompt_ready.store(false);
                    clear_screen_hash();
                    if (kairo98_machine_reset() != 0) {
                        report_state("Error", "HDI flush failed before reset");
                        stop = true;
                    }
                    next_frame = std::chrono::steady_clock::now();
                    break;
                case CommandType::Key:
                    kairo98_machine_key(static_cast<unsigned char>(command.key), command.down);
                    break;
                case CommandType::Disk:
                    kairo98_input_telemetry_reset();
                    kairo98_joy_release_all();
                    prompt_frames = 0;
                    dos_prompt_ready.store(false);
                    clear_screen_hash();
                    if (int disk_result = kairo98_machine_set_disk(command.path.c_str()); disk_result != 0) {
                        report_state("Error", disk_result == 3 ? "HDI flush failed before disk change" : "HDI did not mount after disk change");
                        stop = true;
                    }
                    break;
                case CommandType::Floppy: {
                    int result = kairo98_machine_set_floppy(command.key, command.path.c_str());
                    if (command.completion) command.completion->set_value(result);
                    break;
                }
                case CommandType::Clock:
                    kairo98_input_telemetry_reset();
                    kairo98_joy_release_all();
                    prompt_frames = 0;
                    dos_prompt_ready.store(false);
                    clear_screen_hash();
                    if (int result = kairo98_machine_set_clock(command.key); result != 0) {
                        report_state("Error", result == 1 ? "HDI flush failed before clock change" : "Invalid clock setting");
                        stop = true;
                    }
                    next_frame = std::chrono::steady_clock::now();
                    break;
                case CommandType::Stop:
                    kairo98_joy_release_all();
                    stop = true;
                    break;
                case CommandType::MouseMove:
                    kairo98_mouse_move(command.key, command.y);
                    break;
                case CommandType::MouseWarp:
                    kairo98_mouse_warp(command.key, command.y, command.generation);
                    break;
                case CommandType::MouseButton:
                    kairo98_mouse_button(command.key, command.down);
                    break;
                case CommandType::Joystick:
                    kairo98_joy_set(command.key, command.down);
                    break;
                case CommandType::SaveState: {
                    int result = kairo98_machine_save_state(command.path.c_str());
                    if (command.completion) command.completion->set_value(result);
                    break;
                }
                case CommandType::LoadState: {
                    kairo98_input_telemetry_reset();
                    kairo98_joy_release_all();
                    prompt_frames = 0;
                    dos_prompt_ready.store(false);
                    clear_screen_hash();
                    int result = kairo98_machine_load_state(command.path.c_str());
                    next_frame = std::chrono::steady_clock::now();
                    if (command.completion) command.completion->set_value(result);
                    break;
                }
            }
        }
        if (stop || paused) continue;
        const auto core_start = std::chrono::steady_clock::now();
        kairo98_machine_exec();
        const auto core_end = std::chrono::steady_clock::now();
        const auto core_us = std::chrono::duration_cast<std::chrono::microseconds>(core_end - core_start).count();
        profile_core_us += core_us;
        profile_max_core_us = std::max(profile_max_core_us, static_cast<int64_t>(core_us));
        {
            unsigned long long slices = 0, insts = 0, sti = 0, skips = 0;
            kairo98_machine_counters(&slices, &insts, &sti, &skips);
            if (core_us > 12000) {
                ++profile_heavy_frames;
                profile_heavy_us += core_us;
            }
            if (core_us > 20000) {
                ++profile_big_frames;
                unsigned short cs = 0, ip = 0;
                kairo98_machine_location(&cs, &ip);
                __android_log_print(ANDROID_LOG_INFO, "Kairo98Perf",
                    "slow frame core=%lldus slices=%llu insts=%llu skips=%llu cs:ip=%04x:%04x",
                    static_cast<long long>(core_us), slices - frame_previous_slices,
                    insts - frame_previous_insts, skips - frame_previous_skips, cs, ip);
            }
            frame_previous_slices = slices;
            frame_previous_insts = insts;
            frame_previous_skips = skips;
        }
        prompt_frames = kairo98_machine_dos_prompt() ? prompt_frames + 1 : 0;
        dos_prompt_ready.store(prompt_frames >= 15);
        if (screen_hash_sampling.load(std::memory_order_relaxed) &&
            std::chrono::steady_clock::now() >= next_hash) {
            screen_hash.store(hash_guest_frame(), std::memory_order_relaxed);
            screen_hash_serial.fetch_add(1, std::memory_order_release);
            next_hash = std::chrono::steady_clock::now() + std::chrono::milliseconds(100);
        }
        // Only hand a frame to the presenter when the core redrew something;
        // an unchanged picture needs no 512 KB copy.
        {
            const unsigned long long draws = kairo98_machine_draw_count();
            const unsigned int surface = surface_generation.load(std::memory_order_relaxed);
            if (draws != frame_previous_draws || surface != frame_previous_surface) {
                queue_present(core_end);
                frame_previous_draws = draws;
                frame_previous_surface = surface;
            }
        }
        const auto render_end = std::chrono::steady_clock::now();
        const auto render_us = std::chrono::duration_cast<std::chrono::microseconds>(render_end - core_end).count();
        profile_render_us += render_us;
        profile_max_render_us = std::max(profile_max_render_us, static_cast<int64_t>(render_us));
        audio_due += 44100;
        while (audio_due >= 60 * 512) {
            audio_due -= 60 * 512;
            const auto mix_start = std::chrono::steady_clock::now();
            int filled = kairo98_fill_audio(audio_samples, 512);
            const auto mix_end = std::chrono::steady_clock::now();
            profile_mix_us += std::chrono::duration_cast<std::chrono::microseconds>(mix_end - mix_start).count();
            if (audio_muted.load()) std::memset(audio_samples, 0, sizeof(audio_samples));
            if (audio) {
                aaudio_result_t written = AAudioStream_write(audio, audio_samples, 512,
                    fast_forward.load(std::memory_order_relaxed) ? 0 : 2000000);
                if (written != 512) ++profile_partial_writes;
                profile_buffered_frames = static_cast<int32_t>(
                    AAudioStream_getFramesWritten(audio) - AAudioStream_getFramesRead(audio));
                profile_write_us += std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now() - mix_end).count();
                const int32_t xruns = AAudioStream_getXRunCount(audio);
                if (written > 0) {
                    bool audible = false;
                    for (int i = 0; i < written * 2; ++i) {
                        if (audio_samples[i]) { audible = true; break; }
                    }
                    std::lock_guard<std::mutex> guard(command_mutex);
                    ++audio_buffers;
                    if (filled && audible) ++audible_buffers;
                    if (xruns >= 0) audio_xruns = xruns;
                }
            }
        }
        unsigned short cs = 0, ip = 0;
        kairo98_machine_location(&cs, &ip);
        {
            std::lock_guard<std::mutex> guard(command_mutex);
            ++frame_count;
            last_cs = cs;
            last_ip = ip;
        }
        if (++profile_frames == 600) {
            unsigned long long slices = 0, insts = 0, sti = 0, skips = 0;
            kairo98_machine_counters(&slices, &insts, &sti, &skips);
            unsigned int dropped;
            {
                std::lock_guard<std::mutex> guard(present_mutex);
                dropped = present_dropped;
            }
            unsigned long long cpu_ns = 0, event_ns = 0, draw_ns = 0, fm_ns = 0;
            kairo98_machine_stage_times(&cpu_ns, &event_ns, &draw_ns, &fm_ns);
            unsigned long long egc_writes = 0, egc_reads = 0, egc_fast = 0, egc_bad = 0;
            kairo98_machine_egc_stats(&egc_writes, &egc_reads, &egc_fast, &egc_bad);
            unsigned long long synth_segments = 0, synth_waits = 0, synth_bad = 0;
            kairo_ymfm_stats(&synth_segments, &synth_waits, &synth_bad);
            unsigned int egc_snap[10] = {0};
            unsigned long long egc_dec = 0, egc_cpu = 0;
            kairo98_machine_egc_snapshot(egc_snap, &egc_dec, &egc_cpu);
            __android_log_print(ANDROID_LOG_INFO, "Kairo98Perf",
                "egc snapshot sft=%04x leng=%04x ope=%04x func=%u stack=%u srcbit=%u dstbit=%u "
                "remain=%u ptrdelta=%d fgbg=%04x decreads=%llu cpureads=%llu",
                egc_snap[0], egc_snap[1], egc_snap[2], egc_snap[3], egc_snap[4], egc_snap[5],
                egc_snap[6], egc_snap[7], static_cast<int>(egc_snap[8]), egc_snap[9],
                egc_dec, egc_cpu);
            char result[360];
            std::snprintf(result, sizeof(result),
                "c%.1f r%.1f m%.1f w%.1f x+%d p%d q%d maxC%.1f maxR%.1f late%d/%.1f "
                "sl%.0f in%.0f sti%.0f sk%.0f big%u drop%u cpu%.1f ev%.1f dr%.1f fm%.1f "
                "ew%.0f er%.0f ef%.0f egcbad%llu hv%u/%.1f sw%llu synbad%llu",
                profile_core_us / 600000.0, profile_render_us / 600000.0,
                profile_mix_us / 600000.0, profile_write_us / 600000.0,
                audio_xruns - profile_previous_xruns, profile_partial_writes,
                profile_buffered_frames, profile_max_core_us / 1000.0,
                profile_max_render_us / 1000.0, profile_late_frames,
                profile_max_late_us / 1000.0,
                (slices - profile_previous_slices) / 600.0,
                (insts - profile_previous_insts) / 600.0,
                (sti - profile_previous_sti) / 600.0,
                (skips - profile_previous_skips) / 600.0,
                profile_big_frames, dropped - profile_previous_dropped,
                (cpu_ns - profile_previous_cpu_ns) / 600.0e6,
                (event_ns - profile_previous_event_ns) / 600.0e6,
                (draw_ns - profile_previous_draw_ns) / 600.0e6,
                (fm_ns - profile_previous_fm_ns) / 600.0e6,
                (egc_writes - profile_previous_egc_writes) / 600.0,
                (egc_reads - profile_previous_egc_reads) / 600.0,
                (egc_fast - profile_previous_egc_fast) / 600.0, egc_bad,
                profile_heavy_frames,
                profile_heavy_frames ? profile_heavy_us / (profile_heavy_frames * 1000.0) : 0.0,
                synth_waits - profile_previous_synth_waits, synth_bad);
            profile_previous_synth_waits = synth_waits;
            profile_heavy_frames = 0;
            profile_heavy_us = 0;
            profile_previous_egc_writes = egc_writes;
            profile_previous_egc_reads = egc_reads;
            profile_previous_egc_fast = egc_fast;
            profile_previous_cpu_ns = cpu_ns;
            profile_previous_event_ns = event_ns;
            profile_previous_draw_ns = draw_ns;
            profile_previous_fm_ns = fm_ns;
            profile_previous_slices = slices;
            profile_previous_insts = insts;
            profile_previous_sti = sti;
            profile_previous_skips = skips;
            profile_previous_dropped = dropped;
            profile_big_frames = 0;
#if defined(KAIRO98_PGO_GENERATE)
            // The profiling harness never stops the machine, so persist the
            // counters at every status window; %m merges into one file.
            __llvm_profile_write_file();
#endif
            {
                std::lock_guard<std::mutex> guard(command_mutex);
                audio_profile = result;
            }
            profile_previous_xruns = audio_xruns;
            profile_frames = 0;
            profile_core_us = profile_render_us = profile_mix_us = profile_write_us = 0;
            profile_max_core_us = profile_max_render_us = profile_max_late_us = 0;
            profile_late_frames = 0;
            profile_partial_writes = 0;
        }
        // Frames are scheduled on a fixed 60 Hz grid. A frame that overruns
        // its slot leaves next_frame in the past, so the following frames
        // start immediately and the grid is recovered rather than shifted;
        // otherwise every overrun would permanently lose its excess time,
        // the emulator would run below 60 Hz, and the audio buffer would
        // drain. Only when the backlog exceeds three frames (a stall, not an
        // overrun) is the grid re-anchored to the present.
        next_frame += std::chrono::microseconds(16667);
        auto now = std::chrono::steady_clock::now();
        if (fast_forward.load(std::memory_order_relaxed)) {
            next_frame = now;
        } else if (next_frame < now) {
            ++profile_late_frames;
            profile_max_late_us = std::max(profile_max_late_us, static_cast<int64_t>(
                std::chrono::duration_cast<std::chrono::microseconds>(now - next_frame).count()));
            if (now - next_frame > std::chrono::microseconds(50000)) next_frame = now;
        }
        std::unique_lock<std::mutex> guard(command_mutex);
        command_ready.wait_until(guard, next_frame, [] { return !commands.empty(); });
    }
    if (audio) {
        AAudioStream_requestStop(audio);
        AAudioStream_close(audio);
    }
    {
        std::lock_guard<std::mutex> guard(present_mutex);
        present_stop = true;
    }
    present_ready.notify_one();
    presenter.join();
    int flush_result = kairo98_machine_stop();
    std::lock_guard<std::mutex> guard(command_mutex);
    active = false;
    audio_state = "off";
    if (flush_result != 0) {
        machine_state = "Error";
        machine_error = "HDI flush failed during stop";
    }
    if (machine_state != "Error") machine_state = "Stopped";
}

void enqueue(Command command) {
    std::lock_guard<std::mutex> guard(command_mutex);
    if (!active) return;
    commands.push_back(std::move(command));
    command_ready.notify_one();
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStart(JNIEnv *env, jobject, jstring image_path,
                              jstring font_path, jstring bios_dir,
                                                      jint mhz_times_ten,
                                                      jint gdc_mhz_times_ten,
                                                      jint cpu_multiple,
                                                      jboolean floppy, jstring boot_floppy_path,
                                                      jstring second_floppy_path) {
    std::lock_guard<std::mutex> lifecycle(lifecycle_mutex);
    if (worker.joinable()) return JNI_FALSE;
    const char *chars = image_path ? env->GetStringUTFChars(image_path, nullptr) : nullptr;
    std::string path = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(image_path, chars);
    const char *font_chars = font_path ? env->GetStringUTFChars(font_path, nullptr) : nullptr;
    std::string font = font_chars ? font_chars : "";
    if (font_chars) env->ReleaseStringUTFChars(font_path, font_chars);
    const char *bios_chars = bios_dir ? env->GetStringUTFChars(bios_dir, nullptr) : nullptr;
    std::string bios = bios_chars ? bios_chars : "";
    if (bios_chars) env->ReleaseStringUTFChars(bios_dir, bios_chars);
    const char *boot_chars = boot_floppy_path ? env->GetStringUTFChars(boot_floppy_path, nullptr) : nullptr;
    std::string boot_floppy = boot_chars ? boot_chars : "";
    if (boot_chars) env->ReleaseStringUTFChars(boot_floppy_path, boot_chars);
    const char *second_chars = second_floppy_path ? env->GetStringUTFChars(second_floppy_path, nullptr) : nullptr;
    std::string second_floppy = second_chars ? second_chars : "";
    if (second_chars) env->ReleaseStringUTFChars(second_floppy_path, second_chars);
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        commands.clear();
        frame_count = 0;
        audio_buffers = audible_buffers = 0;
        audio_xruns = 0;
        audio_profile.clear();
        audio_config.clear();
        audio_state = "off";
        last_cs = last_ip = 0;
        machine_error.clear();
        machine_state = "Starting";
        active = true;
    }
    fast_forward.store(false);
    worker = std::thread(run_machine, std::move(path), std::move(font), std::move(bios),
                         mhz_times_ten, gdc_mhz_times_ten, cpu_multiple,
                         floppy == JNI_TRUE, std::move(boot_floppy), std::move(second_floppy));
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStop(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lifecycle(lifecycle_mutex);
    enqueue({CommandType::Stop});
    if (worker.joinable()) worker.join();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativePause(JNIEnv *, jobject, jboolean paused) {
    enqueue({paused ? CommandType::Pause : CommandType::Resume});
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeReset(JNIEnv *, jobject) {
    dos_prompt_ready.store(false);
    enqueue({CommandType::Reset});
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeClock(JNIEnv *, jobject, jint mhz_times_ten) {
    if (mhz_times_ten == 20 || mhz_times_ten == 25) enqueue({CommandType::Clock, mhz_times_ten});
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeKey(JNIEnv *, jobject, jint key, jboolean down) {
    if (key >= 0 && key < 128) enqueue({CommandType::Key, key, down == JNI_TRUE});
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeMouseMove(JNIEnv *, jobject, jint dx, jint dy) {
    if (dx >= -640 && dx <= 640 && dy >= -400 && dy <= 400)
        enqueue({CommandType::MouseMove, dx, false, "", dy});
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeMouseWarp(JNIEnv *, jobject, jint x, jint y) {
    if (x < 0 || x >= 640 || y < 0 || y >= 400) return;
    Command command{CommandType::MouseWarp, x, false, "", y};
    command.generation = mouse_warp_requested.fetch_add(1) + 1;
    enqueue(std::move(command));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeMouseWarpDone(JNIEnv *, jobject) {
    return kairo98_mouse_warp_completed() == mouse_warp_requested.load() ? JNI_TRUE : JNI_FALSE;
}

namespace {
// Runs a state command on the emulation thread between frames and waits for its result.
jint run_state_command(JNIEnv *env, CommandType type, jstring dir) {
    if (!dir) return 1;
    const char *chars = env->GetStringUTFChars(dir, nullptr);
    if (!chars) return 1;
    std::string path(chars);
    env->ReleaseStringUTFChars(dir, chars);
    auto completion = std::make_shared<std::promise<int>>();
    auto response = completion->get_future();
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        if (!active) return 1;
        commands.push_back({type, 0, false, std::move(path), 0, completion});
        command_ready.notify_one();
    }
    if (response.wait_for(std::chrono::seconds(60)) != std::future_status::ready) return 6;
    return response.get();
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeSaveState(JNIEnv *env, jobject, jstring dir) {
    return run_state_command(env, CommandType::SaveState, dir);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeLoadState(JNIEnv *env, jobject, jstring dir) {
    return run_state_command(env, CommandType::LoadState, dir);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeMouseButton(JNIEnv *, jobject, jint button, jboolean down) {
    if (button == 1 || button == 2) enqueue({CommandType::MouseButton, button, down == JNI_TRUE});
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeJoystick(JNIEnv *, jobject, jint control, jboolean down) {
    if (control >= 0 && control < 6) enqueue({CommandType::Joystick, control, down == JNI_TRUE});
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeInputTelemetry(JNIEnv *env, jobject) {
    uint64_t waits = 0, polls = 0, mouse_reads = 0;
    int waiting = 0;
    kairo98_input_telemetry_snapshot(&waits, &polls, &mouse_reads, &waiting);
    jlong values[4] = {static_cast<jlong>(waits), static_cast<jlong>(polls),
                       static_cast<jlong>(mouse_reads), static_cast<jlong>(waiting)};
    jlongArray result = env->NewLongArray(4);
    if (result) env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeDisk(JNIEnv *env, jobject, jstring image_path) {
    if (!image_path) return;
    const char *chars = env->GetStringUTFChars(image_path, nullptr);
    if (!chars) return;
    std::string path(chars);
    env->ReleaseStringUTFChars(image_path, chars);
    enqueue({CommandType::Disk, 0, false, std::move(path)});
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeFloppy(JNIEnv *env, jobject, jint drive,
                                                        jstring image_path) {
    if (drive < 0 || drive > 1) return JNI_FALSE;
    const char *chars = image_path ? env->GetStringUTFChars(image_path, nullptr) : nullptr;
    std::string path = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(image_path, chars);
    auto completion = std::make_shared<std::promise<int>>();
    auto response = completion->get_future();
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        if (!active) return JNI_FALSE;
        commands.push_back({CommandType::Floppy, drive, false, std::move(path), 0, completion});
        command_ready.notify_one();
    }
    return response.wait_for(std::chrono::seconds(10)) == std::future_status::ready &&
        response.get() == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStatus(JNIEnv *env, jobject) {
    char text[320];
    std::lock_guard<std::mutex> guard(command_mutex);
    std::snprintf(text, sizeof(text), "%s%s%s | frames %llu | CS:IP %04x:%04x | audio %s %llu/%llu | xruns %d | %s | %s",
                  machine_state.c_str(), machine_error.empty() ? "" : ": ",
                  machine_error.c_str(), frame_count, last_cs, last_ip,
                  audio_state.c_str(), audible_buffers, audio_buffers, audio_xruns,
                  audio_config.c_str(), audio_profile.c_str());
    return env->NewStringUTF(text);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeDosPromptReady(JNIEnv *, jobject) {
    return dos_prompt_ready.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeSetScreenHashSampling(JNIEnv *, jobject,
                                                                        jboolean enabled) {
    screen_hash_sampling.store(enabled == JNI_TRUE, std::memory_order_relaxed);
    kairo98_gpudraw_set_enabled(enabled == JNI_TRUE ? 0 : 1);
    clear_screen_hash();
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeScreenHashSnapshot(JNIEnv *env, jobject) {
    const uint64_t before = screen_hash_serial.load(std::memory_order_acquire);
    const uint64_t hash = screen_hash.load(std::memory_order_relaxed);
    const uint64_t after = screen_hash_serial.load(std::memory_order_acquire);
    const jlong values[2] = {static_cast<jlong>(before == after ? after : 0),
                             static_cast<jlong>(before == after ? hash : 0)};
    jlongArray result = env->NewLongArray(2);
    if (result) env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}
extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeSetSurface(JNIEnv *env, jobject, jobject surface,
                                                          jint width, jint height) {
    ANativeWindow *replacement = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    (void)width;
    (void)height;
    ANativeWindow *previous = nullptr;
    {
        std::lock_guard<std::mutex> guard(window_mutex);
        previous = window;
        window = replacement;
        surface_generation.fetch_add(1, std::memory_order_relaxed);
    }
    present_ready.notify_one();
    // The presenter holds its own reference while it renders to a window.
    if (previous) ANativeWindow_release(previous);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeSetMuted(JNIEnv *, jobject, jboolean muted) {
    audio_muted.store(muted == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeSetFastForward(JNIEnv *, jobject, jboolean enabled) {
    fast_forward.store(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeInitializeAndReset(JNIEnv *env, jobject) {
    unsigned short cs = 0;
    unsigned short ip = 0;
    int result = kairo98_core_probe(&cs, &ip);
    char message[120];
    std::snprintf(message, sizeof(message),
                  result == 0 ? "21/W core initialized and reset: CS:IP=%04x:%04x"
                              : "21/W core reset failed: CS:IP=%04x:%04x",
                  cs, ip);
    return env->NewStringUTF(message);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeInspectHdi(JNIEnv *env, jobject,
                                                            jstring image_path) {
    if (image_path == nullptr) {
        return env->NewStringUTF("No HDI path was provided.");
    }
    const char *path = env->GetStringUTFChars(image_path, nullptr);
    if (path == nullptr) {
        return nullptr;
    }
    unsigned int cylinders = 0, surfaces = 0, sectors = 0;
    unsigned int sector_size = 0, first_word = 0;
    int result = kairo98_hdi_probe(path, &cylinders, &surfaces, &sectors,
                                  &sector_size, &first_word);
    env->ReleaseStringUTFChars(image_path, path);
    char message[180];
    if (result == 0) {
        std::snprintf(message, sizeof(message),
                      "HDI mounted and sector 0 read: %u cylinders, %u heads, "
                      "%u sectors, %u bytes/sector; first word %04x",
                      cylinders, surfaces, sectors, sector_size, first_word);
    } else {
        std::snprintf(message, sizeof(message),
                      "HDI %s failed (code %d)",
                      result == 1 ? "mount" : "sector read", result);
    }
    return env->NewStringUTF(message);
}
