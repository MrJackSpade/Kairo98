// The PC-98 on Neko Project 21/W as the shared machine host's core (kairo/machine_core.h,
// Kairo98#108). np2 keeps its state in globals, so one machine runs at a time, as the host runs
// one session at a time. Its picture is composed on the GPU (pc98_picture.h).

#include "kairo/machine_core.h"
#include "pc98_picture.h"

#include <android/log.h>
#include <algorithm>
#include <cstdio>
#include <memory>
#include <string>
#include <vector>

extern "C" {
int kairo98_machine_start(const char *image, const char *font_path, const char *bios_dir, int mhz_times_ten,
                          int gdc_mhz_times_ten, int cpu_multiple, int floppy, const char *boot_floppy,
                          const char *second_floppy, const char *cd_image);
int kairo98_machine_dos_prompt(void);
void kairo98_machine_skip_memory_check(int skip);
void kairo98_machine_own_bios(int own);
unsigned int kairo98_machine_exec(void);
void kairo98_machine_clock_rates(unsigned int *realclock, unsigned int *hzbase, unsigned int *clockbase);
int kairo98_machine_reset(void);
void kairo98_machine_key(unsigned char code, int down);
void kairo98_mouse_move(int dx, int dy);
void kairo98_mouse_button(int button, int down);
void kairo98_mouse_warp(int x, int y, unsigned generation);
unsigned kairo98_mouse_warp_completed(void);
int kairo98_machine_save_state(const char *dir);
int kairo98_machine_load_state(const char *dir);
int kairo98_machine_locks(void);
void kairo98_joy_set(int control, int down);
void kairo98_joy_release_all(void);
void kairo98_input_telemetry_reset(void);
void kairo98_input_telemetry_snapshot(uint64_t *waits, uint64_t *polls, uint64_t *mouse_reads, int *waiting);
void kairo98_machine_release_keys(void);
int kairo98_machine_set_disk(const char *image);
int kairo98_machine_set_floppy(int drive, const char *image);
int kairo98_machine_set_cd(const char *image);
const char *kairo98_machine_media_path(int drive);
int kairo98_machine_stop(void);
void kairo98_machine_location(unsigned short *cs, unsigned short *ip);
unsigned long long kairo98_machine_screen_signature(void);
unsigned int kairo98_audio_buffer_frames(void);
int kairo98_fill_audio(short *destination, unsigned int frames);
#if defined(KAIRO98_PGO_GENERATE)
void __llvm_profile_set_filename(const char *name);
int __llvm_profile_write_file(void);
#endif
}

namespace {

// The launch's media slots (MainActivity.mediaSlots): floppy drives A and B, the CD-ROM drive and
// the hard disk.
enum Slot { kFloppyA, kFloppyB, kCd, kHardDisk, kSlots };

// The launch settings MainActivity.startGame gives.
constexpr const char *kFont = "font";                   // FONT.BMP's path
constexpr const char *kBaseClock = "baseClock";         // 20 or 25: the base clock in tenths of a MHz
constexpr const char *kGdcClock = "gdcClock";           // 25 or 50: the GDC clock in tenths of a MHz
constexpr const char *kCpuMultiple = "cpuMultiple";     // 1 to 20
constexpr const char *kSkipMemoryCheck = "skipMemoryCheck";  // on or off
constexpr const char *kOwnBios = "ownBios";             // on: the player's bios.rom in the firmware folder

// np2 mixes sound in buffers of this many stereo frames, at kAudioRate.
constexpr unsigned int kAudioBuffer = 512;
constexpr int32_t kAudioRate = 44100;
// How many frames the DOS prompt must stand before startup commands are typed at it.
constexpr int kPromptFrames = 15;

class Pc98Core : public kairo::MachineCore {
public:
    // Starts np2 with the launch's media; false with error when it cannot.
    bool start(const kairo::Launch &launch, std::string &error) {
        if (launch.media.size() != kSlots) {
            error = "A PC-98 launch names floppies A and B, a CD and a hard disk; this one has " +
                    std::to_string(launch.media.size()) + " media";
            return false;
        }
        const auto setting = [&launch, &error](const char *name, std::string &value) {
            const auto found = launch.settings.find(name);
            if (found == launch.settings.end()) {
                error = std::string("The PC-98 launch has no ") + name + " setting";
                return false;
            }
            value = found->second;
            return true;
        };
        std::string font, base_clock, gdc_clock, cpu_multiple, skip_memory_check, own_bios;
        if (!setting(kFont, font) || !setting(kBaseClock, base_clock) || !setting(kGdcClock, gdc_clock) ||
            !setting(kCpuMultiple, cpu_multiple) || !setting(kSkipMemoryCheck, skip_memory_check) ||
            !setting(kOwnBios, own_bios))
            return false;
        kairo98_input_telemetry_reset();
        kairo98_joy_release_all();
        kairo98_machine_skip_memory_check(skip_memory_check == "on" ? 1 : 0);
        kairo98_machine_own_bios(own_bios == "on" ? 1 : 0);
        // The game boots from its hard disk, with floppy A beside it, or from floppy A.
        const std::string &hard_disk = launch.media[kHardDisk];
        const bool floppy = hard_disk.empty();
        const std::string &image = floppy ? launch.media[kFloppyA] : hard_disk;
        const std::string boot_floppy = floppy ? "" : launch.media[kFloppyA];
        const int result = kairo98_machine_start(image.c_str(), font.c_str(), launch.firmware.c_str(),
                                                 std::stoi(base_clock), std::stoi(gdc_clock), std::stoi(cpu_multiple),
                                                 floppy ? 1 : 0, boot_floppy.c_str(), launch.media[kFloppyB].c_str(),
                                                 launch.media[kCd].c_str());
        if (result != 0) {
            error = result == 2 ? "The hard disk did not mount" : result == 3 ? "Invalid clock setting"
                  : result == 5 ? "Floppy A did not mount" : result == 6 ? "Floppy B did not mount"
                  : result == 7 ? "The CD did not mount" : "A disk image path is too long";
            return false;
        }
        started_ = true;
        // np2 sizes its sound buffers as the machine starts.
        if (kairo98_audio_buffer_frames() != kAudioBuffer) {
            error = "np2 mixes sound in buffers of " + std::to_string(kairo98_audio_buffer_frames()) + " frames, not " +
                    std::to_string(kAudioBuffer);
            return false;
        }
#if defined(KAIRO98_PGO_GENERATE)
        const std::string profile = launch.firmware + "/kairo98-%m.profraw";
        __llvm_profile_set_filename(profile.c_str());
        __android_log_print(ANDROID_LOG_INFO, "Kairo98Perf", "PGO profile: %s", profile.c_str());
#endif
        return true;
    }

    ~Pc98Core() override {
        if (started_) close();
    }

    std::string close() override {
        if (!started_) return "";
        started_ = false;
        return kairo98_machine_stop() == 0 ? "" : "The hard disk could not be written back as the machine stopped";
    }

    bool run_frame() override {
        if (!error_.empty()) return false;
        const unsigned int clocks = kairo98_machine_exec();
        unsigned int realclock = 0, hzbase = 0, clockbase = 0;
        kairo98_machine_clock_rates(&realclock, &hzbase, &clockbase);
        if (clockbase == 0) {
            error_ = "The sound stream has no clock rate";
            return false;
        }
        // The frame lasts as long as the sound it made takes to play: a guest frame, 56.4 Hz in the
        // 400-line mode. The length comes from the core's samples-per-clock ratio rather than its clock
        // rate because that ratio is rounded (at the default clock it makes 0.23% more than 44100
        // samples per guest second), and timing by clocks would hold the audio queue above its target.
        frame_seconds_ = static_cast<double>(clocks) * hzbase / clockbase / kAudioRate;
        // Exactly what the frame produced, in np2's buffers: guest clocks times hzbase not yet pulled
        // as sound, one buffer being kAudioBuffer * clockbase of it.
        audio_due_ += static_cast<uint64_t>(clocks) * hzbase;
        const uint64_t buffer_due = static_cast<uint64_t>(kAudioBuffer) * clockbase;
        while (audio_due_ >= buffer_due) {
            audio_due_ -= buffer_due;
            const size_t end = audio_.size();
            audio_.resize(end + kAudioBuffer * 2);
            kairo98_fill_audio(audio_.data() + end, kAudioBuffer);
        }
        prompt_frames_ = kairo98_machine_dos_prompt() ? prompt_frames_ + 1 : 0;
#if defined(KAIRO98_PGO_GENERATE)
        // The profiling harness never stops the machine, so the counters are written every 600
        // frames; %m merges them into one file.
        if (++profile_frames_ % 600 == 0) __llvm_profile_write_file();
#endif
        return true;
    }

    std::string error() override { return error_; }
    const uint8_t *frame(uint32_t &, uint32_t &) override { return nullptr; }
    kairo::GpuPicture *gpu_picture() override { return &picture_; }
    double frame_seconds() override { return frame_seconds_; }
    int32_t audio_rate() override { return kAudioRate; }

    uint32_t read_audio(int16_t *stereo, uint32_t max_frames) override {
        const size_t frames = std::min<size_t>(max_frames, (audio_.size() - audio_read_) / 2);
        std::copy_n(audio_.data() + audio_read_, frames * 2, stereo);
        audio_read_ += frames * 2;
        if (audio_read_ == audio_.size()) {
            audio_.clear();
            audio_read_ = 0;
        }
        return static_cast<uint32_t>(frames);
    }

    void release_inputs() override {
        kairo98_machine_release_keys();
        kairo98_joy_release_all();
    }
    void key(int code, bool down) override {
        if (code >= 0 && code < 128) kairo98_machine_key(static_cast<unsigned char>(code), down ? 1 : 0);
    }
    // In JoystickInputRouter's order: up, down, left, right, buttons 1 and 2.
    void joystick(int control, bool down) override {
        if (control >= 0 && control < 6) kairo98_joy_set(control, down ? 1 : 0);
    }
    void mouse_move(int dx, int dy) override { kairo98_mouse_move(dx, dy); }
    void mouse_button(int button, bool down) override { kairo98_mouse_button(button, down ? 1 : 0); }
    void mouse_warp(int x, int y) override { kairo98_mouse_warp(x, y, ++warps_); }
    bool mouse_warp_done() override { return kairo98_mouse_warp_completed() == warps_; }
    // Caps (bit 0) and Kana (bit 1).
    int locks() override { return kairo98_machine_locks(); }

    bool guest_input(GuestInput &input) override {
        int waiting = 0;
        kairo98_input_telemetry_snapshot(&input.keyboard_waits, &input.keyboard_polls, &input.mouse_reads, &waiting);
        input.keyboard_waiting = waiting != 0;
        // np2 sees the guest's mouse reads themselves, so it never reports the mouse as set up instead.
        input.mouse_enabled = false;
        return true;
    }

    // Where the guest's CPU is, and whether it stands at the DOS prompt.
    std::string details() override {
        unsigned short cs = 0, ip = 0;
        kairo98_machine_location(&cs, &ip);
        char text[48];
        std::snprintf(text, sizeof(text), "csip=%04x:%04x;prompt=%d", cs, ip, prompt_frames_ >= kPromptFrames ? 1 : 0);
        return text;
    }

    void reset() override {
        restarted();
        if (kairo98_machine_reset() != 0) error_ = "The hard disk could not be written back before the reset";
    }

    int save_state(const std::string &directory) override {
        const int result = kairo98_machine_save_state(directory.c_str());
        state_error_ = state_reason(result);
        return result == 0 ? 0 : kFailed;
    }

    // A load that fails before it changes the machine leaves the game running; one that fails while
    // putting the state's disks or machine back has lost the game.
    int load_state(const std::string &directory) override {
        restarted();
        const int result = kairo98_machine_load_state(directory.c_str());
        state_error_ = state_reason(result);
        return result == 0 ? 0 : result == 4 || result == 5 ? kLost : kFailed;
    }

    std::string state_error() override { return state_error_; }

    int media(int slot, const std::string &path) override {
        switch (slot) {
        case kFloppyA:
        case kFloppyB:
            return kairo98_machine_set_floppy(slot, path.c_str()) == 0 ? 0 : kFailed;
        case kCd:
            return kairo98_machine_set_cd(path.c_str()) == 0 ? 0 : kFailed;
        case kHardDisk: {
            // The machine starts again from the new disk.
            restarted();
            const int result = kairo98_machine_set_disk(path.c_str());
            if (result == 1) return kFailed;
            if (result == 3) error_ = "The hard disk could not be written back before the disk change";
            else if (result != 0) error_ = "The hard disk did not mount after the disk change";
            return result == 0 ? 0 : kFailed;
        }
        default:
            return kUnsupported;
        }
    }

    std::vector<std::string> media_paths() override {
        std::vector<std::string> paths;
        for (int slot = 0; slot < kSlots; ++slot) paths.emplace_back(kairo98_machine_media_path(slot));
        return paths;
    }

    kairo::Prompt prompt() override { return prompt_frames_ >= kPromptFrames ? kairo::Prompt::Line : kairo::Prompt::None; }

    bool typed_key(char c, int &code, bool &shift) override {
        // Letters in A-Z order at their PC-98 scan codes; digits 1-9 are 1-9 and 0 is 0x0a.
        static const unsigned char letters[26] = {0x1d, 0x2d, 0x2b, 0x1f, 0x12, 0x20, 0x21, 0x22, 0x17, 0x23, 0x24,
                                                  0x25, 0x2f, 0x2e, 0x18, 0x19, 0x10, 0x13, 0x1e, 0x14, 0x16, 0x2c,
                                                  0x11, 0x2a, 0x15, 0x29};
        shift = false;
        if (c >= 'a' && c <= 'z') code = letters[c - 'a'];
        else if (c >= 'A' && c <= 'Z') {
            code = letters[c - 'A'];
            shift = true;
        } else if (c >= '1' && c <= '9') code = c - '1' + 1;
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

    int special_key(kairo::SpecialKey key) override {
        switch (key) {
        case kairo::SpecialKey::Return: return 0x1c;
        case kairo::SpecialKey::Shift: return 0x70;
        case kairo::SpecialKey::Ctrl: return 0x74;
        default: return 0x62 + static_cast<int>(key) - static_cast<int>(kairo::SpecialKey::F1);
        }
    }

    uint64_t screen_signature() override { return kairo98_machine_screen_signature(); }

private:
    // The guest starts again (a reset, a load, a new hard disk): its input counts and the prompt with it.
    void restarted() {
        kairo98_input_telemetry_reset();
        kairo98_joy_release_all();
        prompt_frames_ = 0;
    }

    static std::string state_reason(int result) {
        switch (result) {
        case 0: return "";
        case 2: return "The state is missing, or from another version of the machine";
        case 3: return "The state's list of disks could not be read or written";
        case 4: return "The state's disk images could not be copied";
        case 5: return "The machine could not read or write the state";
        case 7: return "The state's folder path is too long";
        default: return "np2 failed with code " + std::to_string(result);
        }
    }

    bool started_ = false;
    std::string error_;
    std::string state_error_;
    Pc98Picture picture_;
    double frame_seconds_ = 1 / 56.42;
    uint64_t audio_due_ = 0;
    std::vector<int16_t> audio_;
    size_t audio_read_ = 0;
    int prompt_frames_ = 0;
    unsigned warps_ = 0;
#if defined(KAIRO98_PGO_GENERATE)
    unsigned profile_frames_ = 0;
#endif
};

} // namespace

std::unique_ptr<kairo::MachineCore> kairo::create_core(const Launch &launch, std::string &error) {
    auto core = std::make_unique<Pc98Core>();
    if (!core->start(launch, error)) return nullptr;
    return core;
}
