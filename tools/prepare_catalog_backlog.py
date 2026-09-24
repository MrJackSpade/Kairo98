#!/usr/bin/env python3
"""Build a LaunchBox match backlog from NeoKobe and translated inventories."""

import argparse
import json
import re
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


EPISODE = re.compile(r"\bEpisode\s*0*(\d+)\b", re.IGNORECASE)

# NeoKobe sometimes groups distinct volumes under one title folder. Keep any
# combined release or unnumbered extra in the parent group rather than
# attributing it to an individual volume.
NEOKOBE_SPLITS = {
    "pc88:Pro Yakyuu Fan (Telenet Japan)": [
        (r"\(Yousei Gibs\)", "yousei-gibs",
         "Pro Yakyuu Fan: Power Up Kit - Yousei Gips"),
    ],
    "pc88:Shiro to Kuro no Densetsu Series - Destruction (Soft Studio Wing)": [
        (r"\(Gekan\)", "gekan", "Destruction Gekan"),
        (r"\(Joukan\)", "joukan", "Destruction Joukan"),
    ],
    "pc98:AicSpirits.zip:Magical Girl Pretty Samy": [
        (r"\(First Part\)", "part-1", "Magical Girl Pretty Samy: First Part"),
        (r"\(Second Part\)", "part-2", "Magical Girl Pretty Samy: Second Part"),
    ],
    "pc98:Fairytale.zip:Sailor-fuku Bishoujo Zukan": [
        (rf"\(No\. {number}\)", f"vol-{number}",
         f"Sailor-fuku Bishoujo Zukan: Vol. {number}") for number in (1, 4, 6)
    ],
    "pc98:FairytaleXShitei.zip:Kounai Shasei": [
        (rf"\(Vol\. {number}\)", f"vol-{number}",
         f"Kounai Shasei Vol. {number}") for number in (1, 2, 3)
    ],
    "pc98:Compile.zip:Disc Station Vol. 10": [
        (r"\(Applesauce - Ano Ko to Natsumatsuri\)", "applesauce",
         "Applesauce: Ano Ko to Natsumatsuri"),
        (r"\(Nazo Puyo\)", "nazo-puyo", "Disc Station Vol. 10: Nazo Puyo"),
        (r"\(Rude Breaker\)", "rude-breaker", "Rude Breaker"),
        (r"\(Runner's High\)", "runners-high", "Runner's High"),
        (r"\(Usajan Gaiden - Ore ga Kirifuda!\)", "usajan-gaiden",
         "Usajan Gaiden: Ore ga Kirifuda!"),
    ],
}


def split_neokobe_group(group):
    """Separate known multi-release folders without duplicating archive paths."""
    if group["platform"] == "pc98" and group.get("source") == "doujinHomebrew.zip":
        return split_homebrew_creator(group)
    rules = NEOKOBE_SPLITS.get(group["groupId"])
    if not rules:
        return [group]
    if group.get("contentIds"):
        raise ValueError(f"cannot assign hashes to split variants: {group['groupId']}")
    buckets = defaultdict(list)
    remainder = []
    for variant in group["variants"]:
        matches = [(suffix, title) for pattern, suffix, title in rules
                   if re.search(pattern, Path(variant).name, re.IGNORECASE)]
        if len(matches) > 1:
            raise ValueError(f"variant matches multiple releases: {variant}")
        if matches:
            buckets[matches[0][0]].append(variant)
        else:
            remainder.append(variant)
    assigned = remainder + [variant for variants in buckets.values() for variant in variants]
    if sorted(assigned) != sorted(group["variants"]):
        raise ValueError(f"split loses archive paths: {group['groupId']}")
    result = [{**group, "variants": sorted(remainder)}] if remainder else []
    for _, suffix, title in rules:
        if buckets[suffix]:
            result.append({**group, "groupId": f"{group['groupId']}:{suffix}",
                           "titleHint": title, "variants": sorted(buckets[suffix])})
    return result


def split_homebrew_creator(group):
    """Use game titles inside homebrew ZIP names instead of creator folder names."""
    if group.get("contentIds"):
        raise ValueError(f"cannot assign hashes to split variants: {group['groupId']}")
    buckets = defaultdict(list)
    remainder = []
    for variant in group["variants"]:
        stem = re.sub(r"(?: \[[^]]+\])+$", "", Path(variant).stem)
        match = re.fullmatch(r"(.+?) \((.+)\)", stem)
        if not match:
            remainder.append(variant)
            continue
        creator, title = match.groups()
        normalize = lambda value: re.sub(r"[^a-z0-9]+", "", value.casefold())
        if normalize(creator) != normalize(group["titleHint"]):
            raise ValueError(f"homebrew creator differs from folder: {variant}")
        buckets[title].append(variant)
    result = [{**group, "variants": sorted(remainder)}] if remainder else []
    for title, variants in sorted(buckets.items()):
        result.append({**group, "groupId": f"{group['groupId']}:title:{title}",
                       "titleHint": title, "variants": sorted(variants)})
    if sorted(variant for part in result for variant in part["variants"]) != sorted(group["variants"]):
        raise ValueError(f"homebrew split loses archive paths: {group['groupId']}")
    return result


def translated_groups(group, archives):
    """Split episode HDIs while keeping a combined image in its own group."""
    sources = {source["path"] for source in group["sources"]}
    episode_ids = defaultdict(set)
    episode_paths = defaultdict(set)
    combined_ids = set()
    combined_paths = set()
    for path in sources:
        archive = archives.get(path)
        if not archive:
            continue
        for disk in archive.get("disks", []):
            entry = disk.get("entry") or path
            match = EPISODE.search(Path(entry).stem)
            if match:
                number = int(match.group(1))
                episode_ids[number].add(disk["contentId"])
                episode_paths[number].add(path)
            else:
                combined_ids.add(disk["contentId"])
                combined_paths.add(path)
    if len(episode_ids) < 2:
        return [{"platform": "pc98", "groupId": "translated:" + group["groupId"],
                 "titleHint": group["titleHint"], "source": "translations",
                 "variants": sorted(sources), "contentIds": group["contentIds"]}]
    all_ids = set(combined_ids).union(*episode_ids.values())
    if all_ids != set(group["contentIds"]):
        raise ValueError(f"episode split loses content IDs: {group['groupId']}")
    if sum(len(ids) for ids in episode_ids.values()) + len(combined_ids) != len(all_ids):
        raise ValueError(f"episode split assigns a content ID more than once: {group['groupId']}")
    result = []
    if combined_ids:
        result.append({"platform": "pc98", "groupId": "translated:" + group["groupId"],
                       "titleHint": group["titleHint"], "source": "translations",
                       "variants": sorted(combined_paths), "contentIds": sorted(combined_ids)})
    for number in sorted(episode_ids):
        result.append({"platform": "pc98",
                       "groupId": f"translated:{group['groupId']}:episode-{number:02d}",
                       "titleHint": f"{group['titleHint']} Episode {number:02d}",
                       "source": "translations", "variants": sorted(episode_paths[number]),
                       "contentIds": sorted(episode_ids[number])})
    return result


def prepare(inventory, translated, snapshots, translated_index=None):
    groups = [part for group in inventory["groups"] for part in split_neokobe_group(group)]
    archives = {item["path"]: item for item in (translated_index or {}).get("archives", [])}
    for item in translated["groups"]:
        groups.extend(translated_groups(item, archives))
    indexes = {platform: exact_index(games) for platform, games in snapshots.items()}
    backlog = []
    for group in groups:
        platform = group["platform"]
        ids = sorted(indexes[platform].get(key(group["titleHint"]), []))
        item = dict(group)
        item["exactCandidates"] = [candidate(game_id, snapshots[platform][game_id])
                                   for game_id in ids]
        backlog.append(item)
    return {"schemaVersion": 1, "groups": backlog}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("translated_queue", type=Path)
    parser.add_argument("pc88_snapshot", type=Path)
    parser.add_argument("pc98_snapshot", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--translated-index", type=Path,
                        help="hashed archive index for splitting multi-episode translated images")
    args = parser.parse_args()
    load = lambda path: json.loads(path.read_text(encoding="utf-8"))
    result = prepare(load(args.inventory), load(args.translated_queue),
                     {"pc88": load(args.pc88_snapshot), "pc98": load(args.pc98_snapshot)},
                     load(args.translated_index) if args.translated_index else None)
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
