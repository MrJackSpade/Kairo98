#include "compiler.h"
#include "pccore.h"
#include "cpucore.h"
#include "keystat.h"
#include "fdd/sxsi.h"
#include "fdd/diskdrv.h"
#include "diskimage/fddfile.h"
#include "diskimage/img_common.h"
#include "dosio.h"
#include "iocore.h"
#include <ctype.h>
#include <string.h>

int kairo98_font_overlay_load(const char *path);

static UINT floppy_type(const char *image) {
    size_t length = image ? strlen(image) : 0;
    return length >= 4 && image[length - 4] == '.' &&
           tolower((unsigned char)image[length - 3]) == 'f' &&
           tolower((unsigned char)image[length - 2]) == 'd' &&
           tolower((unsigned char)image[length - 1]) == 'd'
        ? FTYPE_VFDD : FTYPE_NONE;
}

int kairo98_machine_start(const char *image, const char *font_path, const char *bios_dir,
                          int mhz_times_ten, int floppy) {
    size_t length = image ? strlen(image) : 0;
    size_t bios_length = bios_dir ? strlen(bios_dir) : 0;
    if (length >= (floppy ? sizeof(np2cfg.fddfile[0]) :
                            sizeof(np2cfg.sasihdd[0])) ||
        bios_length >= sizeof(np2cfg.biospath)) {
        return 1;
    }
    if (mhz_times_ten != 20 && mhz_times_ten != 25) return 3;
    np2cfg.baseclock = mhz_times_ten == 25 ? PCBASECLOCK25 : PCBASECLOCK20;
    np2cfg.sasihdd[0][0] = '\0';
    np2cfg.fddfile[0][0] = '\0';
    np2cfg.biospath[0] = '\0';
    if (bios_length) memcpy(np2cfg.biospath, bios_dir, bios_length + 1);
    if (length) {
        if (!floppy) memcpy(np2cfg.sasihdd[0], image, length + 1);
        file_setcd(image);
    }
    pccore_init();
    if (!font_path || kairo98_font_overlay_load(font_path) != 0) {
        pccore_term();
        np2cfg.sasihdd[0][0] = '\0';
        return 4;
    }
    pccore_reset();
    if (length) {
        if (floppy) {
            diskdrv_readyfddex(0, image, floppy_type(image), 0);
            if (!fdd_diskready(0)) {
                pccore_term();
                np2cfg.fddfile[0][0] = '\0';
                return 5;
            }
        } else {
            SXSIDEV drive = sxsi_getptr(0);
            if (drive && (drive->flag & SXSIFLAG_READY)) return 0;
            pccore_term();
            np2cfg.sasihdd[0][0] = '\0';
            return 2;
        }
    }
    return 0;
}

int kairo98_machine_set_floppy(int drive, const char *image) {
    size_t length = image ? strlen(image) : 0;
    if (drive < 0 || drive > 1 || length >= sizeof(np2cfg.fddfile[0])) return 1;
    char previous[sizeof(np2cfg.fddfile[0])];
    memcpy(previous, np2cfg.fddfile[drive], sizeof(previous));
    previous[sizeof(previous) - 1] = '\0';
    diskdrv_setfddex((REG8)drive, NULL, FTYPE_NONE, 0);
    if (length) {
        diskdrv_readyfddex((REG8)drive, image, floppy_type(image), 0);
        if (!fdd_diskready((REG8)drive)) {
            if (previous[0])
                diskdrv_readyfddex((REG8)drive, previous, floppy_type(previous), 0);
            return 2;
        }
    }
    return 0;
}

int kairo98_machine_dos_prompt(void) {
    unsigned int cursor = LOADINTELWORD(gdc.m.para + GDC_CSRW) & 0x0fff;
    unsigned int previous = (cursor - 1) & 0x0fff;
    if (mem[0xa0000 + previous * 2] != '>') return 0;
    for (unsigned int back = 2; back < 80; ++back) {
        unsigned int position = (cursor - back) & 0x0fff;
        if (mem[0xa0000 + position * 2] != ':') continue;
        unsigned int letter = (position - 1) & 0x0fff;
        unsigned int slash = (position + 1) & 0x0fff;
        unsigned char drive = mem[0xa0000 + letter * 2];
        unsigned char separator = mem[0xa0000 + slash * 2];
        if (((drive >= 'A' && drive <= 'Z') || (drive >= 'a' && drive <= 'z')) &&
            (separator == '\\' || separator == '/')) return 1;
    }
    return 0;
}
static int flush_disk(void) {
    SXSIDEV drive = sxsi_getptr(0);
    if (drive && (drive->flag & SXSIFLAG_FILEOPENED) && drive->hdl) {
        return file_sync((FILEH)drive->hdl);
    }
    return 0;
}

void kairo98_machine_exec(void) {
    pccore_exec(TRUE);
}

int kairo98_machine_reset(void) {
    keystat_allrelease();
    if (flush_disk() != 0) return 1;
    pccore_reset();
    return 0;
}

int kairo98_machine_set_clock(int mhz_times_ten) {
    if (mhz_times_ten != 20 && mhz_times_ten != 25) return 2;
    keystat_allrelease();
    if (flush_disk() != 0) return 1;
    np2cfg.baseclock = mhz_times_ten == 25 ? PCBASECLOCK25 : PCBASECLOCK20;
    pccore_reset();
    return 0;
}

void kairo98_machine_key(unsigned char code, int down) {
    keystat_senddata((REG8)(code | (down ? 0 : 0x80)));
}

void kairo98_machine_release_keys(void) {
    keystat_allrelease();
}

int kairo98_machine_set_disk(const char *image) {
    size_t length = image ? strlen(image) : 0;
    if (length >= sizeof(np2cfg.sasihdd[0])) return 1;
    if (flush_disk() != 0) return 3;
    sxsi_devclose(0);
    np2cfg.sasihdd[0][0] = '\0';
    if (length) memcpy(np2cfg.sasihdd[0], image, length + 1);
    pccore_reset();
    if (length) {
        SXSIDEV drive = sxsi_getptr(0);
        if (!drive || !(drive->flag & SXSIFLAG_READY)) return 2;
    }
    return 0;
}

int kairo98_machine_stop(void) {
    keystat_allrelease();
    int result = flush_disk();
    pccore_term();
    np2cfg.sasihdd[0][0] = '\0';
    np2cfg.fddfile[0][0] = '\0';
    np2cfg.fddfile[1][0] = '\0';
    return result;
}

void kairo98_machine_location(unsigned short *cs, unsigned short *ip) {
    *cs = CPU_CS;
    *ip = CPU_IP;
}
