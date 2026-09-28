#ifndef KAIRO98_INPUT_TELEMETRY_H
#define KAIRO98_INPUT_TELEMETRY_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Passive guest observations. No emulator behavior depends on these values. */
void kairo98_observe_keyboard_wait(void);
void kairo98_observe_keyboard_read(void);
void kairo98_observe_keyboard_poll(void);
void kairo98_observe_mouse_port_read(void);
void kairo98_input_telemetry_reset(void);
void kairo98_input_telemetry_snapshot(uint64_t *keyboard_waits,
                                      uint64_t *keyboard_polls,
                                      uint64_t *mouse_port_reads,
                                      int *keyboard_waiting);

#ifdef __cplusplus
}
#endif
#endif
