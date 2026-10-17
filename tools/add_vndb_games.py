#!/usr/bin/env python3
"""Add research entries for NeoKobe games that LaunchBox lacks, from the VNDB database dump.

A choices file lists each inventory group with the VNDB visual novel ("v123") or release
("r123") it is, and an original description. VNDB's own descriptions come from other sources
and are not covered by the dump's license, so none is copied. Each entry takes its title from the
romanized official title, aliases from the other titles, and its disks from the NeoKobe scan. The
adult review follows the official, complete PC-98 releases' has_ero flags, and an adult game joins
the core review's exclusions. Run tools/import_disks.py neokobe afterwards to record its sets.

Choices: [{"group": "pc98:Silkys.zip:Crescent", "vndb": "v8830", "description": "..."}]
"""

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from import_disks import neokobe_disks  # noqa: E402
from research_additions import ResearchInputs, entry_key  # noqa: E402

NULL = "\\N"


def rows(directory, table):
    header = (directory / f"{table}.header").read_text(encoding="utf-8").strip().split("\t")
    with (directory / table).open(encoding="utf-8") as source:
        for line in source:
            yield dict(zip(header, line.rstrip("\n").split("\t")))


def value(text):
    return None if text in (NULL, "") else text.replace("\\n", "\n")


def vndb_records(directory):
    """PC-98 releases with their titles and flags, and the visual novels they belong to."""
    pc98 = {row["id"] for row in rows(directory, "releases_platforms") if row["platform"] == "p98"}
    release_vns = defaultdict(set)
    for row in rows(directory, "releases_vn"):
        if row["id"] in pc98:
            release_vns[row["id"]].add(row["vid"])
    releases = {row["id"]: {**row, "vns": release_vns[row["id"]], "titles": []}
                for row in rows(directory, "releases") if row["id"] in pc98}
    for row in rows(directory, "releases_titles"):
        if row["id"] in releases:
            releases[row["id"]]["titles"].append(row)
    vn_ids = set().union(*release_vns.values())
    vns = {row["id"]: {**row, "titles": []} for row in rows(directory, "vn") if row["id"] in vn_ids}
    for row in rows(directory, "vn_titles"):
        if row["id"] in vns:
            vns[row["id"]]["titles"].append(row)
    return releases, vns


def entry_for(choice, releases, vns, disks):
    identity = choice["vndb"]
    if identity.startswith("r"):
        release = releases[identity]
        chosen = [release]
        titles, original = release["titles"], release["olang"]
        aliases = []
    else:
        vn = vns[identity]
        chosen = [release for release in releases.values() if identity in release["vns"]]
        titles = [row for row in vn["titles"] if row["official"] == "t"]
        original = vn["olang"]
        aliases = [alias for alias in (value(vn["alias"]) or "").split("\n") if alias]
    primary = next(row for row in titles if row["lang"] == original)
    title = value(primary["latin"]) or primary["title"]
    for row in titles:
        aliases += [text for text in (row["title"], value(row["latin"])) if text]
    aliases = list(dict.fromkeys(alias for alias in aliases if alias != title))
    official = [release for release in chosen
                if release["official"] == "t" and release["patch"] == "f"]
    if not official:
        raise ValueError(f"{identity} has no official complete PC-98 release")
    flags = {release["has_ero"] for release in official}
    if len(flags) != 1:
        raise ValueError(f"{identity}: its PC-98 releases disagree about adult content")
    group_disks = sorted({disk["contentId"] for disk in disks if disk["group"] == choice["group"]})
    if not group_disks:
        raise ValueError(f"{choice['group']} has no disks in the scan")
    entry = {"platform": "pc98", "databaseId": f"vndb-{identity}", "title": title, "aliases": aliases,
             "sourceGroups": [choice["group"]], "contentIds": group_disks,
             "description": choice["description"], "pageUrl": f"https://vndb.org/{identity}",
             "boxArtUrl": "", "screenshotUrl": ""}
    review = {"status": "eroge" if flags == {"t"} else "nonadult",
              "vndbIds": sorted(set().union(*(release["vns"] for release in official))),
              "releaseIds": sorted(release["id"] for release in official)}
    return entry, review


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("choices", type=Path)
    parser.add_argument("vndb", type=Path, help="the extracted dump's db directory")
    parser.add_argument("index", type=Path, help="catalog_workbench scan of the extracted NeoKobe tree")
    parser.add_argument("backlog", type=Path, help="match backlog listing the inventory groups")
    parser.add_argument("--write", action="store_true", help="edit the catalog inputs")
    args = parser.parse_args()
    load = lambda path: json.loads(path.read_text(encoding="utf-8"))
    research = ResearchInputs()
    releases, vns = vndb_records(args.vndb)
    disks = list(neokobe_disks(load(args.index), load(args.backlog)))
    for choice in load(args.choices):
        entry, review = entry_for(choice, releases, vns, disks)
        research.add(entry, review, "VNDB adult release" if review["status"] == "eroge" else None)
        print(f"{entry_key(entry)}  {entry['title']}  {review['status']}, {len(entry['contentIds'])} disks")
    if args.write:
        research.write()


if __name__ == "__main__":
    main()
