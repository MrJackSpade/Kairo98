#include "compiler.h"
#include "pccore.h"
#include "cpucore.h"
#include "keystat.h"
#include "fdd/sxsi.h"
#include "dosio.h"
#include <string.h>

int kairo98_font_overlay_load(const char *path);

int kairo98_machine_start(const char *image, int mhz_times_ten) {
    size_t length = image ? strlen(image) : 0;
    if (length >= sizeof(np2cfg.sasihdd[0])) {
        return 1;
    }
    if (mhz_times_ten != 20 && mhz_times_ten != 25) return 3;
    np2cfg.baseclock = mhz_times_ten == 25 ? PCBASECLOCK25 : PCBASECLOCK20;
    np2cfg.sasihdd[0][0] = '\0';
    if (length) {
        memcpy(np2cfg.sasihdd[0], image, length + 1);
        file_setcd(image);
    }
    pccore_init();
    if (kairo98_font_overlay_load(file_getcd("android-font.bin")) != 0) {
        pccore_term();
        np2cfg.sasihdd[0][0] = '\0';
        return 4;
    }
    pccore_reset();
    if (length) {
        SXSIDEV drive = sxsi_getptr(0);
        if (!drive || !(drive->flag & SXSIFLAG_READY)) {
            pccore_term();
            np2cfg.sasihdd[0][0] = '\0';
            return 2;
        }
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
    return result;
}

void kairo98_machine_location(unsigned short *cs, unsigned short *ip) {
    *cs = CPU_CS;
    *ip = CPU_IP;
}
