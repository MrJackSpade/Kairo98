#include "compiler.h"
#include "mousemng.h"
#include "pccore.h"
#include "iocore.h"

/*
 * Host mouse state. Only the emulation worker writes or reads this data, except
 * warp_completed, which the UI thread polls.
 *
 * The bus mouse reports relative counts and the guest keeps its own cursor, so a
 * warp first pushes the cursor a full screen past the corner nearest the previous
 * warp, where guest software clamps it, and then moves it from that corner to the
 * target in counts. When the cursor is still near the last tap it only travels to
 * the nearest corner; a push the full screen size lands in that corner wherever the
 * cursor really was. The latch saturates at one signed
 * byte and drops the rest, so delivery is paced by what the guest has not latched yet.
 */
#define HOME_X 768
#define HOME_Y 480
#define STALL_SYNCS 30

static int pending_x;
static int pending_y;
static int home_x;
static int home_y;
static int homing_x;
static int homing_y;
static int stalled_syncs;
/* The previous warp target; the first warp homes to the top-left corner. */
static int last_x;
static int last_y;
static unsigned warp_generation;
static unsigned warp_completed;
static UINT8 buttons = 0xa0;
MOUSEMNGSTAT mousemngstat;

void mousemng_reset(void) {
    pending_x = pending_y = 0;
    home_x = home_y = 0;
    homing_x = homing_y = 0;
    stalled_syncs = 0;
    last_x = last_y = 0;
    buttons = 0xa0;
    __atomic_store_n(&warp_completed, warp_generation, __ATOMIC_RELEASE);
}

void mousemng_updateautohidecursor(void) {
}

void kairo98_mouse_move(int dx, int dy) {
    pending_x = max(-32768, min(32767, pending_x + dx));
    pending_y = max(-32768, min(32767, pending_y + dy));
}

void kairo98_mouse_warp(int x, int y, unsigned generation) {
    int right = last_x >= 320;
    int bottom = last_y >= 200;
    x = max(0, min(639, x));
    y = max(0, min(399, y));
    home_x = right ? HOME_X : -HOME_X;
    home_y = bottom ? HOME_Y : -HOME_Y;
    homing_x = homing_y = 1;
    pending_x = right ? x - 639 : x;
    pending_y = bottom ? y - 399 : y;
    last_x = x;
    last_y = y;
    stalled_syncs = 0;
    warp_generation = generation;
}

unsigned kairo98_mouse_warp_completed(void) {
    return __atomic_load_n(&warp_completed, __ATOMIC_ACQUIRE);
}

void kairo98_mouse_button(int button, int down) {
    UINT8 bit = button == 1 ? 0x80 : 0x20;
    if (down) buttons &= ~bit;
    else buttons |= bit;
}

/* Takes the next step for one axis. held is the count the guest has not latched. */
static SINT16 next_step(int *home, int *homing, int *pending, int held, int limit, int bypass) {
    int room = bypass ? 32767 : limit - (held < 0 ? -held : held);
    int *source;
    int step;
    if (*home) source = home;
    else if (*homing && held && !bypass) return 0;  /* let the edge clamp settle first */
    else {
        *homing = 0;
        source = pending;
    }
    if (room <= 0 || !*source) return 0;
    step = max(-room, min(room, *source));
    *source -= step;
    return (SINT16)step;
}

UINT8 mousemng_getstat(SINT16 *x, SINT16 *y, int clear) {
    if (!clear) {
        *x = (SINT16)max(-32768, min(32767, home_x + pending_x));
        *y = (SINT16)max(-32768, min(32767, home_y + pending_y));
        return buttons;
    }
    int limit = np2cfg.slowmous ? 15 : 127;
    int bypass = stalled_syncs >= STALL_SYNCS;
    *x = next_step(&home_x, &homing_x, &pending_x, mouseif.x, limit, bypass);
    *y = next_step(&home_y, &homing_y, &pending_y, mouseif.y, limit, bypass);
    int work = home_x || home_y || pending_x || pending_y || homing_x || homing_y;
    int held = mouseif.x || mouseif.y;
    /* A guest that stops latching must not hold a warp open forever. */
    if (*x || *y || (!work && !held)) stalled_syncs = 0;
    else if (stalled_syncs < STALL_SYNCS) stalled_syncs++;
    if (!work && !*x && !*y && (!held || stalled_syncs >= STALL_SYNCS))
        __atomic_store_n(&warp_completed, warp_generation, __ATOMIC_RELEASE);
    return buttons;
}
