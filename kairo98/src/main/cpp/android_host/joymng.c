#include "compiler.h"
#include "joymng.h"

/* PC-98 joystick 1. The emulator worker owns both writes and reads. */
static UINT8 joy_state = 0xff;
static const UINT8 joy_bits[6] = {0x01, 0x02, 0x04, 0x08, 0x40, 0x80};

void kairo98_joy_release_all(void) {
    joy_state = 0xff;
}

void kairo98_joy_set(int control, int down) {
    if (control < 0 || control >= 6) return;
    if (down) joy_state &= (UINT8)~joy_bits[control];
    else joy_state |= joy_bits[control];
}

UINT8 joymng_getstat(void) {
    return joy_state;
}
