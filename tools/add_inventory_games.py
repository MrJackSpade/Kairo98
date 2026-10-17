#!/usr/bin/env python3
"""Catalog NeoKobe groups that no game database describes, under their inventory titles.

A choices file gives each group either a "title", which makes it a research entry of its own
named after the group, or a "game", the research entry it belongs to. A titled entry has no
description or artwork and is recognized only by its disks, never by file name, since its title
is a folder name. Its adult review is the VNDB title review every entry gets. Run
tools/import_disks.py neokobe afterwards.

Choices: [{"group": "pc98:Utility.zip:Assist Calc", "title": "Assist Calc"},
          {"group": "pc98:Compile.zip:Disc Station Vol. 10:applesauce", "game": "pc98:190306"}]
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from audit_vndb_adult_content import review as vndb_review  # noqa: E402
from research_additions import INVENTORY_PREFIX, ResearchInputs, entry_key  # noqa: E402


def entry_for(choice):
    identity = hashlib.sha256(choice["group"].encode("utf-8")).hexdigest()[:12]
    return {"platform": "pc98", "databaseId": INVENTORY_PREFIX + identity, "title": choice["title"],
            "aliases": [], "sourceGroups": [choice["group"]], "contentIds": [],
            "pageUrl": "", "boxArtUrl": "", "screenshotUrl": ""}


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("choices", type=Path)
    parser.add_argument("vndb", type=Path, help="the extracted VNDB dump's db directory")
    parser.add_argument("--write", action="store_true", help="edit the catalog inputs")
    args = parser.parse_args()
    research = ResearchInputs()
    choices = json.loads(args.choices.read_text(encoding="utf-8"))
    entries = [entry_for(choice) for choice in choices if "title" in choice]
    reviews = vndb_review(entries, args.vndb, None)["games"]
    for entry in entries:
        review = reviews[entry_key(entry)]
        research.add(entry, review, "VNDB adult release" if review["status"] == "eroge" else None)
    for choice in choices:
        if "game" in choice:
            research.link(choice["game"], choice["group"])
    counts = {status: sum(reviews[entry_key(entry)]["status"] == status for entry in entries)
              for status in ("eroge", "nonadult", "unreviewed")}
    print(f"{len(entries)} titled entries {counts}; {len(choices) - len(entries)} groups linked to existing entries")
    if args.write:
        research.write()


if __name__ == "__main__":
    main()
