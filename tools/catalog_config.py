"""Kairo98's catalog settings for the shared generator (shared/tools/app_catalog.py): PC-98 hard disk,
floppy and CD media, its self-hosted artwork references, the machine settings and launch fields a
game can carry, and the drives its disk swaps use. tools/catalog_research.py assembles
catalog/research/games-v1.json from Kairo98's research records."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "shared/tools"))
from launchbox_artwork import References

APP = "kairo98"
PLATFORMS = ("pc98",)
ARTWORK_CODE = 98
# PC-88 and PC-98 images, self-hosted in catalog/artwork (tools/artwork_references.py).
ARTWORK = References("Kairo98", (88, 98))

KINDS = {"hdi", "fd", "cd"}
# Kairo98's games name their companion disks themselves (catalog/startup-profiles-v1.json):
# the disks a hard disk boots with and a CD's boot disk.
COMPANION = {}
MEDIA_ROLES = {"bootFloppy", "floppyB", "cd", "bootDisk"}

# The machine settings a game can ask for, as Pc98Catalog validates them.
MACHINE_OPTIONS = {
    "gdcClockTenthsMHz": (25, 50),
    "cpuMultiple": (1, 2, 3, 4, 6, 8, 10, 12, 16, 20),
    "baseClockTenthsMHz": (20, 25),
    "system": ("builtin", "own"),
    "skipMemoryCheck": ("on", "off"),
}

_HASH = re.compile(r"[0-9a-f]{16}\Z")


def _hash_groups(value):
    return isinstance(value, list) and 1 <= len(value) <= 4 and all(
        isinstance(group, list) and 1 <= len(group) <= 16 and all(isinstance(h, str) and _HASH.match(h) for h in group)
        for group in value)


# A launch's fields besides its commands, as Pc98Catalog validates them: a DOS command typed at the
# prompt or on the screens it waits for (one group per command).
LAUNCH_FIELDS = {
    "type": r"guestCommand",
    "text": r"[A-Za-z0-9 \\/._:-]{1,128}",
    "ready": r"dosPrompt",
    "timeoutMs": lambda value: type(value) is int and 1000 <= value <= 120000,
    "screenHashes": _hash_groups,
}

# Floppy drives A and B (MainActivity.mediaSlots, Pc98Catalog's CatalogSetting.DiskSwaps).
DISK_SWAPS = {0: {"fd"}, 1: {"fd"}}

# The extensions the app removes from a file's name before looking it up (the catalog schema's
# mediaExtensions); the name index is built the same way (shared app_catalog.lookup_name).
MEDIA_EXTENSIONS = r"\.(?:zip|hdi|fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf|ccd)$"

# Where the research comes from: VNDB-derived metadata carries the ODbL.
PROVENANCE = {
    "source": "Kairo98 local match catalog, LaunchBox image cache, and VNDB 2026-09-26 dump",
    "license": "VNDB-derived metadata: ODbL 1.0; artwork used under fair use",
    "attribution": "LaunchBox Games Database contributors; VNDB contributors; Kairo98 original descriptions",
}
