#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void *kairo_ymfm_create(void);
void kairo_ymfm_destroy(void *handle);
void kairo_ymfm_reset(void *handle, int ym2608, uint32_t output_rate,
                      uint8_t *adpcm_ram, uint32_t adpcm_ram_size,
                      const char *rhythm_rom_path);
int kairo_ymfm_has_rhythm_rom(void *handle);
void kairo_ymfm_write(void *handle, int bank, uint8_t address, uint8_t data);
uint8_t kairo_ymfm_read_status(void *handle, int bank);
void kairo_ymfm_set_volume(void *handle, uint32_t fm_volume, uint32_t ssg_volume);
void kairo_ymfm_mix(void *handle, int32_t *pcm, uint32_t frames);

/* The synthesizer runs on its own thread and adds its samples into the
 * stream buffer regions handed to kairo_ymfm_mix later. Callers must drain
 * before those regions move or go away: before the host reads the stream,
 * before the stream is reset, and before it is destroyed. */
void kairo_ymfm_drain_all(void);

/* The guest changed one byte of the ADPCM RAM that `adpcm_ram` names; the
 * synthesizer keeps its own copy in stream order. */
void kairo_ymfm_ram_changed(const uint8_t *adpcm_ram, uint32_t offset, uint8_t value);

/* Diagnostics for the debug status line. */
void kairo_ymfm_stats(unsigned long long *segments, unsigned long long *drain_waits,
                      unsigned long long *verify_mismatches);

#ifdef __cplusplus
}
#endif
