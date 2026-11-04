#!/usr/bin/env python3
"""Writes MOUSE98.COM, the shared PC boot test's PC-98 mouse program (Kairo98#122): 8086 code that
reads the bus mouse's counters itself (8255 at 7FD9h-7FDDh), with the mouse interrupt off, so the
DOS disk's accelerating mouse driver (Q's MOUSE) never sees the motion. It moves its pointer, an
asterisk in text VRAM, a pixel per count over 640 x 400 (stopping at the edges, a text cell per
8 x 16 pixels), once per frame (the text GDC's vertical sync), and on a press of the left button
colours the whole text screen from where the pointer is. It never ends.

    make_pc98_mouse_com.py <output>

make_dos_prompt_hdi.py puts it in the DOS prompt disk's root, A:\\MOUSE98.COM.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "shared/tools"))
from x86_assembler import JE, JGE, JLE, JNZ, JZ, Assembler  # noqa: E402

PORT_A, PORT_C = 0x7FD9, 0x7FDD
# Port C: HC (bit 7) latches and clears the counters, SXY (bit 6) picks Y, SHL (bit 5) the high
# nibble, INT (bit 4) turns the mouse interrupt off.
INT_OFF = 0x10


def program():
    a = Assembler()
    b, w = a.b, a.w

    def out_c(value):
        b(0xBA); w(PORT_C)          # mov dx, PORT_C
        b(0xB0, value)              # mov al, value
        b(0xEE)                     # out dx, al

    def in_a():
        b(0xBA); w(PORT_A)          # mov dx, PORT_A
        b(0xEC)                     # in al, dx

    out_c(INT_OFF)
    b(0xC7, 0x06); w("x"); w(320)   # mov word [x], 320
    b(0xC7, 0x06); w("y"); w(200)   # mov word [y], 200
    b(0xC7, 0x06); w("prev"); w(0xFFFF)
    b(0xC6, 0x06); w("button"); b(0x80)  # released

    a.label("frame")
    # One pass a frame: out of the vertical sync, then into it (text GDC status, port 60h, bit 5).
    a.label("leave")
    b(0xE4, 0x60); b(0xA8, 0x20); a.jcc(JNZ, "leave")
    a.label("enter")
    b(0xE4, 0x60); b(0xA8, 0x20); a.jcc(JZ, "enter")

    # The counters: X low and high nibbles into BL, Y into BH; then the button.
    b(0xB1, 4)                      # mov cl, 4
    out_c(INT_OFF | 0x80); in_a(); b(0x24, 0x0F); b(0x88, 0xC3)          # bl = X low
    out_c(INT_OFF | 0xA0); in_a(); b(0xD2, 0xE0); b(0x08, 0xC3)          # bl |= X high << 4
    out_c(INT_OFF | 0xC0); in_a(); b(0x24, 0x0F); b(0x88, 0xC7)          # bh = Y low
    out_c(INT_OFF | 0xE0); in_a(); b(0xD2, 0xE0); b(0x08, 0xC7)          # bh |= Y high << 4
    out_c(INT_OFF)
    in_a(); b(0xA2); w("now")       # mov [now], al: bit 7 the left button, 0 pressed

    def axis(reg_to_al, var, limit, done):
        b(0x88, reg_to_al)          # mov al, bl / bh
        b(0x98)                     # cbw
        b(0x03, 0x06); w(var)       # add ax, [var]
        b(0x85, 0xC0)               # test ax, ax
        a.jcc(JGE, done + "_low")
        b(0x31, 0xC0)               # xor ax, ax
        a.label(done + "_low")
        b(0x3D); w(limit)           # cmp ax, limit
        a.jcc(JLE, done)
        b(0xB8); w(limit)           # mov ax, limit
        a.label(done)
        b(0xA3); w(var)             # mov [var], ax

    axis(0xD8, "x", 639, "x_done")
    axis(0xF8, "y", 399, "y_done")

    # The pointer's cell: (y / 16) * 80 + x / 8, two bytes each.
    b(0xA1); w("y"); b(0xB1, 4); b(0xD3, 0xE8)       # ax = y >> 4
    b(0xBB); w(80); b(0xF7, 0xE3); b(0x89, 0xC7)     # di = ax * 80
    b(0xA1); w("x"); b(0xB1, 3); b(0xD3, 0xE8)       # ax = x >> 3
    b(0x01, 0xC7); b(0xD1, 0xE7)                     # di = (di + ax) * 2
    b(0xB8); w(0xA000); b(0x8E, 0xC0)                # es = text VRAM
    b(0x3B, 0x3E); w("prev"); a.jcc(JE, "drawn")     # cmp di, [prev]
    b(0x8B, 0x36); w("prev")                         # mov si, [prev]
    b(0x81, 0xFE); w(0xFFFF); a.jcc(JE, "unsaved")   # cmp si, 0FFFFh
    b(0xA1); w("saved"); b(0x26, 0x89, 0x04)         # es:[si] = [saved]
    a.label("unsaved")
    b(0x26, 0x8B, 0x05); b(0xA3); w("saved")         # [saved] = es:[di]
    b(0x26, 0xC7, 0x05); w(0x002A)                   # es:[di] = '*'
    b(0x89, 0x3E); w("prev")                         # [prev] = di
    a.label("drawn")

    # A press of the left button: every text cell's colour from the pointer's place.
    b(0xA0); w("now"); b(0x24, 0x80)                 # al = [now] & 80h
    b(0x3A, 0x06); w("button"); a.jcc(JE, "next")    # cmp al, [button]
    b(0xA2); w("button"); b(0x84, 0xC0); a.jcc(JNZ, "next")  # released: nothing
    b(0xA1); w("x"); b(0xB1, 7); b(0xD3, 0xE8); b(0x89, 0xC3)  # bx = x >> 7
    b(0xA1); w("y"); b(0xD3, 0xE8); b(0x01, 0xD8)    # ax = (y >> 7) + bx
    b(0xB3, 7); b(0xF6, 0xF3); b(0x88, 0xE0)         # al = ax % 7
    b(0xFE, 0xC0); b(0xB1, 5); b(0xD2, 0xE0); b(0x0C, 0x01)  # al = (al + 1) << 5 | shown
    b(0xBB); w(0xA200); b(0x8E, 0xC3)                # es = text attributes
    b(0x31, 0xFF); b(0xB9); w(2000)                  # di = 0, cx = 80 x 25
    a.label("fill")
    b(0x26, 0x88, 0x05); b(0x83, 0xC7, 0x02); a.jcc(0xE2, "fill")  # es:[di] = al; di += 2; loop
    a.label("next")
    a.jmp_near("frame")

    for name, size in (("x", 2), ("y", 2), ("prev", 2), ("saved", 2), ("button", 1), ("now", 1)):
        a.label(name)
        b(*([0] * size))
    return a.build()


def main(argv):
    if len(argv) != 2:
        raise SystemExit(__doc__)
    data = program()
    Path(argv[1]).write_bytes(data)
    print(f"{argv[1]}: {len(data)} bytes")


if __name__ == "__main__":
    main(sys.argv)
