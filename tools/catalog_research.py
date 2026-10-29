#!/usr/bin/env python3
"""Assemble Kairo98's catalog research into catalog/research/games-v1.json, which the shared
generator (shared/tools/app_catalog.py, with tools/catalog_config.py) builds the catalog from.

Usage: catalog_research.py [--check]

Kairo98's research is its own (catalog/research/README.md): matches-v1.json, one entry per game
(LaunchBox, VNDB, MobyGames and NeoKobe inventory games); descriptions-v1.json and each language's
descriptions-<language>-v1.json; display-text-v1.json, the reviewed titles, aliases and descriptions;
adult-content-v1.json, the adult review; artwork-index-v1.json, each game's reviewed images in
catalog/artwork; and catalog/startup-profiles-v1.json, each disk's own fields: its set's disks and
media, its own title or description, and its startup choices and disk swaps. A profile's disk that
no game lists becomes a game of its own. NeoKobe inventory games are recognized only by their disks,
never by file name. --check fails if games-v1.json differs from what the research gives.
"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "shared/tools"))
sys.path.insert(0, str(ROOT / "tools"))
import catalog_descriptions
from catalog_documents import compact, require
from catalog_languages import LANGUAGES
from research_additions import name_matched

RESEARCH = ROOT / "catalog/research"
OUTPUT = RESEARCH / "games-v1.json"
# A disk's startup automation, apart from the fields of its disk set.
AUTOMATION = ("startupChoices", "diskSwaps")


def reviewed_display(metadata, review, key):
    """A game's title, aliases and description as the display text review corrected them."""
    item = review.get(key)
    if item is None:
        return metadata
    require(metadata["title"] == item["sourceTitle"], f"Display review title no longer matches: {key}")
    fields = item["fields"]
    require(fields and not fields.keys() - {"title", "description", "aliases"}, f"Invalid display review fields: {key}")
    return {**metadata, **fields}


def controller(value):
    """A game's controls in the catalog's current form: its bindings are its default layer."""
    if "bindings" not in value:
        return value
    converted = {key: item for key, item in value.items() if key != "bindings"}
    converted["defaults"] = {"withoutSticks": value["bindings"]}
    return converted


def assemble():
    matches = json.loads((RESEARCH / "matches-v1.json").read_text(encoding="utf-8-sig"))["entries"]
    adult_review = json.loads((RESEARCH / "adult-content-v1.json").read_text(encoding="utf-8"))["games"]
    description_notes = json.loads((RESEARCH / "descriptions-v1.json").read_text(encoding="utf-8"))
    descriptions = description_notes["descriptions"]
    known = {f'{entry["platform"]}:{entry["databaseId"]}' for entry in matches}
    display_notes = json.loads((RESEARCH / "display-text-v1.json").read_text(encoding="utf-8"))
    require(display_notes.get("schemaVersion") == 1, "Unsupported display text review schema")
    display_review = display_notes["games"]
    require(not display_review.keys() - known, "Display text reviews without catalog matches")
    unknown = (descriptions.keys() | description_notes.get("additionalSources", {}).keys()) - known
    require(not unknown, f"Descriptions without catalog matches: {sorted(unknown)[:5]}")
    incomplete = [entry["title"] for entry in matches
                  if any(group.startswith("translated:") for group in entry.get("sourceGroups", []))
                  and not descriptions.get(f'{entry["platform"]}:{entry["databaseId"]}')]
    require(not incomplete, f"Translated games missing descriptions: {incomplete[:5]}")
    translations = {}
    for language in LANGUAGES:
        path = RESEARCH / f"descriptions-{language}-v1.json"
        if path.is_file():
            for key, text in catalog_descriptions.texts(path).items():
                translations.setdefault(key, {})[language] = text
    profiles = json.loads((ROOT / "catalog/startup-profiles-v1.json").read_text(encoding="utf-8"))["games"]
    artwork = json.loads((RESEARCH / "artwork-index-v1.json").read_text(encoding="utf-8"))

    games = {}
    for entry in sorted(matches, key=lambda item: (item["platform"], item["databaseId"])):
        key = f'{entry["platform"]}:{entry["databaseId"]}'
        source = {"title": entry["title"]}
        description = descriptions.get(key, entry.get("description"))
        if description:
            source["description"] = description
        if entry.get("aliases"):
            source["aliases"] = entry["aliases"]
        shown = reviewed_display(source, display_review, key)
        game = {"title": shown["title"]}
        if shown.get("aliases"):
            game["aliases"] = shown["aliases"]
        if shown.get("description"):
            game["description"] = shown["description"]
        if key in translations:
            require(shown.get("description"), f"Translations of a game without an English description: {key}")
            game["descriptions"] = translations.pop(key)
        if adult_review.get(key, {}).get("status") == "eroge":
            game["adult"] = True
        # A game whose reviewed images were all rejected has none.
        if artwork.get(key):
            game["artwork"] = artwork[key]
        for field in ("machine", "launch", "input"):
            if entry.get(field):
                game[field] = entry[field]
        if entry.get("controller"):
            game["controller"] = controller(entry["controller"])
        game["contentIds"] = entry["contentIds"]
        if entry.get("sourceGroups"):
            game["sourceGroups"] = entry["sourceGroups"]
        if not name_matched(entry):
            game["named"] = False
        else:
            # The names before the display review still recognize the game's files.
            names = [name for name in (entry["title"], *entry.get("aliases", []))
                     if name != game["title"] and name not in game.get("aliases", [])]
            if names:
                game["lookupNames"] = names
        sets, automation = {}, {}
        for disk in entry["contentIds"]:
            profile = profiles.pop(disk, None)
            if profile is None:
                continue
            own = {field: value for field, value in profile.items() if field not in AUTOMATION}
            if own:
                sets[disk] = own
            if any(field in profile for field in AUTOMATION):
                automation[disk] = {field: profile[field] for field in AUTOMATION if field in profile}
        if sets:
            game["diskSets"] = sets
        if automation:
            game["diskAutomation"] = automation
        games[key] = game
    require(not translations, f"Translations of games without catalog matches: {sorted(translations)[:5]}")
    # A profile's disk no game lists is a game of its own, known only by that disk.
    for disk, profile in sorted(profiles.items()):
        require("title" in profile, f"{disk}: a disk no game lists needs its own title")
        game = {"title": profile["title"], "contentIds": [disk], "named": False}
        if profile.get("description"):
            game["description"] = profile["description"]
        own = {field: value for field, value in profile.items() if field not in (*AUTOMATION, "title", "description")}
        if own:
            game["diskSets"] = {disk: own}
        if any(field in profile for field in AUTOMATION):
            game["diskAutomation"] = {disk: {field: profile[field] for field in AUTOMATION if field in profile}}
        games[f"pc98:disk-{disk.split(':', 1)[1][:16]}"] = game
    return {"schemaVersion": 1, "games": games}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    content = json.dumps(assemble(), ensure_ascii=False, indent=1, sort_keys=True).encode("utf-8") + b"\n"
    if args.check:
        require(OUTPUT.read_bytes() == content, f"Stale {OUTPUT.relative_to(ROOT)}: run tools/catalog_research.py")
    else:
        OUTPUT.write_bytes(content)
        print(f"{len(json.loads(content)['games'])} games")


if __name__ == "__main__":
    main()
