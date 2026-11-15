#!/usr/bin/env python3
"""Write catalog/research/tags-v1.json: each Kairo98 game's tags (Kairo#132), which
catalog_research.py gives the game.

Usage: catalog_tags.py [--mobygames <dump directory>]

A tag list is shared research.py's game_tags: the game's year, its genres in LaunchBox's form
("Action; Platform") and its players ("Single Player", "Multiplayer", "Cooperative; Multiplayer").
LaunchBox games take them from .downloads/launchbox/pc98-snapshot.json (the year) and pc98-tags.json
(shared launchbox.py tags); MobyGames games from the dump's pc98-listing.json. Games of other sources
(VNDB, the NeoKobe inventory) have no such record and get no tags. The file is committed, so
catalog_research.py --check needs no downloads.
"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "shared/tools"))

import catalog_files
from catalog_documents import require
from research import game_tags

RESEARCH = ROOT / "catalog/research"
OUTPUT = RESEARCH / "tags-v1.json"


def launchbox_year(record):
    """A LaunchBox record's year: ReleaseYear, else the year of its ReleaseDate."""
    year = record.get("ReleaseYear") or record.get("ReleaseDate", "")[:4]
    return int(year) if year.isdigit() else None


def tags(matches, snapshot, launchbox_tags, listing):
    games = {}
    for entry in matches:
        key = f'{entry["platform"]}:{entry["databaseId"]}'
        if entry["pageUrl"].startswith("https://gamesdb.launchbox-app.com/"):
            game_id = entry["databaseId"]
            require(game_id in snapshot and game_id in launchbox_tags,
                    f"{key}: LaunchBox game {game_id} is not in pc98-snapshot.json and pc98-tags.json")
            found = game_tags(launchbox_year(snapshot[game_id]), launchbox_tags[game_id], None)
        elif entry["pageUrl"].startswith("https://www.mobygames.com/"):
            moby_id = int(entry["databaseId"].removeprefix("moby-"))
            require(moby_id in listing, f"{key}: MobyGames game {moby_id} is not in pc98-listing.json")
            found = game_tags(listing[moby_id].get("release_year"), None, listing[moby_id].get("genres", []))
        else:
            continue
        if found:
            games[key] = found
    return games


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mobygames", type=Path, default=ROOT / ".downloads/mobygames")
    args = parser.parse_args()
    matches = json.loads((RESEARCH / "matches-v1.json").read_text(encoding="utf-8-sig"))["entries"]
    downloads = ROOT / ".downloads/launchbox"
    snapshot = json.loads((downloads / "pc98-snapshot.json").read_text(encoding="utf-8"))
    launchbox_tags = json.loads((downloads / "pc98-tags.json").read_text(encoding="utf-8"))
    listing = {game["id"]: game for game in
               json.loads((args.mobygames / "pc98-listing.json").read_text(encoding="utf-8"))}
    games = tags(matches, snapshot, launchbox_tags, listing)
    content = json.dumps({"schemaVersion": 1, "games": games}, ensure_ascii=False, indent=1, sort_keys=True)
    catalog_files.write_bytes(OUTPUT, content.encode("utf-8") + b"\n")
    print(f"{len(games)} of {len(matches)} games tagged -> {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
