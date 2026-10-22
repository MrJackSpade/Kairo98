#!/usr/bin/env python3
"""Add research entries for NeoKobe games from a MobyGames dump made by shared/tools/mobygames.py.

A choices file names each inventory group's MobyGames game and gives an original description;
MobyGames text is reference only and none is copied. An optional "title" replaces MobyGames'
title, which then becomes an alias. Artwork is the game's PC-98 front cover, preferring a
Japanese release, and a screenshot from the middle of its play, each encoded at most 360 pixels
high into catalog/artwork and served from this repository. A game
MobyGames gives the Adult theme is reviewed as adult and leaves the core catalog; otherwise the
VNDB title review decides, as for every other entry. Run tools/import_disks.py neokobe afterwards.

Choices: [{"group": "pc98:Ving.zip:Life & Death", "moby": 109, "description": "..."}]
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from artwork_references import SELF_HOSTED_BASE  # noqa: E402
from audit_vndb_adult_content import review as vndb_review  # noqa: E402
from build_catalog import compact  # noqa: E402
from research_additions import ResearchInputs, entry_key  # noqa: E402

PC98 = 95
ARTWORK = ROOT / "catalog/artwork"
PROVENANCE = ARTWORK / "art/catalog-provenance-v1.json"
INDEX = ROOT / "catalog/research/artwork-index-v1.json"
NOT_GAMEPLAY = re.compile(r"title|menu|intro|opening|logo|credits|ending|staff|copyright|loading|rules|"
                          r"options|select|story|sign in|profile|start|ago", re.I)


def front_cover(covers):
    groups = sorted(covers.get("cover_groups", []), key=lambda group: "Japan" not in group["countries"])
    return next((cover["image"] for group in groups for cover in group["covers"]
                 if cover["scan_of"] == "Front Cover"), None)


def gameplay_screenshot(screenshots):
    """The middle screenshot captioned as play, since MobyGames lists them in the order they
    come up and play is in the middle; otherwise the middle one."""
    shots = screenshots.get("screenshots", [])
    play = [shot for shot in shots if not NOT_GAMEPLAY.search(shot.get("caption") or "")] or shots
    return play[len(play) // 2]["image"] if play else None


def entry_for(choice, game):
    aliases = [title["title"] for title in game.get("alternate_titles", [])]
    title = choice.get("title", game["title"])
    if title != game["title"]:
        aliases.insert(0, game["title"])
    group_name = choice["group"].rsplit(":", 1)[-1]
    aliases = list(dict.fromkeys(alias for alias in [*aliases, group_name] if alias != title))
    return {"platform": "pc98", "databaseId": f"moby-{game['game_id']}", "title": title,
            "aliases": aliases, "sourceGroups": [choice["group"]], "contentIds": [],
            "description": choice["description"], "pageUrl": game["moby_url"],
            "boxArtUrl": "", "screenshotUrl": ""}


def encode(source, destination, ffmpeg, quality):
    """The image at most 360 pixels high, as WebP; smaller images keep their size."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(ffmpeg), "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                    "-frames:v", "1", "-threads", "1", "-vf", "scale=-2:'min(360,ih)':flags=lanczos",
                    "-c:v", "libwebp", "-compression_level", "6", "-quality", str(quality),
                    "-f", "webp", str(destination)], check=True)


def artwork_for(key, game_id, dump, ffmpeg, quality):
    """The entry's artwork fields and provenance records, with each image encoded."""
    covers = json.loads((dump / "covers" / f"{game_id}-{PC98}.json").read_text(encoding="utf-8"))
    screenshots = json.loads((dump / "screenshots" / f"{game_id}-{PC98}.json").read_text(encoding="utf-8"))
    artwork, provenance = {}, []
    for field, kind, url in (("boxArt", "box", front_cover(covers)),
                             ("preview", "screenshot", gameplay_screenshot(screenshots))):
        if url is None:
            continue
        token = hashlib.sha256(url.encode("utf-8")).hexdigest()[:12]
        path = f"art/catalog/moby/{game_id}/{kind}-{token}.webp"
        encode(dump / "images" / urllib.parse.urlsplit(url).path.lstrip("/"), ARTWORK / path, ffmpeg, quality)
        artwork[field] = path
        artwork[field + "Url"] = SELF_HOSTED_BASE + path
        provenance.append({"asset": path, "game": key, "kind": kind,
                           "sha256": hashlib.sha256((ARTWORK / path).read_bytes()).hexdigest(),
                           "sourceUrl": url})
    return artwork, provenance


def adult_review(entry, game, vndb):
    """The VNDB title review, or an adult review when MobyGames gives the Adult theme."""
    if any(genre["genre_name"] == "Adult" for genre in game.get("genres", [])):
        return {"status": "eroge", "sourceUrl": game["moby_url"], "reason": "MobyGames lists the Adult theme"}
    return vndb_review([entry], vndb, None)["games"][entry_key(entry)]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("choices", type=Path)
    parser.add_argument("dump", type=Path, help="the shared/tools/mobygames.py output directory")
    parser.add_argument("vndb", type=Path, help="the extracted VNDB dump's db directory")
    parser.add_argument("--ffmpeg", type=Path, default=Path("C:/bin/ffmpeg.exe"))
    parser.add_argument("--quality", type=int, default=25)
    parser.add_argument("--write", action="store_true", help="edit the catalog inputs and write artwork")
    args = parser.parse_args()
    research = ResearchInputs()
    index = json.loads(INDEX.read_text(encoding="utf-8"))
    provenance = json.loads(PROVENANCE.read_text(encoding="utf-8"))
    for choice in json.loads(args.choices.read_text(encoding="utf-8")):
        game = json.loads((args.dump / "games" / f"{choice['moby']}.json").read_text(encoding="utf-8"))
        entry = entry_for(choice, game)
        key = entry_key(entry)
        review = adult_review(entry, game, args.vndb)
        research.add(entry, review, "MobyGames or VNDB adult game" if review["status"] == "eroge" else None)
        if args.write:
            artwork, records = artwork_for(key, choice["moby"], args.dump, args.ffmpeg, args.quality)
            if artwork:
                index[key] = artwork
            provenance["assets"] = [item for item in provenance["assets"] if item["game"] != key] + records
        print(f"{key}  {entry['title']}  {review['status']}")
    if args.write:
        research.write()
        INDEX.write_text(json.dumps(index, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
                         encoding="utf-8", newline="\n")
        PROVENANCE.write_bytes(compact(provenance))


if __name__ == "__main__":
    main()
