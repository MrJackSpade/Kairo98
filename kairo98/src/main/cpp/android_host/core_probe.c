#include "compiler.h"
#include "pccore.h"
#include "cpucore.h"
#include "fdd/sxsi.h"

int kairo98_core_probe(UINT16 *code_segment, UINT16 *instruction_pointer) {
    pccore_init();
    pccore_reset();
    *code_segment = CPU_CS;
    *instruction_pointer = CPU_IP;
    pccore_term();
    return (*code_segment == 0xf000 && *instruction_pointer == 0xfff0) ? 0 : 1;
}

int kairo98_hdi_probe(const char *path, UINT32 *cylinders, UINT32 *surfaces,
                     UINT32 *sectors, UINT32 *sector_size, UINT32 *first_word) {
    UINT8 boot_sector[2048];
    SXSIDEV drive;
    int result = 1;

    pccore_init();
    if (sxsi_setdevtype(0, SXSIDEV_HDD) != SUCCESS ||
        sxsi_devopen(0, path) != SUCCESS) {
        goto done;
    }
    drive = sxsi_getptr(0);
    if (drive == NULL || drive->size > sizeof(boot_sector) ||
        sxsi_read(0, 0, boot_sector, drive->size) != 0) {
        result = 2;
        goto done;
    }
    *cylinders = drive->cylinders;
    *surfaces = drive->surfaces;
    *sectors = drive->sectors;
    *sector_size = drive->size;
    *first_word = (UINT32)boot_sector[0] | ((UINT32)boot_sector[1] << 8);
    result = 0;
done:
    pccore_term();
    return result;
}
