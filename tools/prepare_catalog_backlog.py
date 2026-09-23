#!/usr/bin/env python3
"""Build an unapproved review backlog from NeoKobe and translated inventories."""

import argparse
import json
from collections import defaultdict
from pathlib import Path

from catalog_workbench import key


def exact_index(games):
    names = defaultdict(set)
    for game_id, game in games.items():
        for name in [game["Name"], *game.get("aliases", [])]:
            if key(name):
                names[key(name)].add(game_id)
    return names


def candidate(game_id, game):
    counts = game["imageCounts"]
    return {"DatabaseID": game_id, "title": game["Name"],
            "hasOverview": bool(game["Overview"].strip()),
            "boxCount": counts.get("Box - Front", 0),
            "screenshotCount": counts.get("Screenshot - Gameplay", 0)}


def prepare(inventory, translated, snapshots):
    groups = list(inventory["groups"])
    groups += [{"platform": "pc98", "groupId": "translated:" + item["groupId"],
                "titleHint": item["titleHint"], "source": "translations",
                "variants": [source["path"] for source in item["sources"]],
                "contentIds": item["contentIds"]}
               for item in translated["groups"]]
    indexes = {platform: exact_index(games) for platform, games in snapshots.items()}
    backlog = []
    for group in groups:
        platform = group["platform"]
        ids = sorted(indexes[platform].get(key(group["titleHint"]), []))
        item = dict(group)
        item["exactCandidates"] = [candidate(game_id, snapshots[platform][game_id])
                                   for game_id in ids]
        item["review"] = "needs-review"
        backlog.append(item)
    return {"schemaVersion": 1, "groups": backlog}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("translated_queue", type=Path)
    parser.add_argument("pc88_snapshot", type=Path)
    parser.add_argument("pc98_snapshot", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    load = lambda path: json.loads(path.read_text(encoding="utf-8"))
    result = prepare(load(args.inventory), load(args.translated_queue),
                     {"pc88": load(args.pc88_snapshot), "pc98": load(args.pc98_snapshot)})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for platform in ("pc88", "pc98"):
        groups = [item for item in result["groups"] if item["platform"] == platform]
        exact = [item for item in groups if item["exactCandidates"]]
        rich = [item for item in exact if any(candidate["hasOverview"] and
                candidate["boxCount"] and candidate["screenshotCount"]
                for candidate in item["exactCandidates"])]
        print(f"{platform}: {len(groups)} groups, {len(exact)} exact title/alias candidates, "
              f"{len(rich)} candidates with overview + box + screenshot")


if __name__ == "__main__":
    main()
