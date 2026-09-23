#include "input_telemetry.h"

static uint64_t keyboard_waits;
static uint64_t keyboard_polls;
static uint64_t mouse_port_reads;
static int keyboard_waiting;

void kairo98_observe_keyboard_wait(void) {
    __atomic_add_fetch(&keyboard_waits, 1, __ATOMIC_RELAXED);
    __atomic_store_n(&keyboard_waiting, 1, __ATOMIC_RELEASE);
}

void kairo98_observe_keyboard_read(void) {
    __atomic_store_n(&keyboard_waiting, 0, __ATOMIC_RELEASE);
}

void kairo98_observe_keyboard_poll(void) {
    __atomic_add_fetch(&keyboard_polls, 1, __ATOMIC_RELAXED);
}

void kairo98_observe_mouse_port_read(void) {
    __atomic_add_fetch(&mouse_port_reads, 1, __ATOMIC_RELAXED);
}

void kairo98_input_telemetry_reset(void) {
    __atomic_store_n(&keyboard_waits, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&keyboard_polls, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&mouse_port_reads, 0, __ATOMIC_RELEASE);
    __atomic_store_n(&keyboard_waiting, 0, __ATOMIC_RELEASE);
}

void kairo98_input_telemetry_snapshot(uint64_t *waits, uint64_t *polls,
                                      uint64_t *mouse_reads, int *waiting) {
    *waits = __atomic_load_n(&keyboard_waits, __ATOMIC_ACQUIRE);
    *polls = __atomic_load_n(&keyboard_polls, __ATOMIC_ACQUIRE);
    *mouse_reads = __atomic_load_n(&mouse_port_reads, __ATOMIC_ACQUIRE);
    *waiting = __atomic_load_n(&keyboard_waiting, __ATOMIC_ACQUIRE);
}
