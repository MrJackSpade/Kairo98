#!/usr/bin/env python3
"""Writes MIDI98.COM, the shared PC boot test's PC-98 MIDI program (Kairo98#132): 8086 code that puts the
MPU-PC98II (data E0D0h, command and status E0D2h, np2's default) in UART mode, reads its acknowledgement,
writes the MIDI bytes of SEQUENCE to its data port one at a time, each when the MPU is ready, and ends.

    make_pc98_midi_com.py <output>

The sequence holds every kind of message the shared cutter (kairo/midi_bytes.h) tells apart: a SysEx,
channel messages of two and three bytes, data bytes under running status, and a real-time byte.
MESSAGES is what the shared sender gets for it, after the All Notes Off np2's MPU sends on every
channel as it takes the UART command (Bn 7B 00, n from 0 to F). make_dos_prompt_hdi.py puts the program in the DOS
prompt disk's root, A:\\MIDI98.COM.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "shared/tools"))
from x86_assembler import JNZ, LOOP, Assembler  # noqa: E402

DATA, STATUS = 0xE0D0, 0xE0D2
# Status: bit 6 set while the MPU cannot take a byte, bit 7 set while it has none to read.
BUSY, EMPTY = 0x40, 0x80
UART = 0x3F

MESSAGES = [
    "F0 7E 7F 09 01 F7",  # General MIDI on
    "C1 05",              # program change, channel 2
    "91 3C 64",           # note on
    "3C 00",              # under running status: note off as a note on of velocity 0
    "3E 64",              # and another note on
    "F8",                 # timing clock, alone
    "B1 07 7F",           # volume
    "81 3E 00",           # note off
]
SEQUENCE = bytes(int(byte, 16) for message in MESSAGES for byte in message.split())


def program():
    a = Assembler()
    b, w = a.b, a.w

    def wait_clear(bit, label):
        b(0xBA); w(STATUS)            # mov dx, STATUS
        a.label(label)
        b(0xEC)                       # in al, dx
        b(0xA8, bit)                  # test al, bit
        a.jcc(JNZ, label)

    wait_clear(BUSY, "command_ready")
    b(0xB0, UART); b(0xEE)            # mov al, 3Fh; out dx, al
    wait_clear(EMPTY, "acknowledged")
    b(0xBA); w(DATA); b(0xEC)         # mov dx, DATA; in al, dx (the acknowledgement)

    b(0xBE); w("sequence")            # mov si, sequence
    b(0xB9); w(len(SEQUENCE))         # mov cx, its length
    a.label("next")
    wait_clear(BUSY, "byte_ready")
    b(0xAC)                           # lodsb
    b(0xBA); w(DATA); b(0xEE)         # mov dx, DATA; out dx, al
    a.jcc(LOOP, "next")

    b(0xB8); w(0x4C00); b(0xCD, 0x21)  # mov ax, 4C00h; int 21h
    a.label("sequence")
    b(*SEQUENCE)
    return a.build()


def main(argv):
    if len(argv) != 2:
        raise SystemExit(__doc__)
    data = program()
    Path(argv[1]).write_bytes(data)
    print(f"{argv[1]}: {len(data)} bytes")


if __name__ == "__main__":
    main(sys.argv)
