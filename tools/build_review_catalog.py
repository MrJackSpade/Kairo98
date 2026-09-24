#!/usr/bin/env python3
"""Bundle the reviewed research catalog and its 360px artwork into Android assets."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import unicodedata
from pathlib import Path

from build_catalog import build, compact


def lookup_name(value):
    value = re.sub(r"\[[^]]*]", "", value)
    value = re.sub(r"\((?:disk|disc|fd)\s*\d+[^)]*\)", "", value, flags=re.I)
    value = re.sub(r"\.(?:zip|hdi|fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf)$", "", value, flags=re.I)
    return "".join(char.lower() for char in unicodedata.normalize("NFKC", value)
                   if char.isalnum())


def generate(matches_path, gallery, assets, art_assets, ffmpeg, quality):
    matches = json.loads(matches_path.read_text(encoding="utf-8-sig"))["entries"]
    gallery_entries = json.loads((gallery / "manifest.json").read_text(encoding="utf-8-sig"))["entries"]
    gallery_index = {(item["platform"], item["pageUrl"]): item for item in gallery_entries}
    art_dir = art_assets / "art" / "catalog"
    art_dir.mkdir(parents=True, exist_ok=True)
    source_games = []
    all_games = {}
    names = {}
    provenance = []
    total_bytes = 0
    copied = 0
    for number, entry in enumerate(sorted(matches, key=lambda item: (item["platform"], item["databaseId"])), 1):
        key = f'{entry["platform"]}:{entry["databaseId"]}'
        gallery_entry = gallery_index.get((entry["platform"], entry["pageUrl"]))
        if gallery_entry is None:
            raise ValueError(f"Missing gallery entry: {key}")
        metadata = {"title": entry["title"]}
        if entry.get("description"):
            metadata["description"] = entry["description"]
        if entry.get("aliases"):
            metadata["aliases"] = entry["aliases"]
        for field in ("machine", "launch", "controller"):
            if entry.get(field):
                metadata[field] = entry[field]
        artwork = {}
        for gallery_kind, field, url_field in (
                ("box", "boxArt", "boxArtUrl"),
                ("screenshot", "preview", "previewUrl")):
            image = gallery_entry.get("images", {}).get(gallery_kind)
            if image is None:
                continue
            if image["url"] != entry.get("screenshotUrl" if gallery_kind == "screenshot" else "boxArtUrl"):
                raise ValueError(f"Gallery URL differs from catalog: {key} {gallery_kind}")
            relative = Path(image["path"])
            if relative.is_absolute() or ".." in relative.parts or relative.suffix.lower() not in (".webp", ".png", ".jpg", ".jpeg"):
                raise ValueError(f"Unsafe gallery path: {relative}")
            original = gallery / relative
            if not original.is_file():
                raise FileNotFoundError(original)
            asset_relative = relative.with_suffix(".webp")
            destination = art_dir / asset_relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            if not destination.is_file():
                temporary = destination.with_suffix(".part")
                try:
                    subprocess.run([str(ffmpeg), "-hide_banner", "-loglevel", "error", "-y",
                                    "-i", str(original), "-frames:v", "1", "-threads", "1",
                                    "-c:v", "libwebp", "-compression_level", "6", "-quality",
                                    str(quality), "-f", "webp", str(temporary)], check=True)
                    if relative.suffix.lower() == ".webp" and temporary.stat().st_size >= original.stat().st_size:
                        shutil.copyfile(original, destination)
                    else:
                        temporary.replace(destination)
                finally:
                    temporary.unlink(missing_ok=True)
                copied += 1
            size = destination.stat().st_size
            total_bytes += size
            asset_path = "art/catalog/" + asset_relative.as_posix()
            artwork[field] = asset_path
            artwork[url_field] = image["url"]
            provenance.append({"game": key, "kind": gallery_kind, "sourceUrl": image["url"],
                               "asset": asset_path, "sha256": hashlib.sha256(destination.read_bytes()).hexdigest()})
        if artwork:
            metadata["artwork"] = artwork
        all_games[key] = metadata
        if entry.get("contentIds"):
            source_games.append({"contentIds": entry["contentIds"], **metadata})
        if entry["platform"] == "pc98":
            candidates = [entry["title"], *entry.get("aliases", []),
                          *(group.rsplit(":", 1)[-1] for group in entry.get("sourceGroups", []))]
            for candidate in candidates:
                normalized = lookup_name(candidate)
                if len(normalized) >= 4:
                    names.setdefault(normalized, set()).add(key)
        if number % 500 == 0:
            print(f"{number}/{len(matches)} games, {copied} new images", flush=True)

    unique_names = {name: next(iter(keys)) for name, keys in sorted(names.items()) if len(keys) == 1}
    source = {"schemaVersion": 1, "datasets": [{"id": "reviewed-research-2026-09-24",
        "provenance": {"source": "Kairo98 local match catalog and LaunchBox image cache",
                       "license": "User-approved review import; release rights audit pending",
                       "attribution": "LaunchBox Games Database contributors; Kairo98 original descriptions"},
        "games": source_games}]}
    manifest, shards = build(source)
    catalog = assets / "catalog"
    shards_dir = catalog / "shards"
    shards_dir.mkdir(parents=True, exist_ok=True)
    for prefix, games in shards.items():
        (shards_dir / f"{prefix}.json").write_bytes(compact({"schemaVersion": 1, "games": games}))
    for stale in shards_dir.glob("*.json"):
        if stale.stem not in shards:
            stale.unlink()
    (catalog / "manifest-v1.json").write_bytes(compact(manifest))
    (catalog / "name-index-v1.json").write_bytes(compact({"schemaVersion": 1,
        "games": all_games, "names": unique_names}))
    (art_assets / "art" / "catalog-provenance-v1.json").write_bytes(compact({"schemaVersion": 1,
        "quality": quality, "assets": provenance}))
    output_source = matches_path.parent.parent / "source-v1.json"
    output_source.write_bytes(compact(source))
    print(f"{len(source_games)} hashed games, {manifest['games']} content IDs, "
          f"{len(unique_names)} unique PC-98 names, {len(provenance)} images, "
          f"{total_bytes} art bytes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--matches", type=Path, default=Path("catalog/research/matches-v1.json"))
    parser.add_argument("--gallery", type=Path, default=Path(".downloads/catalog-height360"))
    parser.add_argument("--assets", type=Path, default=Path("app/src/main/assets"))
    parser.add_argument("--art-assets", type=Path, default=Path("app/src/withImages/assets"))
    parser.add_argument("--ffmpeg", type=Path, default=Path("C:/bin/ffmpeg.exe"))
    parser.add_argument("--quality", type=int, default=25)
    args = parser.parse_args()
    generate(args.matches, args.gallery, args.assets, args.art_assets, args.ffmpeg, args.quality)
