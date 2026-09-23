#include "compiler.h"
#include "np2.h"
#include "commng.h"
#include "soundmng.h"
#include "sysmng.h"
#include "mousemng.h"
#include "scrnmng.h"
#include "statsave.h"

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
void mousemng_reset(void) {}

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

UINT soundmng_create(UINT rate, UINT ms) {
    (void)rate;
    (void)ms;
    return 0;
}
void soundmng_destroy(void) {}
void soundmng_play(void) {}
void soundmng_stop(void) {}

int statflag_read(STFLAGH state, void *destination, UINT size) {
    (void)state;
    (void)destination;
    (void)size;
    return FAILURE;
}
int statflag_write(STFLAGH state, const void *source, UINT size) {
    (void)state;
    (void)source;
    (void)size;
    return FAILURE;
}
