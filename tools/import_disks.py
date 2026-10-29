#!/usr/bin/env python3
"""Record disk content IDs and multi-disk sets in the catalog inputs.

`library CACHE` reads a device library cache. `neokobe INDEX BACKLOG` reads a
`catalog_workbench.py scan` of the extracted NeoKobe PC-98 tree and the match backlog, so every
disk in a game's inventory group joins that game's research entry.

A set is two or more images in one source (a ZIP, a NeoKobe release folder, or a folder of loose
files) that share a game name once disk numbers, user disks, boot disks, alternates, tags and
extensions are removed. Its entry point is the set's only hard disk image, or else its lowest
numbered disk. The entry point's startup profile gains `setDisks`, the content IDs of the set's
other images, so the library shows one row with the catalog title. A hard disk whose set holds a
floppy labelled as its boot disk ("(Boot Disk)") gains that floppy as its `bootFloppy` medium, so
the app mounts the pair by content ID and never by name. Edits keep the hand-formatted
JSON layout of both inputs.

Pull a library cache with: adb exec-out run-as com.loxifi.kairo98 cat files/library-v2.bin > library-v2.bin
(read by shared/tools/library_cache.py, in the layout the app writes)
Scan the NeoKobe tree with: python tools/catalog_workbench.py scan <PC-98>/Untranslated/NeoKobe-2017 INDEX
"""

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "shared/tools"))
from catalog_index import lookup_name  # noqa: E402
import library_cache  # noqa: E402
from research_additions import name_matched  # noqa: E402

FLOPPY = re.compile(r"\.(?:fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf)$", re.I)
CD = re.compile(r"\.ccd$", re.I)
HARD_DISK = re.compile(r"\.hdi$", re.I)
# A disk label names a disk of a set: "(Disk 2)", "(Disk B - Red)", "(System disk)", "(System disk #2)".
LABEL = re.compile(r"\(([^()]*?)\b(?:disk|disc|fd)\b\s*#?0*(\d+|[a-z]\b)?[^()]*\)", re.I)
# Roles a set starts from, most likely first. A plain numbered, lettered or unlabeled disk ranks
# after them, and a disk with any other role after that.
ENTRY_ROLES = ("boot", "system", "master program", "program", "master", "game", "opening", "install")
TAG = re.compile(r"\[([^]]*)]")
ALTERNATE = re.compile(r"alt\s*(\d*)", re.I)
UNMODIFIED = re.compile(r"original|uncracked", re.I)
# The same disk is often dumped in several formats; the set starts from the first of them.
FORMATS = (".hdi", ".hdm", ".d88", ".fdi", ".nfd", ".fdd", ".88d", ".d98", ".98d", ".dcp", ".dcu", ".xdf", ".ccd")


def library_disks(entries):
    """Disks of a library cache; loose files are grouped by folder, ZIP members by ZIP."""
    for entry in entries:
        if entry["error"] or not entry["contentId"]:
            continue
        name = (entry["zipEntry"] or entry["path"]).replace("\\", "/").rsplit("/", 1)[-1]
        source = entry["uri"] if entry["zipEntry"] else entry["path"].rsplit("/", 1)[0]
        yield {"contentId": entry["contentId"], "name": name, "source": source}


def neokobe_disks(index, backlog):
    """Disks of a scanned NeoKobe tree, each with its release folder and inventory group.

    The tree holds each publisher ZIP extracted to a folder of the same name, and each game
    ZIP inside it extracted to a folder named after it, except ZIPs kept as they were."""
    variants = {}
    for group in backlog["groups"]:
        if group["platform"] != "pc98" or not group.get("source", "").endswith(".zip"):
            continue
        root = group["source"][:-4]
        for variant in group["variants"]:
            variants[f"{root}/{variant}"] = group["groupId"]
            if variant.lower().endswith(".zip"):
                variants[f"{root}/{variant[:-4]}"] = group["groupId"]
    for archive in index["archives"]:
        parts = archive["path"].split("/")
        source = next(("/".join(parts[:n]) for n in range(len(parts), 0, -1)
                       if "/".join(parts[:n]) in variants), None)
        if source is None:
            raise ValueError(f"{archive['path']} is in no inventory group")
        for disk in archive["disks"]:
            name = (disk["entry"] or archive["path"]).rsplit("/", 1)[-1]
            yield {"contentId": disk["contentId"], "name": name, "source": source,
                   "group": variants[source]}


def game_key(name):
    # A label's game number keeps "(#1 System disk)" and "(#2 System disk)" in separate sets.
    return lookup_name(LABEL.sub(lambda label: " ".join(re.findall(r"#\d+", label.group(1))), name))


def entry_rank(name):
    """Orders the disks a set could start from; None for a user disk, which it never starts from.

    Hard disks come first, then disks by role and number. Among copies of one disk, an untagged
    copy comes before one tagged after its label, such as "[installed]", then alternates by
    number, then unmodified originals, which are often copy protected; formats break ties."""
    extension = name[name.rfind("."):].casefold()
    # "Dangel (Special Disk) (Disk 1)" is disk 1 of the special disk set.
    label = next(reversed(list(LABEL.finditer(name))), None)
    role = re.sub(r"\s+", " ", label.group(1)).strip().casefold() if label else ""
    if "user" in role.split():
        return None
    position = len(ENTRY_ROLES) if not role else next(
        (i for i, entry in enumerate(ENTRY_ROLES) if role.endswith(entry)), len(ENTRY_ROLES) + 1)
    order = (label.group(2) or "") if label else ""
    if not order:
        # "(1-Jikanme disk)" is numbered by its role.
        order = next(iter(re.findall(r"(?<!#)\b(\d+)", role)), "")
    order = int(order) if order.isdigit() else ord(order.casefold()) - ord("a") + 1 if order else 0
    tags = TAG.findall(name, label.end() if label else 0)
    alternate = next((ALTERNATE.fullmatch(tag.strip()) for tag in tags
                      if ALTERNATE.fullmatch(tag.strip())), None)
    copy = (3 if any(UNMODIFIED.search(tag) for tag in tags) else 2 if alternate else 1 if tags else 0,
            int(alternate.group(1) or 0) if alternate else 0)
    return (0 if extension == ".hdi" else 1, position, order, copy, FORMATS.index(extension))


def find_sets(disks):
    """Returns the sets among the disks, and the image groups whose entry point is ambiguous."""
    groups = defaultdict(list)
    for disk in disks:
        if FLOPPY.search(disk["name"]) or HARD_DISK.search(disk["name"]):
            groups[(disk["source"], game_key(disk["name"]))].append(disk)
    sets, ambiguous = [], []
    for (source, key), members in sorted(groups.items()):
        if len(members) < 2 or not any(LABEL.search(member["name"]) for member in members):
            continue
        ranked = [(entry_rank(m["name"]), m) for m in members if entry_rank(m["name"]) is not None]
        best = min((rank for rank, _ in ranked), default=None)
        candidates = [m for rank, m in ranked if rank == best]
        if len(candidates) != 1:
            ambiguous.append(f"{source}: {key} has {len(candidates)} possible entry points")
            continue
        entry_point = candidates[0]
        disks = sorted({m["contentId"] for m in members} - {entry_point["contentId"]})
        boot = None
        if HARD_DISK.search(entry_point["name"]):
            # The boot floppy a hard disk starts with; its copies in other formats rank after it.
            floppies = [(rank, m) for rank, m in ranked
                        if FLOPPY.search(m["name"]) and rank[1] == ENTRY_ROLES.index("boot")]
            first = min((rank for rank, _ in floppies), default=None)
            boots = [m for rank, m in floppies if rank == first]
            if len(boots) > 1:
                ambiguous.append(f"{source}: {key} has {len(boots)} possible boot floppies")
            elif boots:
                boot = boots[0]["contentId"]
        if disks:
            sets.append({"source": source, "key": key, "entry": entry_point, "disks": disks, "boot": boot})
    return sets, ambiguous


def release_kind(folder):
    """A NeoKobe release folder's role for CDs: the disc, a disk made to run it, or a hard disk."""
    if re.search(r"\[CD]", folder):
        return "cd"
    if re.search(r"\[CD floppy]", folder) or re.search(r"\(CD version", folder, re.I):
        return "partner"
    if re.search(r"\[HD]", folder):
        return "hd"
    return "other"


def release_qualifier(folder, game):
    """What tells a release apart within its game, without the CD or media naming."""
    rest = folder[len(game):] if folder.startswith(game) else folder
    rest = re.sub(r"\[[^]]*]", "", re.sub(r"\(CD version[^)]*\)", "", rest, flags=re.I))
    return lookup_name(rest)


def find_cd_links(disks, sets):
    """Pairs each disk that runs a CD with that CD, within one inventory group.

    Partner releases are "[CD floppy]" boot disks and "(CD version)" installs, or a game's hard
    disk releases when it has only CD and hard disk releases. A partner takes the CD release
    with its qualifier, or the game's one CD release. Each disk of a partner that starts a set
    or belongs to none needs the release's first disc."""
    hidden = {content_id for found in sets for content_id in found["disks"]}
    releases = defaultdict(lambda: defaultdict(list))
    for disk in disks:
        game, folder = disk["source"].split("/")[-2:]
        releases[disk["group"]][(game, folder)].append(disk)
    links = []
    for group, by_release in sorted(releases.items()):
        kinds = {key: release_kind(key[1]) for key in by_release}
        cds = {key: sorted((d for d in by_release[key] if CD.search(d["name"])),
                           key=lambda d: (entry_rank(d["name"]), d["name"]))
               for key, kind in kinds.items() if kind == "cd"}
        cds = {key: discs for key, discs in cds.items() if discs}
        if not cds:
            continue
        partners = [key for key, kind in kinds.items() if kind == "partner"]
        if not partners and set(kinds.values()) == {"cd", "hd"}:
            partners = [key for key, kind in kinds.items() if kind == "hd"]
        for key in sorted(partners):
            matching = [cd for cd in cds if release_qualifier(cd[1], cd[0]) == release_qualifier(key[1], key[0])]
            release = matching[0] if len(matching) == 1 else next(iter(cds)) if len(cds) == 1 else None
            if release is None:
                continue
            disc = cds[release][0]["contentId"]
            for disk in by_release[key]:
                if not CD.search(disk["name"]) and disk["contentId"] not in hidden:
                    links.append({"target": disk["contentId"], "cd": disc, "group": group})
    return links


def research_keys(matches):
    names = defaultdict(set)
    for item in matches["entries"]:
        if item["platform"] != "pc98" or not name_matched(item):
            continue
        key = f'pc98:{item["databaseId"]}'
        for candidate in (item["title"], *item.get("aliases", []),
                          *(group.rsplit(":", 1)[-1] for group in item.get("sourceGroups", []))):
            normalized = lookup_name(candidate)
            if len(normalized) >= 4:
                names[normalized].add(key)
    return {name: next(iter(keys)) for name, keys in names.items() if len(keys) == 1}


def display_title(name):
    title = TAG.sub("", LABEL.sub("", name))
    title = re.sub(r"\.[A-Za-z0-9]+$", "", title)
    return re.sub(r"\s+", " ", title).strip(" -")


def add_research_ids(text, database_id, content_ids):
    """Adds content IDs to one research entry's contentIds array in place."""
    anchor = re.search(r'\n(\s*)"platform": "pc98",\n\s*"databaseId": "%s",' % re.escape(database_id), text)
    if anchor is None:
        raise ValueError(f"Research entry pc98:{database_id} not found")
    field = re.compile(r'\n(\s*)"contentIds": \[(.*?)\]', re.S).search(text, anchor.end())
    ids = json.loads("[" + field.group(2) + "]")
    added = sorted(set(content_ids) - set(ids))
    if not added:
        return text
    ids += added
    indent = field.group(1)
    body = "".join(f"\n{indent}  {json.dumps(value)}," for value in ids).rstrip(",")
    return text[:field.start()] + f'\n{indent}"contentIds": [{body}\n{indent}]' + text[field.end():]


def set_profile(text, content_id, disks, title):
    """Adds setDisks (and a title for an uncataloged game) to one profile. Existing setDisks are
    kept, since releases can share an entry point, as a translation can share disk 1."""
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
    replaced = re.compile(r'\n      "setDisks": \[([^]]*)\]').search(text, start, end)
    if replaced:
        listing = ", ".join(json.dumps(value) for value in
                            sorted(set(disks) | set(json.loads(f"[{replaced.group(1)}]"))))
        return text[:replaced.start()] + f'\n      "setDisks": [{listing}]' + text[replaced.end():]
    return text[:start] + f'\n      "setDisks": [{listing}],' + text[start:]


def set_media(text, content_id, role, value):
    """Sets one media role on a profile, keeping its other roles."""
    existing = re.search(r'\n    %s: \{' % re.escape(json.dumps(content_id)), text)
    item = {"role": role, "contentId": value}
    if existing is None:
        close = text.rindex("\n  }\n}")
        return text[:close] + f',\n    {json.dumps(content_id)}: {{\n      "media": [{json.dumps(item)}]\n    }}' + text[close:]
    start = existing.end()
    end = text.index("\n    }", start)
    field = re.compile(r'\n      "media": (\[[^\n]*\])').search(text, start, end)
    if field:
        media = [entry for entry in json.loads(field.group(1)) if entry["role"] != role] + [item]
        return text[:field.start()] + f'\n      "media": {json.dumps(media)}' + text[field.end():]
    return text[:start] + f'\n      "media": [{json.dumps(item)}],' + text[start:]


def resolve_by_name(found, names):
    """Research entry and title for a library set: the entry its name matches, or, for a
    supplementary set such as "Dangel (Special Disk)", its base game's entry with its own title."""
    title = display_title(found["entry"]["name"])
    research = names.get(lookup_name(found["entry"]["name"])) or names.get(found["key"])
    if research:
        return research, None
    base = title
    while research is None and re.search(r"\s*\([^()]*\)$", base):
        base = re.sub(r"\s*\([^()]*\)$", "", base)
        research = names.get(lookup_name(base))
    return research, title


def import_library(cache, matches):
    """Research IDs and set profiles for the sets of a library cache."""
    names = research_keys(matches)
    research_ids, set_profiles, unreviewed = defaultdict(set), [], []
    sets, ambiguous = find_sets(library_disks(library_cache.read(cache)["entries"]))
    for found in sets:
        research, title = resolve_by_name(found, names)
        name = found["entry"]["name"]
        if research:
            research_ids[research].add(found["entry"]["contentId"])
        else:
            unreviewed.append(name)
        set_profiles.append((found, title))
        print(f'{name}  ->  {research or "no research entry"}'
              f'{", title " + repr(title) if title else ""}, hides {len(found["disks"])}')
    if unreviewed:
        print("Not in the reviewed research catalog, so not adult-reviewed:", *unreviewed, sep="\n  ")
    return research_ids, set_profiles, ambiguous, []


def import_neokobe(index, backlog, matches):
    """Research IDs for every disk of a linked inventory group, and set profiles for the sets
    whose entry point is linked. Unlinked groups have no reviewed title, so they are counted."""
    linked = {group: f'pc98:{item["databaseId"]}' for item in matches["entries"]
              if item["platform"] == "pc98" for group in item["sourceGroups"]}
    cataloged = {content_id: f'{item["platform"]}:{item["databaseId"]}'
                 for item in matches["entries"] for content_id in item["contentIds"]}
    disks = list(neokobe_disks(index, backlog))
    owners = defaultdict(set)
    for disk in disks:
        if disk["group"] in linked:
            owners[disk["contentId"]].add(linked[disk["group"]])
    for content_id, research in cataloged.items():
        if content_id in owners:
            owners[content_id].add(research)
    conflicts = {content_id: sorted(keys) for content_id, keys in owners.items() if len(keys) > 1}
    research_ids = defaultdict(set)
    for content_id, keys in owners.items():
        if content_id not in conflicts:
            research_ids[next(iter(keys))].add(content_id)
    sets, ambiguous = find_sets(disks)
    set_profiles = [(found, None) for found in sets
                    if found["entry"]["group"] in linked and found["entry"]["contentId"] not in conflicts]
    cd_links = [link for link in find_cd_links(disks, sets)
                if link["group"] in linked and link["target"] not in conflicts and link["cd"] not in conflicts]
    unlinked = {disk["group"] for disk in disks if disk["group"] not in linked}
    print(f"{len(disks)} disks: {sum(len(ids) for ids in research_ids.values())} IDs for "
          f"{len(research_ids)} research entries, {len(set_profiles)} sets, {len(cd_links)} disks that run a CD; "
          f"{sum(disk['group'] not in linked for disk in disks)} disks in {len(unlinked)} unlinked groups")
    for content_id, keys in sorted(conflicts.items()):
        print(f"Not added, disk is in several games: {content_id} {', '.join(keys)}")
    return research_ids, set_profiles, ambiguous, cd_links


def apply(matches_text, profiles_text, research_ids, set_profiles, cd_links=()):
    profiles = json.loads(profiles_text)["games"]
    for research, content_ids in sorted(research_ids.items()):
        matches_text = add_research_ids(matches_text, research.split(":", 1)[1], content_ids)
    entry_points = {found["entry"]["contentId"] for found, _ in set_profiles}
    for found, title in set_profiles:
        # A disk two releases share, such as a boot floppy, keeps its row while it starts a set.
        disks = sorted(set(found["disks"]) - entry_points)
        if not disks:
            continue
        if "title" in profiles.get(found["entry"]["contentId"], {}):
            title = None
        profiles_text = set_profile(profiles_text, found["entry"]["contentId"], disks, title)
    for found, _ in set_profiles:
        if found["boot"]:
            profiles_text = set_media(profiles_text, found["entry"]["contentId"], "bootFloppy", found["boot"])
    # A disc is mounted with the disk that runs it, and shows no row of its own beside it.
    for link in cd_links:
        profiles_text = set_media(profiles_text, link["target"], "cd", link["cd"])
        profiles_text = set_profile(profiles_text, link["target"], [link["cd"]], None)
    json.loads(matches_text)
    json.loads(profiles_text)
    return matches_text, profiles_text


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--write", action="store_true", help="edit the catalog inputs")
    sources = parser.add_subparsers(dest="source", required=True)
    sources.add_parser("library").add_argument("cache", help="library-v2.bin pulled from a device")
    neokobe = sources.add_parser("neokobe")
    neokobe.add_argument("index", help="catalog_workbench scan of the extracted NeoKobe tree")
    neokobe.add_argument("backlog", help="match backlog listing the inventory groups")
    args = parser.parse_args()
    matches_path = ROOT / "catalog/research/matches-v1.json"
    profiles_path = ROOT / "catalog/startup-profiles-v1.json"
    matches_text = matches_path.read_text(encoding="utf-8")
    profiles_text = profiles_path.read_text(encoding="utf-8")
    matches = json.loads(matches_text)
    load = lambda path: json.loads(Path(path).read_text(encoding="utf-8"))
    if args.source == "library":
        found = import_library(args.cache, matches)
    else:
        found = import_neokobe(load(args.index), load(args.backlog), matches)
    research_ids, set_profiles, ambiguous, cd_links = found
    if ambiguous:
        print("Not recorded as sets:", *ambiguous, sep="\n  ")
    matches_text, profiles_text = apply(matches_text, profiles_text, research_ids, set_profiles, cd_links)
    if args.write:
        matches_path.write_text(matches_text, encoding="utf-8", newline="\n")
        profiles_path.write_text(profiles_text, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
