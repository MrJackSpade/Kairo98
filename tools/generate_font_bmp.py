"""Write the bundled PC-98 FONT.BMP from pinned, redistributable bitmap fonts.

The output uses the 2048x2048 monochrome layout that 21/W's FONT.BMP loader
reads (font/fontpc98.c), assembled the way 21/W's font/fontmake.c builds one:

- ANK 0x20-0x7E: Spleen 8x16 (BSD-2-Clause), the table the core already uses.
- ANK 0xA1-0xDF: Shinonome 16 JIS X 0201 half-width katakana (public domain).
- ANK 0x00-0x1F, 0x80-0x9F, 0xE0-0xFF: 21/W's built-in PC-98 graphic glyphs.
- JIS X 0208 kanji and symbols: Shinonome 16 (public domain), with the PC-98
  JIS 78 code swaps and unassigned-code filter from fontmake.c.
- Half-width rows 0x29-0x2B and NEC row 0x2C: 21/W's built-in glyphs.

Glyph ink is a 0 bit (palette black); the loader inverts it into font ROM.
"""

import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parent.parent
target = root / "app/src/main/assets/font/kairo98-font.bmp"

WIDTH = HEIGHT = 2048
STRIDE = WIDTH // 8
pixels = bytearray(b"\xff" * (STRIDE * HEIGHT))  # top-down while building


def read_bdf(path):
    glyphs = {}
    code = None
    rows = None
    for line in path.read_text(encoding="ascii").splitlines():
        if line.startswith("STARTCHAR "):
            code, rows = None, None
        elif line.startswith("ENCODING "):
            code = int(line.split()[1])
        elif line.startswith("BBX "):
            if line.split()[1:] not in (["16", "16", "0", "-2"], ["8", "16", "0", "-2"]):
                raise ValueError(f"{path.name}: unexpected {line} for {code}")
        elif line == "BITMAP":
            rows = []
        elif line == "ENDCHAR":
            if len(rows) != 16:
                raise ValueError(f"{path.name}: expected 16 rows for {code}")
            glyphs[code] = rows
            rows = None
        elif rows is not None:
            rows.append(int(line, 16))
    return glyphs


def read_res(name):
    source = (root / "third_party/np21w/font/fontdata.res").read_text(encoding="cp932")
    match = re.search(r"const UINT8 " + name + r"\[[^\]]*\] = \{(.*?)\};", source, re.S)
    return bytes(int(value, 16) for value in re.findall(r"0x([0-9A-Fa-f]{2})", match.group(1)))


def put_ank(code, rows):
    for y, bits in enumerate(rows):
        pixels[y * STRIDE + code] = ~bits & 0xff


def put_kanji(row, cell, left, right=None):
    """Store a JIS row/cell glyph; `left`/`right` are 16 row bytes each."""
    x = (row - 0x20) * 2
    for y in range(16):
        offset = (cell * 16 + y) * STRIDE + x
        pixels[offset] = ~left[y] & 0xff
        pixels[offset + 1] = 0xff if right is None else ~right[y] & 0xff


# fontmake.c deltable: [from, to) cell ranges (cell - 0x20) absent from PC-98
# JIS rows 0x22-0x2D.
DELTABLE = [
    [(0x0f, 0x5f)],
    [(0x01, 0x10), (0x1a, 0x21), (0x3b, 0x41), (0x5b, 0x5f)],
    [(0x54, 0x5f)],
    [(0x57, 0x5f)],
    [(0x19, 0x21), (0x39, 0x5f)],
    [(0x22, 0x31), (0x52, 0x5f)],
    [(0x01, 0x5f)],
    [(0x01, 0x5f)],
    [(0x01, 0x5f)],
    [(0x01, 0x5f)],
    [(0x01, 0x5f)],
    [(0x1f, 0x20), (0x37, 0x3f), (0x5d, 0x5f)],
]

# fontmake.c jis7883 and jis8390: PC-98 font ROM keeps the JIS 78 positions.
JIS_SWAPS = [
    (0x3646, 0x7421), (0x4b6a, 0x7422), (0x4d5a, 0x7423), (0x596a, 0x7424),
    (0x724d, 0x3033), (0x7274, 0x3229), (0x695a, 0x3342), (0x5978, 0x3349),
    (0x635e, 0x3376), (0x5e75, 0x3443), (0x6b5d, 0x3452), (0x7074, 0x375b),
    (0x6268, 0x395c), (0x6922, 0x3c49), (0x7057, 0x3f59), (0x6c4d, 0x4128),
    (0x5464, 0x445b), (0x626a, 0x4557), (0x5b6d, 0x456e), (0x5e39, 0x4573),
    (0x6d6e, 0x4676), (0x6a24, 0x4768), (0x5b58, 0x4930), (0x5056, 0x4b79),
    (0x692e, 0x4c79), (0x6446, 0x4f36),
]
swap = {}
for a, b in JIS_SWAPS:
    swap[a], swap[b] = b, a


def is_pc98_jis(jis):
    row, cell = jis >> 8, jis & 0xff
    if 0x22 <= row <= 0x2d:
        return not any(lo <= cell - 0x20 < hi for lo, hi in DELTABLE[row - 0x22])
    if row == 0x4f:
        return cell < 0x54
    if row == 0x7c:
        return cell not in (0x6f, 0x70)
    return row not in (0x2e, 0x2f, 0x74, 0x75, 0x76, 0x77, 0x78, 0x7d, 0x7e, 0x7f)


spleen = {}
for index, match in enumerate(re.findall(
        r"\{((?:0x[0-9a-f]{2}, ){15}0x[0-9a-f]{2})\}",
        (root / "third_party/spleen/spleen_ascii_8x16.h").read_text(encoding="ascii"))):
    spleen[0x20 + index] = [int(value, 16) for value in match.split(", ")]
if sorted(spleen) != list(range(0x20, 0x7f)):
    raise ValueError("Spleen table does not cover printable ASCII")

kana = read_bdf(root / "third_party/shinonome/shnm8x16r.bdf")
kanji = read_bdf(root / "third_party/shinonome/shnmk16.bdf")
fontdata_16 = read_res("fontdata_16")
fontdata_29 = read_res("fontdata_29")
fontdata_2a = read_res("fontdata_2a")
fontdata_2b = read_res("fontdata_2b")
fontdata_2c = read_res("fontdata_2c")
for name, data, size in (("fontdata_16", fontdata_16, 3 * 32 * 16),
                         ("fontdata_29", fontdata_29, 94 * 16),
                         ("fontdata_2a", fontdata_2a, 94 * 16),
                         ("fontdata_2b", fontdata_2b, 94 * 16),
                         ("fontdata_2c", fontdata_2c, 76 * 16 * 2)):
    if len(data) != size:
        raise ValueError(f"{name}: expected {size} bytes, found {len(data)}")

for code in range(0x20, 0x7f):
    put_ank(code, spleen[code])
for code in range(0xa1, 0xe0):
    put_ank(code, kana[code])
for block, base in enumerate((0x00, 0x80, 0xe0)):
    for index in range(32):
        start = (block * 32 + index) * 16
        put_ank(base + index, fontdata_16[start:start + 16])

kanji_count = 0
for row in range(0x21, 0x80):
    for cell in range(0x21, 0x7f):
        jis = (row << 8) | cell
        if not is_pc98_jis(jis):
            continue
        rows = kanji.get(swap.get(jis, jis))
        if rows is None:
            continue
        put_kanji(row, cell, [bits >> 8 for bits in rows], [bits & 0xff for bits in rows])
        kanji_count += 1

for row, data in ((0x29, fontdata_29), (0x2a, fontdata_2a), (0x2b, fontdata_2b)):
    for index in range(94):
        put_kanji(row, 0x21 + index, data[index * 16:index * 16 + 16])
# fontmake.c patchextfnt: each row pair is (left half, right half), cells 0x24-0x6F.
for index in range(76):
    pairs = fontdata_2c[index * 32:index * 32 + 32]
    put_kanji(0x2c, 0x24 + index, pairs[0::2], pairs[1::2])

palette = bytes([0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0x00])
image = b"".join(bytes(pixels[y * STRIDE:(y + 1) * STRIDE]) for y in reversed(range(HEIGHT)))
offset = 14 + 40 + len(palette)
header = struct.pack("<2sIHHI", b"BM", offset + len(image), 0, 0, offset)
info = struct.pack("<IiiHHIIiiII", 40, WIDTH, HEIGHT, 1, 1, 0, len(image), 0, 0, 2, 2)
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(header + info + palette + image)
print(f"Wrote {target.relative_to(root)}: {kanji_count} JIS glyphs from Shinonome 16")
