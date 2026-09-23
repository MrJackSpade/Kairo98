#include "compiler.h"
#include "cpucore.h"
#include "font.h"
#include "spleen_ascii_8x16.h"

static void install_ascii_bitmap(void) {
    for (unsigned int code = 0x21; code <= 0x7e; ++code) {
        const unsigned char *glyph = spleen_ascii_8x16[code - 0x20];
        memcpy(fontrom + 0x80000 + code * 16, glyph, 16);
        size_t offset = 0x1000 + ((size_t)(0x29 - 0x20) << 4) +
                        ((size_t)(code - 1) << 12);
        memcpy(fontrom + offset, glyph, 16);
        memset(fontrom + offset + 0x800, 0, 16);
    }
}

/* K98FNT1: eight-byte magic, big-endian record count, then 34-byte records.
 * A record contains a JIS row/cell and sixteen pairs of monochrome row bytes.
 * Android creates this cache from its installed Japanese system font. */
int kairo98_font_overlay_load(const char *path) {
    static const unsigned char magic[8] = {'K', '9', '8', 'F', 'N', 'T', '1', 0};
    unsigned char header[12];
    unsigned char record[34];
    FILE *file = fopen(path, "rb");
    if (!file) return -1;
    if (fread(header, 1, sizeof(header), file) != sizeof(header) ||
        memcmp(header, magic, sizeof(magic)) != 0) {
        fclose(file);
        return -1;
    }
    unsigned int count = ((unsigned int)header[8] << 24) |
                         ((unsigned int)header[9] << 16) |
                         ((unsigned int)header[10] << 8) | header[11];
    if (count == 0 || count > 8192) {
        fclose(file);
        return -1;
    }
    for (unsigned int entry = 0; entry < count; ++entry) {
        if (fread(record, 1, sizeof(record), file) != sizeof(record)) {
            fclose(file);
            return -1;
        }
        unsigned int row = record[0];
        unsigned int cell = record[1];
        if (row == 0 && ((cell >= 0x21 && cell <= 0x7e) ||
                         (cell >= 0xa1 && cell <= 0xdf))) {
            for (unsigned int y = 0; y < 16; ++y) {
                fontrom[0x80000 + cell * 16 + y] = record[2 + y * 2];
            }
            continue;
        }
        if (row < 0x21 || row > 0x7f || cell < 1 || cell > 0x7f) {
            fclose(file);
            return -1;
        }
        size_t offset = 0x1000 + ((size_t)(row - 0x20) << 4) +
                        ((size_t)(cell - 1) << 12);
        if (offset + 0x800 + 16 > FONTMEMORYSIZE) {
            fclose(file);
            return -1;
        }
        for (unsigned int y = 0; y < 16; ++y) {
            fontrom[offset + y] = record[2 + y * 2];
            fontrom[offset + 0x800 + y] = record[3 + y * 2];
        }
    }
    int trailing = fgetc(file);
    fclose(file);
    if (trailing != EOF) return -1;
    install_ascii_bitmap();
    return 0;
}
