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
#include "mousemng.h"
#include "sound/sound.h"
#include "statsave.h"
#include "kairo/state_paths.h"
#include "vram.h"
#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

/* The IDE secondary master. */
#define CD_DRIVE 0x02

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

/* Skip memory check: the next start runs the BIOS memory count at NP21/W's fastest
 * (SUPPORT_FAST_MEMORYCHECK), so it passes at once; off, it counts at the real speed. */
static volatile int skip_memory_check = 1;

void kairo98_machine_skip_memory_check(int skip) { skip_memory_check = skip != 0; }
/* Whether the next start runs the player's bios.rom from the BIOS folder instead of the built-in BIOS. */
static volatile int own_bios = 0;
void kairo98_machine_own_bios(int own) { own_bios = own != 0; }

int kairo98_machine_start(const char *image, const char *font_path, const char *bios_dir,
                          int mhz_times_ten, int gdc_mhz_times_ten,
                          int cpu_multiple, int floppy,
                          const char *boot_floppy, const char *second_floppy,
                          const char *cd_image) {
    size_t length = image ? strlen(image) : 0;
    size_t cd_length = cd_image ? strlen(cd_image) : 0;
    size_t boot_length = boot_floppy ? strlen(boot_floppy) : 0;
    size_t second_length = second_floppy ? strlen(second_floppy) : 0;
    size_t bios_length = bios_dir ? strlen(bios_dir) : 0;
    size_t font_length = font_path ? strlen(font_path) : 0;
    if (length >= (floppy ? sizeof(np2cfg.fddfile[0]) :
                            sizeof(np2cfg.sasihdd[0])) ||
        boot_length >= sizeof(np2cfg.fddfile[0]) || (floppy && boot_length) ||
        second_length >= sizeof(np2cfg.fddfile[1]) || cd_length >= sizeof(np2cfg.idecd[2]) ||
        bios_length >= sizeof(np2cfg.biospath) || !font_length ||
        font_length >= sizeof(np2cfg.fontfile)) {
        return 1;
    }
    if ((mhz_times_ten != 20 && mhz_times_ten != 25) ||
        (gdc_mhz_times_ten != 25 && gdc_mhz_times_ten != 50) ||
        cpu_multiple < 1 || cpu_multiple > 20) return 3;
    configured_gdc_clock = gdc_mhz_times_ten;
    apply_gdc_dipswitch();
    np2cfg.baseclock = mhz_times_ten == 25 ? PCBASECLOCK25 : PCBASECLOCK20;
    /* CPU clock = base clock x multiple. Games timed by CPU speed need the rate of
     * the model they were written for. */
    np2cfg.multiple = (UINT32)cpu_multiple;
    np2cfg.memcheckspeed = skip_memory_check ? 255 : 1;
    np2cfg.usebios = own_bios ? 1 : 0;
    np2cfg.sasihdd[0][0] = '\0';
    np2cfg.fddfile[0][0] = '\0';
    np2cfg.fddfile[1][0] = '\0';
    np2cfg.biospath[0] = '\0';
    /* The hard disk is the primary master. The CD-ROM drive is the secondary master and is
     * always present, so a disc can go in at any time; an empty drive holds no disc. */
    np2cfg.idetype[0] = SXSIDEV_HDD;
    np2cfg.idetype[1] = SXSIDEV_NC;
    np2cfg.idetype[2] = SXSIDEV_CDROM;
    np2cfg.idetype[3] = SXSIDEV_NC;
    np2cfg.idecd[CD_DRIVE][0] = '\0';
    if (cd_length) memcpy(np2cfg.idecd[CD_DRIVE], cd_image, cd_length + 1);
    if (bios_length) memcpy(np2cfg.biospath, bios_dir, bios_length + 1);
    memcpy(np2cfg.fontfile, font_path, font_length + 1);
    if (length) {
        if (!floppy) memcpy(np2cfg.sasihdd[0], image, length + 1);
        file_setcd(image);
    }
    pccore_init();
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
    if (cd_length) {
        SXSIDEV drive = sxsi_getptr(CD_DRIVE);
        if (!drive || !(drive->flag & SXSIFLAG_READY)) {
            pccore_term();
            np2cfg.idecd[CD_DRIVE][0] = '\0';
            return 7;
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

/* Inserts a disc, or ejects it for an empty path. A disc already in the drive is ejected
 * first, and the core reports the new one to the guest after the drive's change delay. */
int kairo98_machine_set_cd(const char *image) {
    size_t length = image ? strlen(image) : 0;
    if (length >= sizeof(np2cfg.idecd[CD_DRIVE])) return 1;
    if (sxsi_getdevtype(CD_DRIVE) != SXSIDEV_CDROM) return 3;
    return sxsi_devopen(CD_DRIVE, length ? image : NULL) == SUCCESS ? 0 : 2;
}

/* The image in floppy drive 0 or 1, the CD-ROM drive for 2, or the hard disk for 3; "" when it is empty. */
const char *kairo98_machine_media_path(int drive) {
    const OEMCHAR *path = drive == 3 ? sxsi_getfilename(0) : drive == 2 ? sxsi_getfilename(CD_DRIVE)
                                     : fdd_getfileex((REG8) drive, NULL, NULL);
    return path ? path : "";
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
/*
 * Startup-screen signature (pc98-vram-v1): FNV-1a 64 over the video memory the
 * guest is displaying, not the rendered picture, so the font, BIOS font ROM,
 * shader, renderer and text cursor blink do not change it. Text contributes its
 * character codes and attributes; graphics contribute the displayed page and
 * palette. A layer that is switched off contributes only its off state.
 */
static UINT64 fnv_bytes(UINT64 hash, const UINT8 *bytes, size_t count, size_t stride) {
    for (size_t i = 0; i < count; i += stride) hash = (hash ^ bytes[i]) * 1099511628211ULL;
    return hash;
}

unsigned long long kairo98_machine_screen_signature(void) {
    const UINT8 display = (gdc.mode1 & 0x80) ? 1 : 0;
    const UINT8 text = display && (gdcs.textdisp & GDCSCRN_ENABLE);
    const UINT8 grph = display && (gdcs.grphdisp & GDCSCRN_ENABLE);
    const UINT8 layers[2] = {(UINT8)(text | (grph << 1)), (UINT8)(grph ? (gdc.analog & 3) : 0)};
    UINT64 hash = fnv_bytes(14695981039346656037ULL, layers, sizeof(layers), 1);
    if (text) {
        /* Codes for all 4096 cells, then the attribute byte of each cell. The
         * memory switch bytes at A3FE0h and above are machine settings. */
        hash = fnv_bytes(hash, mem + 0xa0000, 0x2000, 1);
        hash = fnv_bytes(hash, mem + 0xa2000, 0x1fe0, 2);
    }
    if (grph) {
        if (gdc.analog & 2) {
            hash = fnv_bytes(hash, vramex, sizeof(vramex), 1);
            hash = fnv_bytes(hash, gdc.anareg + 16 * 3, 256 * 4, 1);
        } else {
            const UINT32 page = gdcs.disp ? VRAM_STEP : 0;
            hash = fnv_bytes(hash, mem + page + VRAM_B, 0x8000, 1);
            hash = fnv_bytes(hash, mem + page + VRAM_R, 0x8000, 1);
            hash = fnv_bytes(hash, mem + page + VRAM_G, 0x8000, 1);
            if (gdc.analog & 1) {
                hash = fnv_bytes(hash, mem + page + VRAM_E, 0x8000, 1);
                hash = fnv_bytes(hash, gdc.anareg, 16 * 3, 1);
            } else {
                hash = fnv_bytes(hash, gdc.degpal, sizeof(gdc.degpal), 1);
            }
        }
    }
    return hash;
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

/* One call emulates one guest video frame, as long as the GDC mode makes it: 56.4 Hz
 * in the normal 400-line mode, not the host's 60 Hz. Returns the guest clocks it ran. */
unsigned int kairo98_machine_exec(void) {
    const UINT32 start = CPU_CLOCK + CPU_BASECLOCK - CPU_REMCLOCK;
    pccore_exec(TRUE);
    return (CPU_CLOCK + CPU_BASECLOCK - CPU_REMCLOCK) - start;
}

/* The guest clock rate, and the sound stream's samples per clock as hzbase / clockbase. */
void kairo98_machine_clock_rates(unsigned int *realclock, unsigned int *hzbase, unsigned int *clockbase) {
    *realclock = pccore.realclock;
    *hzbase = soundcfg.hzbase;
    *clockbase = soundcfg.clockbase;
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

void kairo98_machine_key(unsigned char code, int down) {
    keystat_senddata((REG8)(code | (down ? 0 : 0x80)));
}

/* Bit 0: Caps lock, bit 1: Kana lock, as the keyboard controller holds them. */
/* Media reads and writes since the machine started (kairo98_media_activity). */
static unsigned long long media_activity;

void kairo98_media_activity(void) { ++media_activity; }

unsigned long long kairo98_machine_media_activity(void) { return media_activity; }

int kairo98_machine_locks(void) {
    return (keyctrl.capsref != NKEYREF_NC ? 1 : 0) | (keyctrl.kanaref != NKEYREF_NC ? 2 : 0);
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
    np2cfg.idecd[CD_DRIVE][0] = '\0';
    return result;
}

void kairo98_machine_location(unsigned short *cs, unsigned short *ip) {
    *cs = CPU_CS;
    *ip = CPU_IP;
}

/*
 * Save states. The core's state file records disk images by path only, so a
 * slot also keeps a copy of every mounted image: loading an older state
 * against a disk the guest has written since would leave the guest's cached
 * file system out of step with the image. disks.txt maps each copy to the
 * working path it came from.
 */
void kairo_ymfm_reload_all_ram(void);

/* Creates the folders above [path]: a working copy sits under its game's own folders. */
static int make_parents(const char *path) {
    char parent[MAX_PATH * 2];
    char *slash;
    if (strlen(path) >= sizeof(parent)) return 1;
    strcpy(parent, path);
    for (slash = strchr(parent + 1, '/'); slash; slash = strchr(slash + 1, '/')) {
        *slash = '\0';
        if (mkdir(parent, 0700) != 0 && errno != EEXIST) return 1;
        *slash = '/';
    }
    return 0;
}

static int copy_file(const char *from, const char *to) {
    FILE *in = fopen(from, "rb");
    if (!in) return 1;
    if (make_parents(to) != 0) {
        fclose(in);
        return 1;
    }
    FILE *out = fopen(to, "wb");
    if (!out) {
        fclose(in);
        return 1;
    }
    static char buffer[64 * 1024];
    size_t count;
    int failed = 0;
    while ((count = fread(buffer, 1, sizeof(buffer), in)) > 0) {
        if (fwrite(buffer, 1, count, out) != count) {
            failed = 1;
            break;
        }
    }
    if (ferror(in)) failed = 1;
    fclose(in);
    if (fclose(out) != 0) failed = 1;
    return failed;
}

/* The longest slot directory: every file in it fits the slot paths' buffers (disks.txt is the longest name). */
static int slot_fits(const char *dir) { return strlen(dir) + sizeof("/disks.txt") <= MAX_PATH * 2; }

static void slot_path(char *out, size_t size, const char *dir, const char *name) {
    snprintf(out, size, "%s/%s", dir, name);
}

int kairo98_machine_save_state(const char *dir) {
    char path[MAX_PATH * 2];
    char copy[MAX_PATH * 2];
    FILE *list;
    int drive;
    if (!dir || !*dir) return 1;
    if (!slot_fits(dir)) return 7;
    if (flush_disk() != 0) return 2;
    slot_path(path, sizeof(path), dir, "disks.txt");
    list = fopen(path, "w");
    if (!list) return 3;
    {
        const OEMCHAR *hdd = sxsi_getfilename(0);
        if (hdd && *hdd) {
            slot_path(copy, sizeof(copy), dir, "hdd0.img");
            if (copy_file(hdd, copy) != 0) {
                fclose(list);
                return 4;
            }
            fprintf(list, "hdd0.img\t%s\n", hdd);
        }
    }
    for (drive = 0; drive < 2; drive++) {
        UINT type;
        int readonly;
        const OEMCHAR *image = fdd_getfileex((REG8)drive, &type, &readonly);
        if (!image || !*image) continue;
        snprintf(copy, sizeof(copy), "%s/fdd%d.img", dir, drive);
        if (copy_file(image, copy) != 0) {
            fclose(list);
            return 4;
        }
        fprintf(list, "fdd%d.img\t%s\n", drive, image);
    }
    if (fclose(list) != 0) return 3;
    slot_path(path, sizeof(path), dir, "state.np2");
    return statsave_save(path) == STATFLAG_SUCCESS ? 0 : 5;
}

int kairo98_machine_load_state(const char *dir) {
    char path[MAX_PATH * 2];
    char line[MAX_PATH * 2 + 32];
    char copy[MAX_PATH * 2];
    OEMCHAR message[256];
    FILE *list;
    int drive;
    int result;
    if (!dir || !*dir) return 1;
    if (!slot_fits(dir)) return 7;
    slot_path(path, sizeof(path), dir, "state.np2");
    /* Reject a missing or incompatible state before touching the running machine.
     * A disk change is expected: the images are restored below. */
    result = statsave_check(path, message, NELEMENTS(message));
    if (result == STATFLAG_FAILURE || (result & STATFLAG_VERSION)) return 2;
    slot_path(path, sizeof(path), dir, "disks.txt");
    list = fopen(path, "r");
    if (!list) return 3;
    keystat_allrelease();
    flush_disk();
    sxsi_alltrash();
    for (drive = 0; drive < 2; drive++) diskdrv_setfddex((REG8)drive, NULL, FTYPE_NONE, 0);
    result = 0;
    while (fgets(line, sizeof(line), list)) {
        char *tab = strchr(line, '\t');
        char *end;
        if (!tab) continue;
        *tab = '\0';
        end = tab + 1 + strcspn(tab + 1, "\r\n");
        *end = '\0';
        slot_path(copy, sizeof(copy), dir, line);
        /* Each image goes back to its disk's working copy, wherever that is now (kairo_state_path). */
        if (copy_file(copy, kairo_state_path(tab + 1)) != 0) result = 4;
    }
    fclose(list);
    if (result != 0) return result;
    slot_path(path, sizeof(path), dir, "state.np2");
    result = statsave_load(path);
    if (result == STATFLAG_FAILURE) return 5;
    kairo_ymfm_reload_all_ram();
    mousemng_reset();
    return 0;
}
