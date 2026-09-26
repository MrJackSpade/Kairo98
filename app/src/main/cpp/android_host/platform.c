#include "compiler.h"
#include "np2.h"
#include "commng.h"
#include "soundmng.h"
#include "sysmng.h"
#include "mousemng.h"
#include "scrnmng.h"
#include "ymfm_bridge.h"
#include "statsave.h"
#include "sound/sound.h"

NP2OSCFG np2oscfg = {0};

static UINT disconnected_read(COMMNG self, UINT8 *data) {
    (void)self;
    (void)data;
    return 0;
}
static UINT disconnected_write(COMMNG self, UINT8 data) {
    (void)self;
    (void)data;
    return 0;
}
static UINT disconnected_retry(COMMNG self) {
    (void)self;
    return 0;
}
static void disconnected_block(COMMNG self) {
    (void)self;
}
static UINT8 disconnected_status(COMMNG self) {
    (void)self;
    return 0xf0;
}
static INTPTR disconnected_message(COMMNG self, UINT message, INTPTR value) {
    (void)self;
    (void)message;
    (void)value;
    return 0;
}
static void disconnected_release(COMMNG self) {
    (void)self;
}
static _COMMNG disconnected = {
    COMCONNECT_OFF,
    disconnected_read,
    disconnected_write,
    disconnected_retry,
    disconnected_block,
    disconnected_block,
    disconnected_retry,
    disconnected_status,
    disconnected_message,
    disconnected_release
};

void commng_initialize(void) {}
void commng_finalize(void) {}
COMMNG commng_create(UINT device, BOOL onReset) {
    (void)device;
    (void)onReset;
    return &disconnected;
}
void commng_destroy(COMMNG handle) {
    (void)handle;
}

void sysmng_initialize(void) {}
void sysmng_deinitialize(void) {}
void sysmng_update(UINT flags) { (void)flags; }
void sysmng_cpureset(void) {}
void sysmng_requestupdatecaption(UINT8 flags) { (void)flags; }

static UINT16 frame_pixels[640 * 400];
static SCRNSURF frame = {
    (UINT8 *)frame_pixels,
    2,
    640 * 2,
    640,
    400,
    16,
    0
};

const UINT16 *kairo98_frame_pixels(void) { return frame_pixels; }

void scrnmng_setwidth(int x, int width) {
    (void)x;
    frame.width = width > 0 && width <= 640 ? width : 640;
}
void scrnmng_setheight(int y, int height) {
    (void)y;
    frame.height = height > 0 && height <= 400 ? height : 400;
}
const SCRNSURF *scrnmng_surflock(void) { return &frame; }
void scrnmng_surfunlock(const SCRNSURF *surface) { (void)surface; }
RGB16 scrnmng_makepal16(RGB32 color) {
    return (RGB16)(((color.p.r & 0xf8) << 8) |
                   ((color.p.g & 0xfc) << 3) |
                   (color.p.b >> 3));
}

static UINT audio_buffer_frames;

UINT soundmng_create(UINT rate, UINT ms) {
    (void)rate;
    (void)ms;
    audio_buffer_frames = 512;
    return audio_buffer_frames;
}
void soundmng_destroy(void) { audio_buffer_frames = 0; }
void soundmng_play(void) {}
void soundmng_stop(void) {}

UINT kairo98_audio_buffer_frames(void) { return audio_buffer_frames; }

int kairo98_fill_audio(SINT16 *destination, UINT frames) {
    const SINT32 *source;
    UINT i;
    if (frames != audio_buffer_frames || frames == 0) return 0;
    /* The FM synthesizer adds into the stream buffer from its own thread;
     * wait for everything issued so far before reading the buffer. */
    kairo_ymfm_drain_all();
    source = sound_pcmlock();
    if (!source) {
        ZeroMemory(destination, frames * 2 * sizeof(*destination));
        return 0;
    }
    for (i = 0; i < frames * 2; ++i) {
        SINT32 sample = source[i];
        if (sample > 32767) sample = 32767;
        if (sample < -32768) sample = -32768;
        destination[i] = (SINT16)sample;
    }
    sound_pcmunlock(source);
    return 1;
}

