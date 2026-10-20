#include "audio_output.h"

#include <cstdio>

namespace {
// Room for four times the target, so frames that run back to back after a
// stall refill the queue instead of dropping samples.
uint32_t ring_capacity(uint32_t queue_target) {
    uint32_t capacity = 1;
    while (capacity < 4 * queue_target) capacity <<= 1;
    return capacity;
}
} // namespace

AudioOutput::AudioOutput(uint32_t queue_target)
    : queue_target_(queue_target), ring_(ring_capacity(queue_target)) {}

std::unique_ptr<AudioOutput> AudioOutput::open(uint32_t queue_target) {
    std::unique_ptr<AudioOutput> output(new AudioOutput(queue_target));
    AAudioStreamBuilder *builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return nullptr;
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(builder, 44100);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setDataCallback(builder, on_data, output.get());
    AAudioStreamBuilder_setErrorCallback(builder, on_error, output.get());
    const aaudio_result_t opened = AAudioStreamBuilder_openStream(builder, &output->stream_);
    AAudioStreamBuilder_delete(builder);
    if (opened != AAUDIO_OK) return nullptr;
    if (AAudioStream_requestStart(output->stream_) != AAUDIO_OK) return nullptr;
    return output;
}

AudioOutput::~AudioOutput() {
    if (!stream_) return;
    AAudioStream_requestStop(stream_);
    AAudioStream_close(stream_);
}

uint32_t AudioOutput::write(const int16_t *frames, uint32_t count) {
    return ring_.write(frames, count);
}

void AudioOutput::pause() { AAudioStream_requestPause(stream_); }
void AudioOutput::resume() { AAudioStream_requestStart(stream_); }

int32_t AudioOutput::underruns() const {
    const int32_t device = AAudioStream_getXRunCount(stream_);
    return queue_underruns_.load(std::memory_order_relaxed) + (device > 0 ? device : 0);
}

std::string AudioOutput::config() const {
    char text[80];
    std::snprintf(text, sizeof(text), "buf %d/%d/%d q%u",
                  AAudioStream_getBufferSizeInFrames(stream_),
                  AAudioStream_getBufferCapacityInFrames(stream_),
                  AAudioStream_getFramesPerBurst(stream_), queue_target_);
    return text;
}

aaudio_data_callback_result_t AudioOutput::on_data(AAudioStream *, void *self, void *audio,
                                                   int32_t frames) {
    auto *output = static_cast<AudioOutput *>(self);
    auto *samples = static_cast<int16_t *>(audio);
    const uint32_t wanted = static_cast<uint32_t>(frames);
    if (!output->primed_.load(std::memory_order_relaxed)) {
        if (output->ring_.size() < output->queue_target_) {
            std::memset(samples, 0, wanted * 2 * sizeof(int16_t));
            return AAUDIO_CALLBACK_RESULT_CONTINUE;
        }
        output->primed_.store(true, std::memory_order_relaxed);
    }
    const uint32_t got = output->ring_.read(samples, wanted);
    if (got < wanted) {
        std::memset(samples + got * 2, 0, (wanted - got) * 2 * sizeof(int16_t));
        output->queue_underruns_.fetch_add(1, std::memory_order_relaxed);
        output->primed_.store(false, std::memory_order_relaxed);
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AudioOutput::on_error(AAudioStream *, void *self, aaudio_result_t error) {
    static_cast<AudioOutput *>(self)->lost_error_.store(error, std::memory_order_release);
}
