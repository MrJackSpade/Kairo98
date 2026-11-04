#!/usr/bin/env python3
"""Makes the shared PC boot test's DOS prompt hard disk (Kairo98#126) from Aegean Kai no Shizuku's
translation archive: its HDI with the last line of AUTOEXEC.BAT, which starts the game, turned into a
remark, so the machine boots DOS (with the mouse driver loaded) to its prompt in A:\\EGEE. The same
disk is also written as an NHD (T98-Next's format), so the test covers both kinds Kairo98 boots.

    make_dos_prompt_hdi.py <Aegean Kai No Shizuku [T-En by Svipur v1.1].zip> <output.zip>

The line keeps its length ("GAME" becomes "REM "), so the file's size and the disk's FAT stay as
they are. The disk's root also gets MOUSE98.COM, the direct-tap check's mouse program
(make_pc98_mouse_com.py), written with the shared fat_disk.py. uitest/resources.json lists the
output under host/, which the device's test APK leaves out.
"""
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "shared/tools"))
sys.path.insert(0, str(ROOT / "tools"))
from fat_disk import add_file  # noqa: E402
import make_pc98_mouse_com  # noqa: E402

IMAGE = "Aegean Kai No Shizuku [T-En by Svipur v1.1].hdi"
OUTPUT_IMAGE = "Aegean Kai no Shizuku DOS prompt.hdi"
OUTPUT_NHD = "Aegean Kai no Shizuku DOS prompt.nhd"
# AUTOEXEC.BAT's end, up to its EOF mark.
GAME_LINE = b"\r\nCD EGEE\r\nGAME\r\n\x1a"
PROMPT_LINE = b"\r\nCD EGEE\r\nREM \r\n\x1a"


def main(argv):
    if len(argv) != 3:
        raise SystemExit(__doc__)
    source, output = Path(argv[1]), Path(argv[2])
    with zipfile.ZipFile(source) as archive:
        names = [n for n in archive.namelist() if n.endswith(IMAGE)]
        if len(names) != 1:
            raise SystemExit(f"{source} holds {len(names)} files named {IMAGE}")
        image = archive.read(names[0])
    if image.count(GAME_LINE) != 1:
        raise SystemExit(f"{IMAGE} holds AUTOEXEC.BAT's game line {image.count(GAME_LINE)} times, not once")
    prompt = bytearray(image.replace(GAME_LINE, PROMPT_LINE))
    add_file(prompt, partition(prompt), "MOUSE98.COM", make_pc98_mouse_com.program())
    prompt = bytes(prompt)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(OUTPUT_IMAGE, prompt)
        archive.writestr(OUTPUT_NHD, nhd(prompt))
    print(f"{output}: {OUTPUT_IMAGE} and {OUTPUT_NHD}, {len(prompt)} bytes")
    return 0


def partition(hdi):
    """The byte where the HDI's first partition (PC-98's partition table, sector 1) starts."""
    def dword(at):
        return int.from_bytes(hdi[at:at + 4], "little")
    header_size, sector_size, sectors, surfaces = dword(8), dword(16), dword(20), dword(24)
    entry = header_size + sector_size
    cylinder = int.from_bytes(hdi[entry + 10:entry + 12], "little")
    return header_size + cylinder * surfaces * sectors * sector_size


def nhd(hdi):
    """The HDI's disk as an NHD: np21w's NHDHDR (fdd/sxsihdd.h, 512 bytes), then the same sectors."""
    def dword(at):
        return int.from_bytes(hdi[at:at + 4], "little")
    header_size, size, sector_size = dword(8), dword(12), dword(16)
    sectors, surfaces, cylinders = dword(20), dword(24), dword(28)
    if cylinders * surfaces * sectors * sector_size != size:
        raise SystemExit(f"the HDI's geometry ({cylinders}x{surfaces}x{sectors}x{sector_size}) is not its {size} bytes")
    header = (b"T98HDDIMAGE.R0".ljust(16, b"\0") + bytes(0x100) + (512).to_bytes(4, "little") +
              cylinders.to_bytes(4, "little") + surfaces.to_bytes(2, "little") + sectors.to_bytes(2, "little") +
              sector_size.to_bytes(2, "little") + bytes(0xE2))
    if len(header) != 512:
        raise SystemExit(f"the NHD header is {len(header)} bytes, not 512")
    return header + hdi[header_size:header_size + size]


if __name__ == "__main__":
    sys.exit(main(sys.argv))
