#include <jni.h>
#include <cstdio>
#include <android/log.h>
#include <android/native_window.h>
#include <sys/resource.h>
#include <android/native_window_jni.h>
#include <array>
#include "android_host/gpudraw.h"
#include "gl_presenter.h"
#include "gpu_frame_merge.h"
#include "kairo/audio_output.h"
#include "kairo/jni_strings.h"
#include "kairo/state_paths.h"
#include "kairo/startup_typer.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <future>
#include <map>
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
                                      const char *boot_floppy, const char *second_floppy,
                                      const char *cd_image);
extern "C" int kairo98_machine_dos_prompt(void);
extern "C" void kairo98_machine_skip_memory_check(int skip);
extern "C" unsigned int kairo98_machine_exec(void);
extern "C" void kairo98_machine_clock_rates(unsigned int *realclock, unsigned int *hzbase,
                                            unsigned int *clockbase);
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
extern "C" int kairo98_machine_locks(void);
extern "C" unsigned kairo98_mouse_warp_completed(void);
extern "C" void kairo98_joy_set(int control, int down);
extern "C" void kairo98_joy_release_all(void);
extern "C" void kairo98_input_telemetry_reset(void);
extern "C" void kairo98_input_telemetry_snapshot(uint64_t *, uint64_t *, uint64_t *, int *);
extern "C" void kairo98_machine_release_keys(void);
extern "C" int kairo98_machine_set_disk(const char *image);
extern "C" int kairo98_machine_set_floppy(int drive, const char *image);
extern "C" int kairo98_machine_set_cd(const char *image);
extern "C" const char *kairo98_machine_media_path(int drive);
extern "C" int kairo98_machine_stop(void);
extern "C" void kairo98_machine_location(unsigned short *cs, unsigned short *ip);
extern "C" unsigned long long kairo98_machine_screen_signature(void);
extern "C" unsigned int kairo98_audio_buffer_frames(void);
extern "C" int kairo98_fill_audio(short *destination, unsigned int frames);

#if defined(KAIRO98_PGO_GENERATE)
extern "C" void __llvm_profile_set_filename(const char *name);
extern "C" int __llvm_profile_write_file(void);
#endif

namespace {
// How far the frame grid may fall behind before it re-anchors to the present;
// the audio queue holds this much so recoverable lateness never underruns.
constexpr auto kFrameStall = std::chrono::milliseconds(50);
constexpr int32_t kAudioStallFrames = 44100 * kFrameStall.count() / 1000;
enum class CommandType { Pause, Resume, Reset, Stop, Key, Disk, Floppy, Cd, MediaPaths, Clock, MouseMove, MouseWarp, MouseButton, Joystick,
                         SaveState, LoadState, StartupStart, StartupAdd, StartupResume };
struct Command {
    CommandType type;
    int key = 0;
    bool down = false;
    std::string path;
    int y = 0;
    std::shared_ptr<std::promise<int>> completion;
    unsigned generation = 0;
    std::vector<kairo::StartupStep> steps;
    // LoadState: each disk path a state may name, and where that disk is now (kairo/state_paths.h).
    std::map<std::string, std::string> moved;
    // MediaPaths: filled with each drive's image.
    std::shared_ptr<std::vector<std::string>> media;
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
// While set, frames run back to back instead of on the frame grid, and audio
// is written only as far as the output buffer has room.
std::atomic<bool> fast_forward{false};
std::atomic<bool> dos_prompt_ready{false};
// The startup typer's state for the UI (Kairo98#65): StartupIdle with nothing left to type,
// StartupTyping while steps remain, StartupStalled when it gave up waiting for startup_stalled's
// step, StartupFailed when a step could not be typed (startup_error, under command_mutex).
enum StartupState { StartupIdle, StartupTyping, StartupStalled, StartupFailed };
std::atomic<int> startup_state{StartupIdle};
std::atomic<int> startup_stalled{-1};
std::string startup_error;
// The PC-98's vertical refresh in its 400-line mode, which turns a step's timeout into frames.
constexpr double kGuestFramesPerSecond = 56.42;

// The guest as kairo::StartupTyper types into it: the DOS prompt once it has stood for 15 frames,
// the PC-98 keyboard's scan codes, and the displayed video memory's signature.
struct Pc98Typing {
    int prompt_frames = 0;
    kairo::Prompt prompt() const { return prompt_frames >= 15 ? kairo::Prompt::Line : kairo::Prompt::None; }
    bool typed_key(char c, int &code, bool &shift) {
        // Letters in A-Z order, then digits 1-9 and 0, at their PC-98 scan codes.
        static const unsigned char letters[26] = {0x1d, 0x2d, 0x2b, 0x1f, 0x12, 0x20, 0x21, 0x22, 0x17, 0x23, 0x24, 0x25,
            0x2f, 0x2e, 0x18, 0x19, 0x10, 0x13, 0x1e, 0x14, 0x16, 0x2c, 0x11, 0x2a, 0x15, 0x29};
        shift = false;
        if (c >= 'a' && c <= 'z') code = letters[c - 'a'];
        else if (c >= 'A' && c <= 'Z') { code = letters[c - 'A']; shift = true; }
        else if (c >= '1' && c <= '9') code = c - '1' + 1;
        else if (c == '0') code = 0x0a;
        else if (c == ' ') code = 0x34;
        else if (c == '\\') code = 0x0d;
        else if (c == '/') code = 0x32;
        else if (c == '.') code = 0x31;
        else if (c == '-') code = 0x0b;
        else if (c == ':') code = 0x27;
        else if (c == '_') code = 0x33;
        else return false;
        return true;
    }
    int special_key(kairo::SpecialKey key) {
        switch (key) {
        case kairo::SpecialKey::Return: return 0x1c;
        case kairo::SpecialKey::Shift: return 0x70;
        case kairo::SpecialKey::Ctrl: return 0x74;
        default: return 0x62 + static_cast<int>(key) - static_cast<int>(kairo::SpecialKey::F1);
        }
    }
    bool expand_command(std::string &) { return true; }
    void key(int code, bool down) { kairo98_machine_key(static_cast<unsigned char>(code), down ? 1 : 0); }
    uint64_t screen_signature() { return kairo98_machine_screen_signature(); }
};
// Caps and Kana as a loaded state left them, published before the load completes.
std::atomic<int> loaded_locks{0};
std::atomic<unsigned> mouse_warp_requested{0};
// A passive host-side observation of the guest's displayed video memory
// (kairo98_machine_screen_signature). Startup automation reads it; the
// emulator core neither knows about nor depends on it.
std::atomic<bool> screen_hash_sampling{false};
std::atomic<uint64_t> screen_hash{0};
std::atomic<uint64_t> screen_hash_serial{0};

void clear_screen_hash() {
    screen_hash.store(0, std::memory_order_relaxed);
    screen_hash_serial.fetch_add(1, std::memory_order_release);
}

// The UI thread hands the current ANativeWindow to the presenter through
// `window`; the presenter takes its own reference when it attaches, so the UI
// thread may release its reference at any time. `surface_generation` tells
// both threads that the window changed.
std::mutex window_mutex;
ANativeWindow *window = nullptr;
std::atomic<unsigned int> surface_generation{0};
std::mutex shader_mutex;
std::string shader_preset;
std::atomic<unsigned int> shader_generation{0};

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
    unsigned int applied_shader_generation = ~0u;
    for (;;) {
        std::unique_ptr<PresentFrame> frame;
        {
            std::unique_lock<std::mutex> guard(present_mutex);
            for (;;) {
                present_ready.wait(guard, [&] {
                    return !present_queue.empty() || present_stop ||
                           surface_generation.load(std::memory_order_relaxed) != attached_generation ||
                           shader_generation.load(std::memory_order_relaxed) != applied_shader_generation;
                });
                if (present_stop) return;
                if (surface_generation.load(std::memory_order_relaxed) != attached_generation ||
                    shader_generation.load(std::memory_order_relaxed) != applied_shader_generation) break;
                const auto due = present_queue.front()->due;
                if (present_ready.wait_until(guard, due, [] { return present_stop; })) return;
                // Past due. If the following frame is due as well, skip this one.
                if (present_queue.size() >= 2 &&
                    present_queue[1]->due <= std::chrono::steady_clock::now()) {
                    kairo98_merge_gpu_frames(present_queue[1]->data,
                                             present_queue.front()->data);
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
        const unsigned int selected_shader_generation = shader_generation.load(std::memory_order_relaxed);
        if (selected_shader_generation != applied_shader_generation) {
            std::string path;
            {
                std::lock_guard<std::mutex> guard(shader_mutex);
                path = shader_preset;
            }
            renderer.setShaderPreset(std::move(path));
            applied_shader_generation = selected_shader_generation;
            if (renderer.ready()) renderer.draw();
        }
        const unsigned int generation = surface_generation.load(std::memory_order_relaxed);
        if (generation != attached_generation) {
            ANativeWindow *target = nullptr;
            {
                std::lock_guard<std::mutex> guard(window_mutex);
                target = window;
                // Keep the window alive after the UI can replace its reference.
                if (target) ANativeWindow_acquire(target);
            }
            attached_generation = generation;
            renderer.attach(target);
            if (target) ANativeWindow_release(target);
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
            kairo98_merge_gpu_frames(present_queue.front()->data, frame->data);
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
                 bool floppy, std::string boot_floppy, std::string second_floppy,
                 std::string cd_image) {
    dos_prompt_ready.store(false);
    clear_screen_hash();
    kairo98_input_telemetry_reset();
    kairo98_joy_release_all();
    unsigned int prompt_frames = 0;
    int start_result = kairo98_machine_start(image.c_str(), font_path.c_str(), bios_dir.c_str(),
                                             mhz_times_ten,
                                             gdc_mhz_times_ten, cpu_multiple, floppy ? 1 : 0,
                                             boot_floppy.c_str(), second_floppy.c_str(),
                                             cd_image.c_str());
    if (start_result != 0) {
        report_state("Error", start_result == 2 ? "HDI did not mount" :
                              start_result == 3 ? "Invalid clock setting" :
                              start_result == 5 ? "Floppy A did not mount" :
                              start_result == 6 ? "Floppy B did not mount" :
                              start_result == 7 ? "CD did not mount" : "Disk image path is too long");
        std::lock_guard<std::mutex> guard(command_mutex);
        active = false;
        commands.clear();
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
    std::thread presenter(present_loop);
    std::unique_ptr<kairo::AudioOutput> audio;
    // Opens the stream, or a new one after Android stops the old one, and shows
    // it in Diagnostics.
    int32_t earlier_stream_underruns = 0;
    const auto open_audio = [&audio, &earlier_stream_underruns] {
        if (audio) earlier_stream_underruns += audio->underruns();
        audio.reset();
        if (kairo98_audio_buffer_frames() == 512) audio = kairo::AudioOutput::open(44100, kAudioStallFrames);
        std::lock_guard<std::mutex> guard(command_mutex);
        audio_state = audio ? "on" : "off";
        audio_config = audio ? audio->config() : "";
    };
    open_audio();
    bool paused = false;
    bool stop = false;
    // Guest clocks times hzbase not yet pulled as audio; one 512-sample buffer is
    // 512 * clockbase of it, the same ratio the core turns clocks into samples with.
    uint64_t audio_due = 0;
    short audio_samples[512 * 2];
    double profile_guest_seconds = 0;
    auto next_frame = std::chrono::steady_clock::now();
    auto next_hash = next_frame;
    kairo::StartupTyper typer;
    Pc98Typing typing;
    const auto publish_typer = [&] {
        startup_stalled.store(typer.stalled());
        startup_state.store(typer.stalled() >= 0 ? StartupStalled : typer.active() ? StartupTyping : StartupIdle);
    };
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
            // The worker's only wait. A command wakes it early so input is applied at
            // once, but the frame itself still waits for its slot on the grid below.
            std::unique_lock<std::mutex> guard(command_mutex);
            const auto has_commands = [] { return !commands.empty(); };
            if (paused) command_ready.wait(guard, has_commands);
            else command_ready.wait_until(guard, next_frame, has_commands);
            pending.swap(commands);
        }
        for (const auto &command : pending) {
            switch (command.type) {
                case CommandType::Pause:
                    paused = true;
                    kairo98_machine_release_keys();
                    kairo98_joy_release_all();
                    if (audio) audio->pause();
                    report_state("Paused");
                    break;
                case CommandType::Resume:
                    paused = false;
                    if (audio) audio->resume();
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
                case CommandType::StartupStart:
                    typer.start(std::move(command.steps));
                    publish_typer();
                    break;
                case CommandType::StartupAdd:
                    for (auto &step : command.steps) typer.add(std::move(step));
                    publish_typer();
                    break;
                case CommandType::StartupResume:
                    typer.resume();
                    publish_typer();
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
                case CommandType::Cd: {
                    int result = kairo98_machine_set_cd(command.path.c_str());
                    if (command.completion) command.completion->set_value(result);
                    break;
                }
                case CommandType::MediaPaths:
                    for (int drive = 0; drive < 3; ++drive) command.media->push_back(kairo98_machine_media_path(drive));
                    command.completion->set_value(0);
                    break;
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
                    // The clock change rescales hzbase / clockbase.
                    audio_due = 0;
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
                    int result;
                    {
                        kairo::StatePaths paths(std::move(command.moved));
                        result = kairo98_machine_load_state(command.path.c_str());
                    }
                    loaded_locks.store(kairo98_machine_locks());
                    next_frame = std::chrono::steady_clock::now();
                    if (command.completion) command.completion->set_value(result);
                    break;
                }
            }
        }
        if (stop || paused) continue;
        // Running a frame for every input event would run the guest faster than
        // its refresh rate, overfill the audio stream, and push next_frame ahead
        // of real time so audio starves once the input stops (#60).
        if (std::chrono::steady_clock::now() < next_frame) continue;
        const bool sample_screen = screen_hash_sampling.load(std::memory_order_relaxed);
        const auto core_start = std::chrono::steady_clock::now();
        const unsigned int frame_clocks = kairo98_machine_exec();
        const auto core_end = std::chrono::steady_clock::now();
        unsigned int realclock = 0, hzbase = 0, clockbase = 0;
        kairo98_machine_clock_rates(&realclock, &hzbase, &clockbase);
        if (clockbase == 0) {
            report_state("Error", "Sound stream has no clock rate");
            stop = true;
            continue;
        }
        profile_guest_seconds += static_cast<double>(frame_clocks) / realclock;
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
        if (typer.active()) {
            typing.prompt_frames = prompt_frames;
            std::string error;
            if (typer.frame(typing, error)) publish_typer();
            else {
                {
                    std::lock_guard<std::mutex> guard(command_mutex);
                    startup_error = error;
                }
                startup_state.store(StartupFailed);
            }
        }
        if (sample_screen &&
            std::chrono::steady_clock::now() >= next_hash) {
            screen_hash.store(kairo98_machine_screen_signature(), std::memory_order_relaxed);
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
        // Pull exactly what the frame produced. A fixed 735 samples (44100 / 60)
        // against a 56.4 Hz guest left 47 extra samples a frame, which the core's
        // full stream dropped: constant crackle (#60).
        audio_due += static_cast<uint64_t>(frame_clocks) * hzbase;
        const uint64_t audio_buffer_due = 512ull * clockbase;
        while (audio_due >= audio_buffer_due) {
            audio_due -= audio_buffer_due;
            const auto mix_start = std::chrono::steady_clock::now();
            int filled = kairo98_fill_audio(audio_samples, 512);
            const auto mix_end = std::chrono::steady_clock::now();
            profile_mix_us += std::chrono::duration_cast<std::chrono::microseconds>(mix_end - mix_start).count();
            if (audio_muted.load()) std::memset(audio_samples, 0, sizeof(audio_samples));
            if (audio) {
                const uint32_t written = audio->write(audio_samples, 512);
                if (written != 512) ++profile_partial_writes;
                profile_buffered_frames = static_cast<int32_t>(audio->queued());
                profile_write_us += std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now() - mix_end).count();
                if (written > 0) {
                    bool audible = false;
                    for (uint32_t i = 0; i < written * 2; ++i) {
                        if (audio_samples[i]) { audible = true; break; }
                    }
                    std::lock_guard<std::mutex> guard(command_mutex);
                    ++audio_buffers;
                    if (filled && audible) ++audible_buffers;
                    audio_xruns = earlier_stream_underruns + audio->underruns();
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
                "g%.2f c%.1f r%.1f m%.1f w%.1f x+%d p%d q%d maxC%.1f maxR%.1f late%d/%.1f "
                "sl%.0f in%.0f sti%.0f sk%.0f big%u drop%u cpu%.1f ev%.1f dr%.1f fm%.1f "
                "ew%.0f er%.0f ef%.0f egcbad%llu hv%u/%.1f sw%llu synbad%llu",
                600 / profile_guest_seconds,
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
            profile_guest_seconds = 0;
            profile_core_us = profile_render_us = profile_mix_us = profile_write_us = 0;
            profile_max_core_us = profile_max_render_us = profile_max_late_us = 0;
            profile_late_frames = 0;
            profile_partial_writes = 0;
        }
        // Each frame's slot lasts as long as the audio it made takes to play at
        // 44.1 kHz: a guest frame, 56.4 Hz in the normal 400-line mode. The
        // length comes from the core's samples-per-clock ratio rather than its
        // clock rate because that ratio is rounded (at the default clock it makes
        // 0.23% more than 44100 samples per guest second), and timing by clocks
        // would hold the audio queue that far above its target for good.
        // The slot is then stretched or shrunk by at most 0.5% to hold the queue
        // at its target, so the host clock cannot drift from the audio device's.
        // A frame that overruns its slot leaves next_frame in the past, so the
        // following frames start immediately and the grid is recovered rather
        // than shifted; otherwise every overrun would permanently lose its
        // excess time, the guest would run slow, and the audio queue would
        // drain. Only when the backlog exceeds kFrameStall (a stall, not an
        // overrun) is the grid re-anchored to the present.
        const double pace = audio ? audio->pace_scale() : 1.0;
        const double frame_samples = static_cast<double>(frame_clocks) * hzbase / clockbase;
        next_frame += std::chrono::nanoseconds(static_cast<int64_t>(frame_samples * 1e9 / 44100 * pace));
        auto now = std::chrono::steady_clock::now();
        if (fast_forward.load(std::memory_order_relaxed)) {
            next_frame = now;
        } else if (next_frame < now) {
            ++profile_late_frames;
            profile_max_late_us = std::max(profile_max_late_us, static_cast<int64_t>(
                std::chrono::duration_cast<std::chrono::microseconds>(now - next_frame).count()));
            if (now - next_frame > kFrameStall) next_frame = now;
        }
        if (audio && audio->lost_error() != AAUDIO_OK) {
            __android_log_print(ANDROID_LOG_ERROR, "Kairo98Audio", "stream stopped with %s; reopening",
                                AAudio_convertResultToText(audio->lost_error()));
            open_audio();
        }
    }
    audio.reset();
    {
        std::lock_guard<std::mutex> guard(present_mutex);
        present_stop = true;
    }
    present_ready.notify_one();
    presenter.join();
    int flush_result = kairo98_machine_stop();
    std::lock_guard<std::mutex> guard(command_mutex);
    active = false;
    // Unanswered commands break their promises, so a caller waiting on one returns.
    commands.clear();
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

/** A Java string argument, or an empty string for null. */
using kairo::utf8;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStart(JNIEnv *env, jobject, jstring image_path,
                                                      jstring font_path, jstring bios_dir,
                                                      jint mhz_times_ten,
                                                      jint gdc_mhz_times_ten,
                                                      jint cpu_multiple,
                                                      jboolean floppy, jstring boot_floppy_path,
                                                      jstring second_floppy_path,
                                                      jstring cd_path) {
    std::lock_guard<std::mutex> lifecycle(lifecycle_mutex);
    if (worker.joinable()) return JNI_FALSE;
    std::string path = utf8(env, image_path);
    std::string font = utf8(env, font_path);
    std::string bios = utf8(env, bios_dir);
    std::string boot_floppy = utf8(env, boot_floppy_path);
    std::string second_floppy = utf8(env, second_floppy_path);
    std::string cd = utf8(env, cd_path);
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
                         floppy == JNI_TRUE, std::move(boot_floppy), std::move(second_floppy),
                         std::move(cd));
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
Java_com_mrjackspade_kairo98_MainActivity_nativeSkipMemoryCheck(JNIEnv *, jobject, jboolean skip) {
    kairo98_machine_skip_memory_check(skip == JNI_TRUE ? 1 : 0);
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
jint run_state_command(JNIEnv *env, CommandType type, jstring dir,
                       std::map<std::string, std::string> moved = {}) {
    if (!dir) return 1;
    std::string path = utf8(env, dir);
    auto completion = std::make_shared<std::promise<int>>();
    auto response = completion->get_future();
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        if (!active) return 1;
        Command command{type, 0, false, std::move(path), 0, completion};
        command.moved = std::move(moved);
        commands.push_back(std::move(command));
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
Java_com_mrjackspade_kairo98_MainActivity_nativeLoadState(JNIEnv *env, jobject, jstring dir,
                                                          jobjectArray from, jobjectArray to) {
    std::map<std::string, std::string> moved;
    const auto earlier = kairo::utf8_strings(env, from), now = kairo::utf8_strings(env, to);
    for (size_t i = 0; i < earlier.size(); ++i) moved[earlier[i]] = now.at(i);
    return run_state_command(env, CommandType::LoadState, dir, std::move(moved));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeLoadedLocks(JNIEnv *, jobject) {
    return loaded_locks.load();
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
    std::string path = utf8(env, image_path);
    enqueue({CommandType::Disk, 0, false, std::move(path)});
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeFloppy(JNIEnv *env, jobject, jint drive,
                                                        jstring image_path) {
    if (drive < 0 || drive > 1) return JNI_FALSE;
    std::string path = utf8(env, image_path);
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeCd(JNIEnv *env, jobject, jstring image_path) {
    auto completion = std::make_shared<std::promise<int>>();
    auto response = completion->get_future();
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        if (!active) return JNI_FALSE;
        commands.push_back({CommandType::Cd, 0, false, utf8(env, image_path), 0, completion});
        command_ready.notify_one();
    }
    try {
        return response.get() == 0 ? JNI_TRUE : JNI_FALSE;
    } catch (const std::future_error &) {
        return JNI_FALSE;  // The machine stopped before changing the disc.
    }
}

// What floppy drives A and B and the CD-ROM drive hold, "" for an empty one; null when no machine runs.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeMediaPaths(JNIEnv *env, jobject) {
    auto completion = std::make_shared<std::promise<int>>();
    auto response = completion->get_future();
    auto media = std::make_shared<std::vector<std::string>>();
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        if (!active) return nullptr;
        Command command{CommandType::MediaPaths, 0, false, {}, 0, completion};
        command.media = media;
        commands.push_back(std::move(command));
        command_ready.notify_one();
    }
    try {
        response.get();
    } catch (const std::future_error &) {
        return nullptr;  // The machine stopped before answering.
    }
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(media->size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t drive = 0; drive < media->size(); ++drive)
        env->SetObjectArrayElement(array, static_cast<jsize>(drive), env->NewStringUTF((*media)[drive].c_str()));
    return array;
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

/** Steps as the UI gives them: each text, whether Return follows, the screen signatures it waits
 * for (none: the DOS prompt) and its timeout in milliseconds (0: none). */
static std::vector<kairo::StartupStep> startup_steps(JNIEnv *env, jobjectArray texts, jbooleanArray enters,
                                                     jobjectArray screens, jintArray timeouts_ms, bool now) {
    const jsize count = env->GetArrayLength(texts);
    std::vector<jboolean> enter(count);
    std::vector<jint> timeout(count);
    env->GetBooleanArrayRegion(enters, 0, count, enter.data());
    env->GetIntArrayRegion(timeouts_ms, 0, count, timeout.data());
    std::vector<kairo::StartupStep> steps(count);
    for (jsize i = 0; i < count; ++i) {
        auto text = static_cast<jstring>(env->GetObjectArrayElement(texts, i));
        steps[i].text = utf8(env, text);
        env->DeleteLocalRef(text);
        steps[i].enter = enter[i] == JNI_TRUE;
        auto hashes = static_cast<jlongArray>(env->GetObjectArrayElement(screens, i));
        const jsize hash_count = env->GetArrayLength(hashes);
        std::vector<jlong> values(hash_count);
        env->GetLongArrayRegion(hashes, 0, hash_count, values.data());
        env->DeleteLocalRef(hashes);
        steps[i].screens.assign(values.begin(), values.end());
        steps[i].now = now;
        steps[i].timeout_frames = static_cast<int>(timeout[i] * kGuestFramesPerSecond / 1000);
    }
    return steps;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStartupStart(JNIEnv *env, jobject, jobjectArray texts,
        jbooleanArray enters, jobjectArray screens, jintArray timeouts_ms) {
    Command command{CommandType::StartupStart};
    command.steps = startup_steps(env, texts, enters, screens, timeouts_ms, false);
    // Published before the worker takes it, so the UI never reads the typer as idle in between.
    startup_state.store(command.steps.empty() ? StartupIdle : StartupTyping);
    startup_stalled.store(-1);
    enqueue(std::move(command));
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStartupKeys(JNIEnv *env, jobject, jstring text, jboolean enter) {
    Command command{CommandType::StartupAdd};
    kairo::StartupStep step;
    step.text = utf8(env, text);
    step.enter = enter == JNI_TRUE;
    step.now = true;
    command.steps.push_back(std::move(step));
    enqueue(std::move(command));
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStartupResume(JNIEnv *, jobject) {
    startup_state.store(StartupTyping);
    startup_stalled.store(-1);
    enqueue({CommandType::StartupResume});
}

/** The typer's state (StartupState) and the step it stalled on, or -1. */
extern "C" JNIEXPORT jintArray JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStartupStatus(JNIEnv *env, jobject) {
    const jint values[2] = {startup_state.load(), startup_stalled.load()};
    jintArray result = env->NewIntArray(2);
    if (result) env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeStartupError(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> guard(command_mutex);
    return env->NewStringUTF(startup_error.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeDosPromptReady(JNIEnv *, jobject) {
    return dos_prompt_ready.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeSetScreenHashSampling(JNIEnv *, jobject,
                                                                        jboolean enabled) {
    screen_hash_sampling.store(enabled == JNI_TRUE, std::memory_order_relaxed);
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
Java_com_mrjackspade_kairo98_MainActivity_nativeSetShaderPreset(JNIEnv *env, jobject,
                                                                jstring preset) {
    std::string path = utf8(env, preset);
    {
        std::lock_guard<std::mutex> guard(shader_mutex);
        shader_preset = std::move(path);
        shader_generation.fetch_add(1, std::memory_order_relaxed);
    }
    present_ready.notify_one();
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
    const std::string image = utf8(env, image_path);
    const char *path = image.c_str();
    unsigned int cylinders = 0, surfaces = 0, sectors = 0;
    unsigned int sector_size = 0, first_word = 0;
    int result = kairo98_hdi_probe(path, &cylinders, &surfaces, &sectors,
                                  &sector_size, &first_word);
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
