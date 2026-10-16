#!/usr/bin/env python3
"""Record multi-disk sets from a device library cache as catalog set entry points.

A set is two or more images in one ZIP (or one folder, for loose files) that share a game name
once disk numbers, user disks, boot disks, alternates, tags and extensions are removed. Its
entry point is the set's only hard disk image, or else its lowest numbered disk. The entry
point's startup profile gains `setDisks`, the content IDs of the set's other images, and the
research entry its name matches gains the entry point's content ID, so the library shows one
row with the catalog title. Edits keep the hand-formatted JSON layout of both inputs.

Pull the cache with: adb exec-out run-as com.loxifi.kairo98 cat files/library-v2.bin > library-v2.bin
"""

import argparse
import json
import re
import struct
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_review_catalog import lookup_name  # noqa: E402

CACHE_MAGIC = 0x4B4C4942
CACHE_VERSION = 2
FLOPPY = re.compile(r"\.(?:fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf)$", re.I)
HARD_DISK = re.compile(r"\.hdi$", re.I)
DISK_NUMBER = re.compile(r"\((?:disk|disc|fd)\s*0*(\d+)[^)]*\)", re.I)
ROLE = re.compile(r"\((?:user(?:\s*disk)?|boot\s*disk|(?:system|program|data|sampling|scenario|event)\s*disk)\)", re.I)
ALTERNATE = re.compile(r"\[alt\s*\d*]", re.I)


def read_cache(path):
    data = Path(path).read_bytes()
    position = 0

    def take(fmt):
        nonlocal position
        value = struct.unpack_from(fmt, data, position)[0]
        position += struct.calcsize(fmt)
        return value

    def text():
        nonlocal position
        size = take(">i")
        value = data[position:position + size].decode("utf-8")
        position += size
        return value

    def optional():
        return text() if take(">?") else None

    if take(">I") != CACHE_MAGIC or take(">i") != CACHE_VERSION:
        raise ValueError(f"{path} is not a library-v{CACHE_VERSION} cache")
    text()
    text()
    entries = []
    for _ in range(take(">i")):
        entry = {"id": text(), "uri": text(), "path": text(), "zipEntry": optional()}
        for _ in range(4):
            take(">q")
        entry["contentId"] = optional()
        entry["error"] = optional()
        entries.append(entry)
    return entries


def image_name(entry):
    return (entry["zipEntry"] or entry["path"]).replace("\\", "/").rsplit("/", 1)[-1]


def game_key(name):
    return lookup_name(ROLE.sub("", ALTERNATE.sub("", name)))


def find_sets(entries):
    groups = defaultdict(list)
    for entry in entries:
        if entry["error"] or not entry["contentId"]:
            continue
        name = image_name(entry)
        if not (FLOPPY.search(name) or HARD_DISK.search(name)):
            continue
        source = entry["uri"] if entry["zipEntry"] else entry["path"].rsplit("/", 1)[0]
        groups[(source, game_key(name))].append(entry)
    sets = []
    for (source, key), members in sorted(groups.items()):
        names = [image_name(member) for member in members]
        if len(members) < 2 or not any(DISK_NUMBER.search(n) or ROLE.search(n) for n in names):
            continue
        hard_disks = [m for m in members if HARD_DISK.search(image_name(m))]
        if len(hard_disks) > 1:
            raise ValueError(f"{source}: more than one hard disk image in set {key}")
        if hard_disks:
            entry_point = hard_disks[0]
        else:
            numbered = [(int(DISK_NUMBER.search(image_name(m)).group(1)), m) for m in members
                        if DISK_NUMBER.search(image_name(m)) and not ALTERNATE.search(image_name(m))]
            if not numbered:
                raise ValueError(f"{source}: set {key} has no numbered disk")
            first = min(number for number, _ in numbered)
            candidates = [m for number, m in numbered if number == first]
            if len(candidates) != 1:
                raise ValueError(f"{source}: set {key} has more than one disk {first}")
            entry_point = candidates[0]
        disks = sorted({m["contentId"] for m in members} - {entry_point["contentId"]})
        if disks:
            sets.append({"source": source, "key": key, "entry": entry_point, "disks": disks})
    return sets


def research_keys(matches):
    names = defaultdict(set)
    for item in matches["entries"]:
        if item["platform"] != "pc98":
            continue
        key = f'pc98:{item["databaseId"]}'
        for candidate in (item["title"], *item.get("aliases", []),
                          *(group.rsplit(":", 1)[-1] for group in item.get("sourceGroups", []))):
            normalized = lookup_name(candidate)
            if len(normalized) >= 4:
                names[normalized].add(key)
    return {name: next(iter(keys)) for name, keys in names.items() if len(keys) == 1}


def display_title(name):
    title = re.sub(r"\[[^]]*]", "", ROLE.sub("", DISK_NUMBER.sub("", name)))
    title = re.sub(r"\.[A-Za-z0-9]+$", "", title)
    return re.sub(r"\s+", " ", title).strip(" -")


def add_research_id(text, database_id, content_id):
    """Adds a content ID to one research entry's contentIds array in place."""
    anchor = re.search(r'\n(\s*)"platform": "pc98",\n\s*"databaseId": "%s",' % re.escape(database_id), text)
    if anchor is None:
        raise ValueError(f"Research entry pc98:{database_id} not found")
    field = re.compile(r'\n(\s*)"contentIds": \[(.*?)\]', re.S).search(text, anchor.end())
    ids = json.loads("[" + field.group(2) + "]")
    if content_id in ids:
        return text
    ids.append(content_id)
    indent = field.group(1)
    body = "".join(f"\n{indent}  {json.dumps(value)}," for value in ids).rstrip(",")
    return text[:field.start()] + f'\n{indent}"contentIds": [{body}\n{indent}]' + text[field.end():]


def set_profile(text, content_id, disks, title):
    """Adds or replaces setDisks (and a title for an uncataloged game) on one profile."""
    listing = ", ".join(json.dumps(value) for value in disks)
    existing = re.search(r'\n    %s: \{' % re.escape(json.dumps(content_id)), text)
    if existing is None:
        fields = ([f'"title": {json.dumps(title, ensure_ascii=False)}'] if title else []) + \
                 [f'"setDisks": [{listing}]']
        record = ",\n".join(f"      {field}" for field in fields)
        close = text.rindex("\n  }\n}")
        return text[:close] + f',\n    {json.dumps(content_id)}: {{\n{record}\n    }}' + text[close:]
    start = existing.end()
    end = text.index("\n    }", start)
    replaced = re.compile(r'\n      "setDisks": \[[^]]*\]').search(text, start, end)
    if replaced:
        return text[:replaced.start()] + f'\n      "setDisks": [{listing}]' + text[replaced.end():]
    return text[:start] + f'\n      "setDisks": [{listing}],' + text[start:]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("cache", help="library-v2.bin pulled from a device")
    parser.add_argument("--write", action="store_true", help="edit the catalog inputs")
    args = parser.parse_args()
    matches_path = ROOT / "catalog/research/matches-v1.json"
    profiles_path = ROOT / "catalog/startup-profiles-v1.json"
    matches_text = matches_path.read_text(encoding="utf-8")
    profiles_text = profiles_path.read_text(encoding="utf-8")
    names = research_keys(json.loads(matches_text))
    profiles = json.loads(profiles_text)["games"]
    unreviewed = []
    for found in find_sets(read_cache(args.cache)):
        entry = found["entry"]
        name = image_name(entry)
        title = display_title(name)
        research = names.get(lookup_name(name)) or names.get(found["key"])
        if research is None:
            # A supplementary set such as "Dangel (Special Disk)" belongs to its base game's
            # entry, and keeps its own title.
            base = title
            while research is None and re.search(r"\s*\([^()]*\)$", base):
                base = re.sub(r"\s*\([^()]*\)$", "", base)
                research = names.get(lookup_name(base))
        else:
            title = None
        if research:
            matches_text = add_research_id(matches_text, research.split(":", 1)[1], entry["contentId"])
        else:
            unreviewed.append(name)
        if "title" in profiles.get(entry["contentId"], {}):
            title = None
        profiles_text = set_profile(profiles_text, entry["contentId"], found["disks"], title)
        print(f'{name}  ->  {research or "no research entry"}'
              f'{", title " + repr(title) if title else ""}, hides {len(found["disks"])}')
    if unreviewed:
        print("Not in the reviewed research catalog, so not adult-reviewed:", *unreviewed, sep="\n  ")
    json.loads(matches_text)
    json.loads(profiles_text)
    if args.write:
        matches_path.write_text(matches_text, encoding="utf-8", newline="\n")
        profiles_path.write_text(profiles_text, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
