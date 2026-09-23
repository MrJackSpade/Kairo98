#!/usr/bin/env python3
"""Prepare and validate source-linked, manually reviewed catalog research."""

import argparse
import json
from pathlib import Path


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def propose(backlog, snapshots, selected):
    by_group = {group["groupId"]: group for group in backlog["groups"]}
    entries = {}
    for group_id in selected:
        group = by_group[group_id]
        matches = group["exactCandidates"]
        if len(matches) != 1:
            raise ValueError(f"{group_id}: expected one exact candidate; inspect manually")
        platform = group["platform"]
        game_id = matches[0]["DatabaseID"]
        game = snapshots[platform][game_id]
        identity = f"{platform}:{game_id}"
        if identity not in entries:
            def first_image(category):
                return next((image["url"] for image in game["images"]
                             if image["type"] == category), "")
            entries[identity] = {"platform": platform, "databaseId": game_id,
                                 "title": game["Name"], "aliases": game["aliases"],
                                 "sourceGroups": [], "contentIds": [],
                                 "description": "", "pageUrl": "",
                                 "boxArt": {"url": first_image("Box - Front"), "review": "unreviewed"},
                                 "screenshot": {"url": first_image("Screenshot - Gameplay"),
                                                "review": "unreviewed"},
                                 "notes": ""}
        entries[identity]["sourceGroups"].append(group_id)
        entries[identity]["contentIds"] = sorted(set(entries[identity]["contentIds"]) |
                                                set(group.get("contentIds", [])))
    return {"schemaVersion": 1, "entries": sorted(entries.values(),
            key=lambda item: (item["platform"], item["title"].casefold(), item["databaseId"]))}


def validate(reviews, backlog, snapshots):
    if reviews.get("schemaVersion") != 1 or not isinstance(reviews.get("entries"), list):
        raise ValueError("invalid review catalog envelope")
    by_group = {group["groupId"]: group for group in backlog["groups"]}
    used_groups = set()
    used_games = set()
    for entry in reviews["entries"]:
        platform = entry["platform"]
        game_id = entry["databaseId"]
        game = snapshots[platform][game_id]
        identity = (platform, game_id)
        if identity in used_games:
            raise ValueError(f"duplicate reviewed game: {identity}")
        used_games.add(identity)
        if entry["title"] != game["Name"]:
            raise ValueError(f"{identity}: title differs from selected source")
        if not entry["pageUrl"].startswith("https://gamesdb.launchbox-app.com/games/details/"):
            raise ValueError(f"{identity}: missing direct source page link")
        if len(entry["description"].strip()) < 25:
            raise ValueError(f"{identity}: missing original description")
        if not entry["notes"].strip():
            raise ValueError(f"{identity}: missing manual identity and image notes")
        if not entry["sourceGroups"]:
            raise ValueError(f"{identity}: missing on-disk source group")
        expected_ids = set()
        for group_id in entry["sourceGroups"]:
            if group_id in used_groups:
                raise ValueError(f"source group reviewed twice: {group_id}")
            used_groups.add(group_id)
            group = by_group[group_id]
            if group["platform"] != platform:
                raise ValueError(f"{group_id}: platform mismatch")
            expected_ids.update(group.get("contentIds", []))
        if set(entry["contentIds"]) != expected_ids or len(entry["contentIds"]) != len(expected_ids):
            raise ValueError(f"{identity}: content IDs differ from reviewed source groups")
        for field, category in (("boxArt", "Box - Front"),
                                ("screenshot", "Screenshot - Gameplay")):
            image = entry[field]
            if set(image) != {"url", "review"}:
                raise ValueError(f"{identity}: invalid {field}")
            if image["review"] not in ("checked", "flagged", "unreviewed", "missing"):
                raise ValueError(f"{identity}: invalid {field} status")
            options = {item["url"] for item in game["images"] if item["type"] == category}
            if image["url"] and image["url"] not in options:
                raise ValueError(f"{identity}: {field} URL does not belong to selected game")
            if image["review"] == "checked" and not image["url"]:
                raise ValueError(f"{identity}: checked {field} needs image URL")
            if image["review"] == "missing" and image["url"]:
                raise ValueError(f"{identity}: missing {field} must have empty URL")
    return len(used_games), len(used_groups)


def coverage(backlog, reviews):
    for platform in ("pc88", "pc98"):
        groups = [item for item in backlog["groups"] if item["platform"] == platform]
        entries = [item for item in reviews["entries"] if item["platform"] == platform]
        linked_groups = {name for item in entries for name in item["sourceGroups"]}
        exact = sum(bool(item["exactCandidates"]) for item in groups)
        boxes = sum(item["boxArt"]["review"] == "checked" for item in entries)
        screens = sum(item["screenshot"]["review"] == "checked" for item in entries)
        flagged = [(item["title"], field) for item in entries for field in ("boxArt", "screenshot")
                   if item[field]["review"] == "flagged"]
        print(f"{platform}: {len(entries)} reviewed records / {len(linked_groups)} disk groups; "
              f"{exact}/{len(groups)} groups have exact LaunchBox candidates; "
              f"{boxes} checked boxes, {screens} checked screenshots")
        for title, field in flagged:
            print(f"  FLAGGED {title}: {field}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    for command in ("propose", "validate"):
        child = sub.add_parser(command)
        child.add_argument("backlog", type=Path)
        child.add_argument("pc88_snapshot", type=Path)
        child.add_argument("pc98_snapshot", type=Path)
        child.add_argument("input", type=Path, help="group IDs for propose, reviewed JSON for validate")
        if command == "propose":
            child.add_argument("output", type=Path)
    summary = sub.add_parser("coverage")
    summary.add_argument("backlog", type=Path)
    summary.add_argument("reviews", type=Path)
    args = parser.parse_args()
    if args.command == "coverage":
        coverage(load(args.backlog), load(args.reviews))
        return
    backlog = load(args.backlog)
    snapshots = {"pc88": load(args.pc88_snapshot), "pc98": load(args.pc98_snapshot)}
    if args.command == "propose":
        groups = [line.strip() for line in args.input.read_text(encoding="utf-8-sig").splitlines()
                  if line.strip() and not line.startswith("#")]
        result = propose(backlog, snapshots, groups)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Proposed {len(result['entries'])} unreviewed games")
    else:
        games, groups = validate(load(args.input), backlog, snapshots)
        print(f"Validated {games} manually reviewed games spanning {groups} on-disk groups")


if __name__ == "__main__":
    main()
