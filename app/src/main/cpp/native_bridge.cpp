#include <jni.h>
#include <cstdio>
#include <android/native_window.h>
#include <android/native_window_jni.h>
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
                                      const char *bios_dir, int font_bitmap, int mhz_times_ten,
                                      int gdc_mhz_times_ten, int floppy, const char *boot_floppy);
extern "C" int kairo98_machine_dos_prompt(void);
extern "C" void kairo98_machine_exec(void);
extern "C" int kairo98_machine_reset(void);
extern "C" int kairo98_machine_set_clock(int mhz_times_ten);
extern "C" void kairo98_machine_key(unsigned char code, int down);
extern "C" void kairo98_mouse_move(int dx, int dy);
extern "C" void kairo98_mouse_button(int button, int down);
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

namespace {
enum class CommandType { Pause, Resume, Reset, Stop, Key, Disk, Floppy, Clock, MouseMove, MouseButton, Joystick };
struct Command {
    CommandType type;
    int key = 0;
    bool down = false;
    std::string path;
    int y = 0;
    std::shared_ptr<std::promise<int>> completion;
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
std::string audio_state = "off";
std::atomic<bool> audio_muted{false};
std::atomic<bool> dos_prompt_ready{false};
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

std::mutex window_mutex;
ANativeWindow *window = nullptr;

void render_frame() {
    std::lock_guard<std::mutex> guard(window_mutex);
    if (!window) return;
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) return;
    const auto *source = kairo98_frame_pixels();
    if (buffer.format == WINDOW_FORMAT_RGB_565 && buffer.width > 0 && buffer.height > 0) {
        static int mapped_width = 0;
        static std::vector<int> source_x;
        if (mapped_width != buffer.width) {
            source_x.resize(buffer.width);
            for (int x = 0; x < buffer.width; ++x) {
                source_x[x] = static_cast<long long>(x) * 640 / buffer.width;
            }
            mapped_width = buffer.width;
        }
        int previous_source_y = -1;
        for (int y = 0; y < buffer.height; ++y) {
            auto *target = static_cast<unsigned short *>(buffer.bits) + y * buffer.stride;
            const int source_y = static_cast<long long>(y) * 400 / buffer.height;
            if (source_y == previous_source_y) {
                std::memcpy(target, target - buffer.stride, buffer.width * sizeof(*target));
                continue;
            }
            const auto *row = source + source_y * 640;
            if (buffer.width == 640) {
                std::memcpy(target, row, 640 * sizeof(*target));
            } else {
                for (int x = 0; x < buffer.width; ++x) {
                    target[x] = row[source_x[x]];
                }
            }
            previous_source_y = source_y;
        }
    }
    ANativeWindow_unlockAndPost(window);
}

void report_state(const char *state, const char *error = "") {
    std::lock_guard<std::mutex> guard(command_mutex);
    machine_state = state;
    machine_error = error;
}

void run_machine(std::string image, std::string font_path, std::string bios_dir,
                 bool font_bitmap, int mhz_times_ten, int gdc_mhz_times_ten, bool floppy,
                 std::string boot_floppy) {
    dos_prompt_ready.store(false);
    clear_screen_hash();
    kairo98_input_telemetry_reset();
    kairo98_joy_release_all();
    unsigned int prompt_frames = 0;
    int start_result = kairo98_machine_start(image.c_str(), font_path.c_str(), bios_dir.c_str(),
                                             font_bitmap ? 1 : 0, mhz_times_ten,
                                             gdc_mhz_times_ten, floppy ? 1 : 0,
                                             boot_floppy.c_str());
    if (start_result != 0) {
        report_state("Error", start_result == 2 ? "HDI did not mount" :
                              start_result == 3 ? "Invalid clock setting" :
                              start_result == 4 ? "PC-98 font cache missing or invalid" :
                              start_result == 5 ? "Floppy did not mount" : "Disk image path is too long");
        std::lock_guard<std::mutex> guard(command_mutex);
        active = false;
        return;
    }
    report_state("Running");
    AAudioStream *audio = nullptr;
    AAudioStreamBuilder *builder = nullptr;
    if (kairo98_audio_buffer_frames() == 512 && AAudio_createStreamBuilder(&builder) == AAUDIO_OK) {
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setSampleRate(builder, 44100);
        AAudioStreamBuilder_setChannelCount(builder, 2);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        if (AAudioStreamBuilder_openStream(builder, &audio) != AAUDIO_OK ||
            AAudioStream_requestStart(audio) != AAUDIO_OK) {
            if (audio) AAudioStream_close(audio);
            audio = nullptr;
        }
    }
    if (builder) AAudioStreamBuilder_delete(builder);
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        audio_state = audio ? "on" : "off";
    }
    bool paused = false;
    bool stop = false;
    unsigned int audio_due = 0;
    short audio_samples[512 * 2];
    auto next_frame = std::chrono::steady_clock::now();
    auto next_hash = next_frame;
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
                case CommandType::MouseButton:
                    kairo98_mouse_button(command.key, command.down);
                    break;
                case CommandType::Joystick:
                    kairo98_joy_set(command.key, command.down);
                    break;
            }
        }
        if (stop || paused) continue;
        kairo98_machine_exec();
        prompt_frames = kairo98_machine_dos_prompt() ? prompt_frames + 1 : 0;
        dos_prompt_ready.store(prompt_frames >= 15);
        if (screen_hash_sampling.load(std::memory_order_relaxed) &&
            std::chrono::steady_clock::now() >= next_hash) {
            screen_hash.store(hash_guest_frame(), std::memory_order_relaxed);
            screen_hash_serial.fetch_add(1, std::memory_order_release);
            next_hash = std::chrono::steady_clock::now() + std::chrono::milliseconds(100);
        }
        render_frame();
        audio_due += 44100;
        while (audio_due >= 60 * 512) {
            audio_due -= 60 * 512;
            int filled = kairo98_fill_audio(audio_samples, 512);
            if (audio_muted.load()) std::memset(audio_samples, 0, sizeof(audio_samples));
            if (audio) {
                aaudio_result_t written = AAudioStream_write(audio, audio_samples, 512, 2000000);
                if (written > 0) {
                    bool audible = false;
                    for (int i = 0; i < written * 2; ++i) {
                        if (audio_samples[i]) { audible = true; break; }
                    }
                    std::lock_guard<std::mutex> guard(command_mutex);
                    ++audio_buffers;
                    if (filled && audible) ++audible_buffers;
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
        next_frame += std::chrono::microseconds(16667);
        auto now = std::chrono::steady_clock::now();
        if (next_frame < now) next_frame = now;
        std::unique_lock<std::mutex> guard(command_mutex);
        command_ready.wait_until(guard, next_frame, [] { return !commands.empty(); });
    }
    if (audio) {
        AAudioStream_requestStop(audio);
        AAudioStream_close(audio);
    }
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
                              jstring font_path, jstring bios_dir, jboolean font_bitmap,
                                                      jint mhz_times_ten,
                                                      jint gdc_mhz_times_ten,
                                                      jboolean floppy, jstring boot_floppy_path) {
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
    {
        std::lock_guard<std::mutex> guard(command_mutex);
        commands.clear();
        frame_count = 0;
        audio_buffers = audible_buffers = 0;
        audio_state = "off";
        last_cs = last_ip = 0;
        machine_error.clear();
        machine_state = "Starting";
        active = true;
    }
    worker = std::thread(run_machine, std::move(path), std::move(font), std::move(bios),
                         font_bitmap == JNI_TRUE, mhz_times_ten, gdc_mhz_times_ten,
                         floppy == JNI_TRUE, std::move(boot_floppy));
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
    char text[240];
    std::lock_guard<std::mutex> guard(command_mutex);
    std::snprintf(text, sizeof(text), "%s%s%s | frames %llu | CS:IP %04x:%04x | audio %s %llu/%llu",
                  machine_state.c_str(), machine_error.empty() ? "" : ": ",
                  machine_error.c_str(), frame_count, last_cs, last_ip,
                  audio_state.c_str(), audible_buffers, audio_buffers);
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
    std::lock_guard<std::mutex> guard(window_mutex);
    if (replacement) {
        ANativeWindow_setBuffersGeometry(replacement, width > 0 ? width : 640,
                                         height > 0 ? height : 400, WINDOW_FORMAT_RGB_565);
    }
    if (window) ANativeWindow_release(window);
    window = replacement;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo98_MainActivity_nativeSetMuted(JNIEnv *, jobject, jboolean muted) {
    audio_muted.store(muted == JNI_TRUE);
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
