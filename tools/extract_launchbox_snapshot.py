#!/usr/bin/env python3
"""Stream only PC-88/PC-98 records from LaunchBox's Metadata.zip into ignored JSON."""

import argparse
import json
import zipfile
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree as ET

PLATFORMS = {"NEC PC-8801": "pc88", "NEC PC-9801": "pc98"}
FIELDS = ("Name", "Overview", "Developer", "Publisher", "ReleaseDate", "Platform")


def extract(source):
    result = {slug: {} for slug in PLATFORMS.values()}
    with zipfile.ZipFile(source) as archive, archive.open("Metadata.xml") as stream:
        iterator = ET.iterparse(stream, events=("start", "end"))
        _, root = next(iterator)
        for event, node in iterator:
            if event != "end":
                continue
            if node.tag == "Game":
                platform = node.findtext("Platform")
                game_id = node.findtext("DatabaseID")
                if platform in PLATFORMS and game_id:
                    game = {field: node.findtext(field) or "" for field in FIELDS}
                    game.update(DatabaseID=game_id, aliases=[], images=[])
                    result[PLATFORMS[platform]][game_id] = game
                root.clear()
            elif node.tag == "GameAlternateName":
                game_id = node.findtext("DatabaseID")
                name = node.findtext("AlternateName")
                for games in result.values():
                    if game_id in games and name:
                        games[game_id]["aliases"].append(name)
                root.clear()
            elif node.tag == "GameImage":
                game_id = node.findtext("DatabaseID")
                filename = node.findtext("FileName") or ""
                category = node.findtext("Type") or "Other"
                if filename and "/" not in filename and "\\" not in filename:
                    for games in result.values():
                        if game_id in games:
                            games[game_id]["images"].append({
                                "type": category,
                                "url": "https://images.launchbox-app.com/" + filename})
                root.clear()
    for games in result.values():
        for game in games.values():
            game["aliases"] = sorted(set(game["aliases"]))
            game["imageCounts"] = dict(Counter(image["type"] for image in game["images"]))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output_directory", type=Path)
    args = parser.parse_args()
    args.output_directory.mkdir(parents=True, exist_ok=True)
    for platform, games in extract(args.source).items():
        destination = args.output_directory / f"{platform}-snapshot.json"
        destination.write_text(json.dumps(games, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        print(f"{platform}: {len(games)} games -> {destination}")


if __name__ == "__main__":
    main()
