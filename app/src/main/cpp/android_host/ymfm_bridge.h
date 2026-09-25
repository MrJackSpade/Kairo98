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

#ifdef __cplusplus
}
#endif
