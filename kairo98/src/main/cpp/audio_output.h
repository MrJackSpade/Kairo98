#pragma once

#include <aaudio/AAudio.h>
#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include "audio_ring.h"

// The guest's 44.1 kHz stereo sound, played through an AAudio data callback.
// The emulation thread queues samples with write(); the device pulls them on its
// own clock. The queue holds queue_target() frames: enough to cover the lateness
// the frame grid recovers from, so a late frame never underruns the device.
class AudioOutput {
public:
    // A started low-latency stream, or null when the device cannot open one.
    static std::unique_ptr<AudioOutput> open(uint32_t queue_target);
    ~AudioOutput();
    AudioOutput(const AudioOutput &) = delete;
    AudioOutput &operator=(const AudioOutput &) = delete;

    // Queues frames; returns how many fit. The rest are dropped, which only
    // happens while frames run back to back, as in fast-forward.
    uint32_t write(const int16_t *frames, uint32_t count);
    void pause();
    void resume();

    // The frame-length multiplier that keeps the queue at its target.
    double pace_scale() const { return audio_pace_scale(ring_.size(), queue_target_); }
    // The error AAudio stopped the stream with, such as AAUDIO_ERROR_DISCONNECTED
    // when the output route changes, or AAUDIO_OK while it plays. A stopped
    // stream never restarts, so the owner opens a new one.
    aaudio_result_t lost_error() const { return lost_error_.load(std::memory_order_acquire); }

    uint32_t queued() const { return ring_.size(); }
    // Times the queue ran dry under the device, plus the device's own underruns.
    int32_t underruns() const;
    // "buf <device buffer>/<capacity>/<burst> q<target>" for Diagnostics.
    std::string config() const;

private:
    explicit AudioOutput(uint32_t queue_target);
    static aaudio_data_callback_result_t on_data(AAudioStream *, void *self, void *audio, int32_t frames);
    static void on_error(AAudioStream *, void *self, aaudio_result_t error);

    AAudioStream *stream_ = nullptr;
    const uint32_t queue_target_;
    AudioRing ring_;
    // The callback plays silence until the queue first reaches its target, and
    // again after it runs dry, so playback resumes with the full cushion rather
    // than crackling frame by frame at the edge of empty.
    std::atomic<bool> primed_{false};
    std::atomic<int32_t> queue_underruns_{0};
    std::atomic<aaudio_result_t> lost_error_{AAUDIO_OK};
};
