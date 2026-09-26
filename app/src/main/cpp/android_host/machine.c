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

static int configured_gdc_clock = 50;

static void apply_gdc_dipswitch(void) {
    /* DIP switch 2-8 is sampled by the BIOS into MEMB_PRXDUPD at boot. */
    if (configured_gdc_clock == 25) np2cfg.dipsw[1] |= 0x80;
    else np2cfg.dipsw[1] &= ~0x80;
}

static void apply_gdc_clock(void) {
    if (configured_gdc_clock == 25) gdc.clock &= ~0x80;
    else gdc.clock |= 0x80;
    gdc_updateclock();
}

int kairo98_machine_start(const char *image, const char *font_path, const char *bios_dir,
                          int font_bitmap, int mhz_times_ten, int gdc_mhz_times_ten, int floppy,
                          const char *boot_floppy, const char *second_floppy) {
    size_t length = image ? strlen(image) : 0;
    size_t boot_length = boot_floppy ? strlen(boot_floppy) : 0;
    size_t second_length = second_floppy ? strlen(second_floppy) : 0;
    size_t bios_length = bios_dir ? strlen(bios_dir) : 0;
    size_t font_length = font_path ? strlen(font_path) : 0;
    if (length >= (floppy ? sizeof(np2cfg.fddfile[0]) :
                            sizeof(np2cfg.sasihdd[0])) ||
        boot_length >= sizeof(np2cfg.fddfile[0]) || (floppy && boot_length) ||
        second_length >= sizeof(np2cfg.fddfile[1]) ||
        bios_length >= sizeof(np2cfg.biospath) || !font_length ||
        (font_bitmap && font_length >= sizeof(np2cfg.fontfile))) {
        return 1;
    }
    if ((mhz_times_ten != 20 && mhz_times_ten != 25) ||
        (gdc_mhz_times_ten != 25 && gdc_mhz_times_ten != 50)) return 3;
    configured_gdc_clock = gdc_mhz_times_ten;
    apply_gdc_dipswitch();
    np2cfg.baseclock = mhz_times_ten == 25 ? PCBASECLOCK25 : PCBASECLOCK20;
    np2cfg.sasihdd[0][0] = '\0';
    np2cfg.fddfile[0][0] = '\0';
    np2cfg.fddfile[1][0] = '\0';
    np2cfg.biospath[0] = '\0';
    np2cfg.fontfile[0] = '\0';
    if (bios_length) memcpy(np2cfg.biospath, bios_dir, bios_length + 1);
    if (font_bitmap) memcpy(np2cfg.fontfile, font_path, font_length + 1);
    if (length) {
        if (!floppy) memcpy(np2cfg.sasihdd[0], image, length + 1);
        file_setcd(image);
    }
    pccore_init();
    if (!font_bitmap && kairo98_font_overlay_load(font_path) != 0) {
        pccore_term();
        np2cfg.sasihdd[0][0] = '\0';
        return 4;
    }
    pccore_reset();
    apply_gdc_clock();
    if (length && !floppy) {
        SXSIDEV drive = sxsi_getptr(0);
        if (!drive || !(drive->flag & SXSIFLAG_READY)) {
            pccore_term();
            np2cfg.sasihdd[0][0] = '\0';
            return 2;
        }
    }
    const char *first_floppy = boot_length ? boot_floppy : (floppy ? image : NULL);
    if (first_floppy) {
        diskdrv_readyfddex(0, first_floppy, floppy_type(first_floppy), 0);
        if (!fdd_diskready(0)) {
            pccore_term();
            np2cfg.fddfile[0][0] = '\0';
            return 5;
        }
    }
    if (second_length) {
        diskdrv_readyfddex(1, second_floppy, floppy_type(second_floppy), 0);
        if (!fdd_diskready(1)) {
            pccore_term();
            np2cfg.fddfile[1][0] = '\0';
            return 6;
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

unsigned long long kairo98_counter_slices;
unsigned long long kairo98_counter_insts;
unsigned long long kairo98_counter_sti;
unsigned long long kairo98_counter_draws;
unsigned long long kairo98_time_cpu_ns;
unsigned long long kairo98_time_event_ns;
unsigned long long kairo98_time_draw_ns;
unsigned long long kairo98_time_fm_ns;

unsigned long long kairo98_machine_draw_count(void) {
    return kairo98_counter_draws;
}

extern unsigned long long kairo98_egc_writes;
extern unsigned long long kairo98_egc_reads;
extern unsigned long long kairo98_egc_fast_reads;
extern unsigned long long kairo98_egc_mismatch;

extern unsigned long long kairo98_egc_dec_reads;
extern unsigned long long kairo98_egc_cpu_reads;
extern unsigned int kairo98_egc_snap[10];
extern int kairo98_egc_snap_pending;

void kairo98_machine_egc_stats(unsigned long long *writes, unsigned long long *reads,
                               unsigned long long *fast_reads, unsigned long long *mismatch) {
    *writes = kairo98_egc_writes;
    *reads = kairo98_egc_reads;
    *fast_reads = kairo98_egc_fast_reads;
    *mismatch = kairo98_egc_mismatch;
}

void kairo98_machine_egc_snapshot(unsigned int *snap, unsigned long long *dec_reads,
                                  unsigned long long *cpu_reads) {
    int i;
    for (i = 0; i < 10; i++) snap[i] = kairo98_egc_snap[i];
    *dec_reads = kairo98_egc_dec_reads;
    *cpu_reads = kairo98_egc_cpu_reads;
    kairo98_egc_snap_pending = 1;
}

void kairo98_machine_stage_times(unsigned long long *cpu_ns, unsigned long long *event_ns,
                                 unsigned long long *draw_ns, unsigned long long *fm_ns) {
    *cpu_ns = kairo98_time_cpu_ns;
    *event_ns = kairo98_time_event_ns;
    *draw_ns = kairo98_time_draw_ns;
    *fm_ns = kairo98_time_fm_ns;
}

void kairo98_machine_exec(void) {
    pccore_exec(TRUE);
}

void kairo98_machine_counters(unsigned long long *slices, unsigned long long *insts,
                              unsigned long long *sti, unsigned long long *skips) {
    *slices = kairo98_counter_slices;
    *insts = kairo98_counter_insts;
    *sti = kairo98_counter_sti;
    *skips = kairo98_counter_skips;
}

int kairo98_machine_reset(void) {
    keystat_allrelease();
    if (flush_disk() != 0) return 1;
    apply_gdc_dipswitch();
    pccore_reset();
    apply_gdc_clock();
    return 0;
}

int kairo98_machine_set_clock(int mhz_times_ten) {
    if (mhz_times_ten != 20 && mhz_times_ten != 25) return 2;
    keystat_allrelease();
    if (flush_disk() != 0) return 1;
    np2cfg.baseclock = mhz_times_ten == 25 ? PCBASECLOCK25 : PCBASECLOCK20;
    apply_gdc_dipswitch();
    pccore_reset();
    apply_gdc_clock();
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
    apply_gdc_dipswitch();
    pccore_reset();
    apply_gdc_clock();
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
