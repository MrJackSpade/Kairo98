#include "compiler.h"
#include "mousemng.h"

/* Host mouse state. Only the emulation worker writes or reads this data. */
static int pending_x;
static int pending_y;
static UINT8 buttons = 0xa0;

void mousemng_reset(void) {
    pending_x = pending_y = 0;
    buttons = 0xa0;
}

void kairo98_mouse_move(int dx, int dy) {
    pending_x = max(-32768, min(32767, pending_x + dx));
    pending_y = max(-32768, min(32767, pending_y + dy));
}

void kairo98_mouse_button(int button, int down) {
    UINT8 bit = button == 1 ? 0x80 : 0x20;
    if (down) buttons &= ~bit;
    else buttons |= bit;
}

UINT8 mousemng_getstat(SINT16 *x, SINT16 *y, int clear) {
    *x = (SINT16)pending_x;
    *y = (SINT16)pending_y;
    if (clear) pending_x = pending_y = 0;
    return buttons;
}
