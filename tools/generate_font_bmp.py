"""Write the bundled PC-98 FONT.BMP from pinned, redistributable bitmap fonts.

The output uses the 2048x2048 monochrome layout that 21/W's FONT.BMP loader
reads (font/fontpc98.c), assembled the way 21/W's font/fontmake.c builds one:

- ANK 0x20-0x7E and 0xA1-0xDF: Shinonome 16 JIS X 0201 (public domain).
- ANK 0x00-0x1F, 0x80-0x9F, 0xE0-0xFF: 21/W's built-in PC-98 graphic glyphs,
  with 0xF1-0xF7 and 0xFC derived from Shinonome as fontmake.c does.
- JIS X 0208 kanji and symbols: Shinonome 16 (public domain), with the PC-98
  JIS 78 code swaps and unassigned-code filter from fontmake.c.
- Half-width rows 0x29-0x2A: the ANK glyphs, converted as fontmake.c does.
- Half-width row 0x2B and NEC row 0x2C: 21/W's built-in glyphs, with the
  0x2B74-0x2B7E brackets derived from Shinonome as fontmake.c does.

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


ank_font = read_bdf(root / "third_party/shinonome/shnm8x16r.bdf")
kanji = read_bdf(root / "third_party/shinonome/shnmk16.bdf")
fontdata_16 = read_res("fontdata_16")
fontdata_2b = read_res("fontdata_2b")
fontdata_2c = read_res("fontdata_2c")
for name, data, size in (("fontdata_16", fontdata_16, 3 * 32 * 16),
                         ("fontdata_2b", fontdata_2b, 94 * 16),
                         ("fontdata_2c", fontdata_2c, 76 * 16 * 2)):
    if len(data) != size:
        raise ValueError(f"{name}: expected {size} bytes, found {len(data)}")

# Glyphs are 16 row bytes with 1 bits for ink, MSB leftmost. 16-dot glyphs
# are 16 row words.
ank = {code: list(ank_font[code]) for code in list(range(0x20, 0x7f)) + list(range(0xa1, 0xe0))}
for block, base in enumerate((0x00, 0x80, 0xe0)):
    for index in range(32):
        start = (block * 32 + index) * 16
        ank[base + index] = list(fontdata_16[start:start + 16])

wide = {}
for row in range(0x21, 0x80):
    for cell in range(0x21, 0x7f):
        jis = (row << 8) | cell
        if is_pc98_jis(jis) and swap.get(jis, jis) in kanji:
            wide[jis] = list(kanji[swap.get(jis, jis)])
kanji_count = len(wide)
half = {}  # 8-dot glyphs in JIS rows 0x29-0x2B
for index in range(94):
    half[0x2b21 + index] = list(fontdata_2b[index * 16:index * 16 + 16])
for index in range(76):
    pairs = fontdata_2c[index * 32:index * 32 + 32]
    wide[0x2c24 + index] = [(left << 8) | right for left, right in zip(pairs[0::2], pairs[1::2])]


# fontmake.c copyglyph conversions from 16 to 8 dots and between 8-dot glyphs.
def squeeze(rows):
    """Merge each pair of columns, keeping ink from either."""
    out = []
    for bits in rows:
        value = 0
        for x in range(8):
            if bits & (0xc000 >> (x * 2)):
                value |= 0x80 >> x
        out.append(value)
    return out


def adjust(rows, right):
    """Take the 8 columns around the ink of a 16-dot bracket."""
    used = 0
    for bits in rows:
        used |= bits
    begin = next((x for x in range(16) if used & (0x8000 >> x)), 16)
    end = next((x for x in range(16, begin, -1) if used & (0x10000 >> x)), begin)
    if right:
        begin = max(0, begin - (8 - (end - begin)))
    elif end - begin < 8 and begin > 0:
        begin -= 1
    return [((bits << begin) >> 8) & 0xff for bits in rows]


def mirror(rows):
    out = [int(f"{bits:08b}"[::-1], 2) for bits in rows]
    used = 0
    for bits in out:
        used |= bits
    return [bits >> 1 for bits in out] if not used & 0x01 else out


def narrow(rows):
    """Close a 7-dot glyph to 6 dots when it has no empty right column."""
    used = differs = 0
    for bits in rows:
        used |= bits
        differs |= (bits ^ (bits << 1)) & 0xff
    if not used & 0x01:
        return rows
    if not used & 0xc0:
        return [(bits << 1) & 0xff for bits in rows]
    if not differs & 0x08:
        return [(bits & 0xf0) | ((bits & 0x07) << 1) for bits in rows]
    if not differs & 0x10:
        return [(bits & 0xe0) | ((bits & 0x0f) << 1) for bits in rows]
    return rows


def voiced(rows):
    out = list(rows)
    for y in (0, 1):
        out[y] = (out[y] & ~0x07) | 0x05
    return out


def semivoiced(rows):
    out = list(rows)
    for y, mark in ((0, 0x02), (1, 0x05), (2, 0x05), (3, 0x02)):
        out[y] = (out[y] & ~0x07) | mark
    return out


for code, jis in zip(range(0xf1, 0xf8), (0x315f, 0x472f, 0x376e, 0x467c, 0x3b7e, 0x4a2c, 0x4943)):
    ank[code] = squeeze(wide[jis])  # 円年月日時分秒
for index in range(0x5e):
    glyph = ank[0x21 + index]
    half[0x2921 + index] = narrow(glyph) if 0x20 <= index < 0x39 or 0x40 <= index < 0x59 else glyph
for index in range(0x3f):
    half[0x2a21 + index] = ank[0xa1 + index]
for index, jis in enumerate((0x2570, 0x2571, 0x256e, 0x2575, 0x2576)):
    half[0x2a60 + index] = squeeze(wide[jis])  # ヰヱヮヵヶ
half[0x2a65] = voiced(ank[0xb3])
for index in range(15):
    half[0x2a66 + index] = voiced(ank[0xb6 + index])
for index in range(5):
    half[0x2a75 + index * 2] = voiced(ank[0xca + index])
    half[0x2a75 + index * 2 + 1] = semivoiced(ank[0xca + index])
for dst, jis in zip(range(0x2b74, 0x2b7e), (0x214c, 0x214d, 0x2152, 0x2153, 0x2154, 0x2155,
                                           0x2158, 0x2159, 0x215a, 0x215b)):
    half[dst] = adjust(wide[jis], dst % 2 == 0)  # 〔〕〈〉《》『』【】
half[0x2b7e] = ank[ord("-")]
ank[0xfc] = mirror(ank[ord("/")])  # backslash

for code, rows in ank.items():
    put_ank(code, rows)
for jis, rows in wide.items():
    put_kanji(jis >> 8, jis & 0xff, [bits >> 8 for bits in rows], [bits & 0xff for bits in rows])
for jis, rows in half.items():
    put_kanji(jis >> 8, jis & 0xff, rows)

palette = bytes([0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0x00])
image = b"".join(bytes(pixels[y * STRIDE:(y + 1) * STRIDE]) for y in reversed(range(HEIGHT)))
offset = 14 + 40 + len(palette)
header = struct.pack("<2sIHHI", b"BM", offset + len(image), 0, 0, offset)
info = struct.pack("<IiiHHIIiiII", 40, WIDTH, HEIGHT, 1, 1, 0, len(image), 0, 0, 2, 2)
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(header + info + palette + image)
print(f"Wrote {target.relative_to(root)}: {kanji_count} JIS glyphs from Shinonome 16")
