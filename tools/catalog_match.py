#!/usr/bin/env python3
"""Consolidate on-disk game groups with cached LaunchBox identity and media links."""

import argparse
import copy
import difflib
import json
import re
from collections import Counter
from pathlib import Path

from catalog_workbench import key


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def page_url(game_id):
    # These snapshot records resolve to different public IDs after LaunchBox
    # database changes; their URLs were checked against official pages or
    # official listing links on 2026-09-23.
    overrides = {
        "450780": "412248-battle-block-alfin",
        "453654": "418778-berserkers-front-gaiden-3",
        "454555": "420335-poy",
        "454556": "420336-bakutotsu-turb",
        "454557": "420337-roli-roli-rolling",
        "454559": "420339-seena-2",
        "454560": "420340-sengoku-turb",
        "454562": "420342-stardust-chaser",
        "454564": "420344-super-depth",
        "454566": "420346-super-depth-2-finalty",
        "454567": "420347-turb-3",
        "454569": "420349-twins",
        "454570": "420350-turb",
        "454571": "420351-turb-2",
        "454572": "420352-twins-2",
        "454574": "420354-bio-100-free-game-collection",
        "454575": "420355-bio-100-game-collection-part-2",
        "454683": "420463-camel-zoo",
        "454686": "420466-daisenryaku-ii-campaign-version",
        "454691": "420471-car-ii-grand-prix",
        "468902": "434684-abnormal-soldier",
        "470408": "436190-jewel-bem-hunter-lime-vol-02",
        "470410": "436192-jewel-bem-hunter-lime-vol03",
        "470413": "436195-jewel-bem-hunter-lime-vol-04",
        "470421": "436203-lightning-warrior-raidy",
        "475938": "441727-the-tower-of-zarbartz",
    }
    if game_id in overrides:
        return "https://gamesdb.launchbox-app.com/games/details/" + overrides[game_id]
    # The adjacent 453654-453669 records tentatively follow the offset
    # confirmed for Berserkers Front Gaiden 3 (453654). Official pages
    # sampled across the 454xxx-462xxx snapshot blocks resolve
    # at DatabaseID - 34220. Samples in the 465xxx-470xxx block resolve at
    # DatabaseID - 34218, except Jewel Bem Hunter Lime volumes 05-12
    # (470837-470844), which resolve at DatabaseID - 34216. Remaining records
    # in those blocks still need the user's final catalog check.
    snapshot_id = int(game_id)
    if 453654 <= snapshot_id <= 453669:
        public_id = snapshot_id - 34876
    elif 454000 <= snapshot_id < 463000:
        public_id = snapshot_id - 34220
    elif 470837 <= snapshot_id <= 470844:
        public_id = snapshot_id - 34216
    elif 465000 <= snapshot_id < 471000:
        public_id = snapshot_id - 34218
    else:
        public_id = snapshot_id - 50000
    if public_id <= 0:
        raise ValueError(f"no public page mapping for DatabaseID {game_id}")
    return f"https://gamesdb.launchbox-app.com/games/details/{public_id}"


BOX_TYPES = ("Box - Front", "Fanart - Box - Front")
SCREENSHOT_TYPES = ("Screenshot - Gameplay", "Screenshot - Game Title",
                    "Screenshot - Game Select")

# These folder names collide with different games in LaunchBox. Keep them out
# of subsequent automatic exact-title joins after the attribution audit.
EXACT_TITLE_COLLISIONS = {
    "pc88:Misty (Champion Soft)",        # LaunchBox Misty is Data West Vol. 1
    "pc88:Labyrinth (Doujin - Dream7)",  # LaunchBox entry is the Pack-In-Video game
    "pc88:Tsumeshougi (Apollo Technica)",  # LaunchBox entry credits Tsukumo
    "pc88:The Dragon Princess (Doujin - Run Tech)",  # LaunchBox entry credits Koei
    "pc88:G Senryaku (Doujin - Team-DS)",  # LaunchBox entry credits K2 Staff and Great
    "pc88:Koikoi (S. Miyoshi)",  # LaunchBox entry credits Tsukumo; source has extras only
    "pc88:Tetris (Doujin - Noripy)",  # LaunchBox entry credits Bullet-Proof Software
}


def first_image(game, categories):
    for category in categories:
        url = next((image["url"] for image in game["images"]
                    if image["type"] == category), "")
        if url:
            return url
    return ""


def new_entry(platform, game_id, game):
    return {
        "platform": platform, "databaseId": game_id,
        "title": game["Name"], "aliases": list(game["aliases"]),
        "sourceGroups": [], "contentIds": [], "description": "",
        "pageUrl": page_url(game_id),
        "boxArtUrl": first_image(game, BOX_TYPES),
        "screenshotUrl": first_image(game, SCREENSHOT_TYPES),
    }


def sorted_catalog(entries):
    return {"schemaVersion": 1, "entries": sorted(entries,
            key=lambda item: (item["platform"], item["title"].casefold(), item["databaseId"]))}


def add_disk_aliases(entries, by_group):
    """Keep inventory title spellings searchable for already linked games."""
    for entry in entries:
        entry["aliases"] = list(entry["aliases"])
        known = {key(name) for name in [entry["title"], *entry["aliases"]]}
        hints = sorted({by_group[group_id].get("titleHint", "").strip()
                        for group_id in entry["sourceGroups"]}, key=str.casefold)
        for hint in hints:
            normalized = key(hint)
            if (not normalized or normalized in known or hint.startswith("[") or
                    re.search(r"\.(?:hdi|zip|7z)$", hint, re.IGNORECASE)):
                continue
            entry["aliases"].append(hint)
            known.add(normalized)


def homebrew_attribution_matches(group, game):
    """Reject same-title collisions with a different credited creator."""
    if group.get("source") != "doujinHomebrew.zip":
        return True
    creator = group["groupId"].split(":", 3)[2]
    if creator in ("Dai-2-kai ASCII Entertainment Software Contest", "The Internet Contest Park"):
        return True
    clean = lambda name: re.sub(r"(?:software|soft)$", "",
                                re.sub(r"[^a-z0-9]+", "", name.casefold()))
    source = clean(creator)
    credits = [clean(game.get(field, "")) for field in ("Developer", "Publisher")]
    credits = [credit for credit in credits if credit]
    if not credits:
        return True
    return any(source == credit or
               (len(source) >= 5 and len(credit) >= 5 and
                (source in credit or credit in source or
                 difflib.SequenceMatcher(None, source, credit).ratio() >= .82))
               for credit in credits)


def consolidate(backlog, snapshots, previous):
    """Keep hand-selected matches; add unambiguous exact title/alias candidates."""
    by_group = {group["groupId"]: group for group in backlog["groups"]}
    entries = {}
    assigned = set()
    for old in previous["entries"]:
        identity = (old["platform"], old["databaseId"])
        if identity in entries:
            raise ValueError(f"duplicate existing game: {identity}")
        entry = {field: old[field] for field in
                 ("platform", "databaseId", "title", "aliases", "sourceGroups",
                  "contentIds", "description", "pageUrl")}
        entry["boxArtUrl"] = old.get("boxArtUrl", old.get("boxArt", {}).get("url", ""))
        entry["screenshotUrl"] = old.get("screenshotUrl", old.get("screenshot", {}).get("url", ""))
        if old["databaseId"] in snapshots[old["platform"]]:
            game = snapshots[old["platform"]][old["databaseId"]]
            entry["boxArtUrl"] = entry["boxArtUrl"] or first_image(game, BOX_TYPES)
            entry["screenshotUrl"] = entry["screenshotUrl"] or first_image(game, SCREENSHOT_TYPES)
        entries[identity] = entry
        for group_id in entry["sourceGroups"]:
            if group_id not in by_group or group_id in assigned:
                raise ValueError(f"unknown or duplicate existing group: {group_id}")
            assigned.add(group_id)

    for group in backlog["groups"]:
        if group["groupId"] in assigned:
            continue
        if group["groupId"] in EXACT_TITLE_COLLISIONS:
            continue
        platform = group["platform"]
        candidates = group["exactCandidates"]
        # If a title has several exact matches, use only an already selected
        # identity when that resolves the ambiguity. Otherwise leave it out.
        selected = [candidate for candidate in candidates
                    if (platform, candidate["DatabaseID"]) in entries]
        if len(selected) == 1:
            game_id = selected[0]["DatabaseID"]
        elif len(candidates) == 1:
            game_id = candidates[0]["DatabaseID"]
        else:
            continue
        identity = (platform, game_id)
        game = snapshots[platform][game_id]
        if not homebrew_attribution_matches(group, game):
            continue
        if identity not in entries:
            entries[identity] = new_entry(platform, game_id, game)
        entries[identity]["sourceGroups"].append(group["groupId"])
        entries[identity]["contentIds"] = sorted(set(entries[identity]["contentIds"])
                                              | set(group.get("contentIds", [])))
        assigned.add(group["groupId"])
    add_disk_aliases(entries.values(), by_group)
    result = sorted_catalog(entries.values())
    for entry in result["entries"]:
        entry["contentIds"] = sorted({content_id for group_id in entry["sourceGroups"]
                                      for content_id in by_group[group_id].get("contentIds", [])})
    validate(result, backlog, snapshots)
    return result


def choose(catalog, backlog, snapshots, choices):
    """Apply explicit group-to-LaunchBox ID corrections to one catalog."""
    by_group = {group["groupId"]: group for group in backlog["groups"]}
    entries = {(entry["platform"], entry["databaseId"]): dict(entry)
               for entry in catalog["entries"]}
    for group_id, game_id in choices.items():
        group = by_group[group_id]
        platform = group["platform"]
        identity = (platform, game_id)
        game = snapshots[platform][game_id]
        for entry in entries.values():
            if group_id in entry["sourceGroups"]:
                entry["sourceGroups"] = [name for name in entry["sourceGroups"] if name != group_id]
        if identity not in entries:
            entries[identity] = new_entry(platform, game_id, game)
        entries[identity]["sourceGroups"].append(group_id)
    retained = [entry for entry in entries.values() if entry["sourceGroups"]]
    add_disk_aliases(retained, by_group)
    result = sorted_catalog(retained)
    for entry in result["entries"]:
        entry["sourceGroups"] = sorted(set(entry["sourceGroups"]))
        entry["contentIds"] = sorted({content_id for group_id in entry["sourceGroups"]
                                      for content_id in by_group[group_id].get("contentIds", [])})
    validate(result, backlog, snapshots)
    return result


def add_live(catalog, backlog, snapshots, records):
    """Add direct LaunchBox page matches missing from the local snapshot."""
    by_group = {group["groupId"]: group for group in backlog["groups"]}
    entries = {(entry["platform"], entry["databaseId"]): dict(entry)
               for entry in catalog["entries"]}
    for record in records:
        group_id = record["groupId"]
        group = by_group[group_id]
        page = record["pageUrl"]
        match = re.fullmatch(r"https://gamesdb\.launchbox-app\.com/games/details/(\d+)(?:-[a-z0-9-]+)?", page)
        if not match:
            raise ValueError(f"invalid LaunchBox detail page: {page}")
        game_id = f"live:{match.group(1)}"
        identity = (group["platform"], game_id)
        for entry in entries.values():
            if group_id in entry["sourceGroups"]:
                entry["sourceGroups"] = [name for name in entry["sourceGroups"] if name != group_id]
        if identity not in entries:
            entries[identity] = {
                "platform": group["platform"], "databaseId": game_id,
                "title": record["title"], "aliases": record.get("aliases", []),
                "sourceGroups": [], "contentIds": [], "description": "",
                "pageUrl": page, "boxArtUrl": record.get("boxArtUrl", ""),
                "screenshotUrl": record.get("screenshotUrl", ""),
            }
        entries[identity]["sourceGroups"].append(group_id)
    retained = [entry for entry in entries.values() if entry["sourceGroups"]]
    add_disk_aliases(retained, by_group)
    result = sorted_catalog(retained)
    for entry in result["entries"]:
        entry["sourceGroups"] = sorted(set(entry["sourceGroups"]))
        entry["contentIds"] = sorted({content_id for group_id in entry["sourceGroups"]
                                      for content_id in by_group[group_id].get("contentIds", [])})
    validate(result, backlog, snapshots)
    return result


def sync_page_urls(catalog, snapshots, listings):
    """Use exact canonical URLs from the complete public platform listings."""
    by_name = {}
    for platform, listing in listings.items():
        if listing["platform"] != platform or len(listing["entries"]) != len(snapshots[platform]):
            raise ValueError(f"incomplete or incorrect {platform} listing")
        names = {}
        for item in listing["entries"]:
            name = item["title"].strip().casefold()
            if name in names:
                raise ValueError(f"duplicate {platform} listing title: {name}")
            names[name] = item["pageUrl"]
        snapshot_names = {game["Name"].strip().casefold()
                          for game in snapshots[platform].values()}
        if set(names) != snapshot_names:
            raise ValueError(f"{platform} listing titles differ from snapshot")
        by_name[platform] = names
    result = copy.deepcopy(catalog)
    for entry in result["entries"]:
        game_id = entry["databaseId"]
        if game_id.startswith("live:"):
            continue
        title = snapshots[entry["platform"]][game_id]["Name"].strip().casefold()
        entry["pageUrl"] = by_name[entry["platform"]][title]
    return result


def unlink_groups(catalog, backlog, group_ids):
    """Remove disk groups proven to be different games from a source record."""
    by_group = {group["groupId"]: group for group in backlog["groups"]}
    removed = set(group_ids)
    if len(removed) != len(group_ids) or not removed <= set(by_group):
        raise ValueError("duplicate or unknown group to unlink")
    assigned = {group_id for entry in catalog["entries"]
                for group_id in entry["sourceGroups"]}
    if not removed <= assigned:
        raise ValueError("group to unlink is not currently matched")
    entries = copy.deepcopy(catalog["entries"])
    for entry in entries:
        entry["sourceGroups"] = [group_id for group_id in entry["sourceGroups"]
                                 if group_id not in removed]
        entry["contentIds"] = sorted({content_id for group_id in entry["sourceGroups"]
                                      for content_id in by_group[group_id].get("contentIds", [])})
    return sorted_catalog([entry for entry in entries if entry["sourceGroups"]])


def validate(catalog, backlog, snapshots):
    if catalog.get("schemaVersion") != 1 or not isinstance(catalog.get("entries"), list):
        raise ValueError("invalid match catalog envelope")
    by_group = {group["groupId"]: group for group in backlog["groups"]}
    used_groups = set()
    used_games = set()
    required = {"platform", "databaseId", "title", "aliases", "sourceGroups",
                "contentIds", "description", "pageUrl", "boxArtUrl", "screenshotUrl"}
    for entry in catalog["entries"]:
        if set(entry) != required:
            raise ValueError(f"unexpected catalog fields: {set(entry) ^ required}")
        platform, game_id = entry["platform"], entry["databaseId"]
        identity = (platform, game_id)
        if identity in used_games:
            raise ValueError(f"duplicate game: {identity}")
        used_games.add(identity)
        live_id = re.fullmatch(r"live:(\d+)", game_id)
        game = None if live_id else snapshots[platform][game_id]
        if game and entry["title"] != game["Name"]:
            raise ValueError(f"title differs from source: {identity}")
        if live_id and (not entry["title"].strip() or not re.fullmatch(
                rf"https://gamesdb\.launchbox-app\.com/games/details/{live_id.group(1)}(?:-[a-z0-9-]+)?",
                entry["pageUrl"])):
            raise ValueError(f"invalid live page identity: {identity}")
        if not entry["pageUrl"].startswith("https://gamesdb.launchbox-app.com/games/details/"):
            raise ValueError(f"missing source page link: {identity}")
        if game:
            expected_page_id = re.search(r"/games/details/(\d+)", page_url(game_id)).group(1)
            actual_page_id = re.search(r"/games/details/(\d+)", entry["pageUrl"])
            if not actual_page_id or actual_page_id.group(1) != expected_page_id:
                raise ValueError(f"source page identity differs: {identity}")
        if not entry["sourceGroups"]:
            raise ValueError(f"missing source group: {identity}")
        expected_ids = set()
        for group_id in entry["sourceGroups"]:
            if group_id in used_groups:
                raise ValueError(f"source group assigned twice: {group_id}")
            used_groups.add(group_id)
            group = by_group[group_id]
            if group["platform"] != platform:
                raise ValueError(f"platform mismatch: {group_id}")
            expected_ids.update(group.get("contentIds", []))
        if set(entry["contentIds"]) != expected_ids or len(entry["contentIds"]) != len(expected_ids):
            raise ValueError(f"content IDs differ from source groups: {identity}")
        for field, categories in (("boxArtUrl", BOX_TYPES),
                                  ("screenshotUrl", SCREENSHOT_TYPES)):
            if game:
                options = {image["url"] for image in game["images"]
                           if image["type"] in categories}
                if entry[field] and entry[field] not in options:
                    raise ValueError(f"{field} URL differs from source: {identity}")
            elif entry[field] and not entry[field].startswith("https://images.launchbox-app.com/"):
                raise ValueError(f"invalid live image URL: {identity}")
    return len(used_games), len(used_groups)


def coverage(backlog, catalog):
    linked = {group_id for entry in catalog["entries"] for group_id in entry["sourceGroups"]}
    for platform in ("pc88", "pc98"):
        groups = [group for group in backlog["groups"] if group["platform"] == platform]
        entries = [entry for entry in catalog["entries"] if entry["platform"] == platform]
        counts = Counter(len(group["exactCandidates"]) for group in groups if group["groupId"] not in linked)
        print(f"{platform}: {len(entries)} matched games / "
              f"{sum(group['groupId'] in linked for group in groups)} of {len(groups)} disk groups; "
              f"{sum(bool(entry['boxArtUrl']) for entry in entries)} box URLs, "
              f"{sum(bool(entry['screenshotUrl']) for entry in entries)} screenshot URLs; "
              f"{counts[0]} without exact candidate, "
              f"{sum(count for n, count in counts.items() if n > 1)} ambiguous")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("consolidate", "choose", "add-live", "sync-pages", "unlink", "validate", "coverage"):
        cmd = sub.add_parser(name)
        cmd.add_argument("backlog", type=Path)
        if name != "coverage":
            cmd.add_argument("pc88_snapshot", type=Path)
            cmd.add_argument("pc98_snapshot", type=Path)
        cmd.add_argument("catalog", type=Path)
        if name == "choose":
            cmd.add_argument("choices", type=Path, help="JSON object of group IDs to LaunchBox DatabaseIDs")
        if name == "add-live":
            cmd.add_argument("records", type=Path, help="JSON list of direct LaunchBox page matches")
        if name == "sync-pages":
            cmd.add_argument("pc88_listing", type=Path)
            cmd.add_argument("pc98_listing", type=Path)
        if name == "unlink":
            cmd.add_argument("group_ids", type=Path, help="JSON list of disk group IDs to unlink")
        if name in ("consolidate", "choose", "add-live", "sync-pages", "unlink"):
            cmd.add_argument("output", type=Path)
    args = parser.parse_args()
    backlog = load(args.backlog)
    if args.command == "coverage":
        coverage(backlog, load(args.catalog))
        return
    snapshots = {"pc88": load(args.pc88_snapshot), "pc98": load(args.pc98_snapshot)}
    if args.command in ("consolidate", "choose", "add-live", "sync-pages", "unlink"):
        if args.command == "consolidate":
            result = consolidate(backlog, snapshots, load(args.catalog))
        elif args.command == "choose":
            result = choose(load(args.catalog), backlog, snapshots, load(args.choices))
        elif args.command == "sync-pages":
            listings = {"pc88": load(args.pc88_listing), "pc98": load(args.pc98_listing)}
            result = sync_page_urls(load(args.catalog), snapshots, listings)
        elif args.command == "unlink":
            result = unlink_groups(load(args.catalog), backlog, load(args.group_ids))
        else:
            result = add_live(load(args.catalog), backlog, snapshots, load(args.records))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_name(args.output.name + ".tmp")
        temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        temporary.replace(args.output)
    else:
        result = load(args.catalog)
    games, groups = validate(result, backlog, snapshots)
    print(f"Validated {games} matched games spanning {groups} on-disk groups")


if __name__ == "__main__":
    main()
