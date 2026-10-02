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
from artwork_references import compact as compact_artwork, expand as expand_artwork
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "shared/tools"))
from catalog_package import build_package, image_paths, filter_artwork


def lookup_name(value):
    value = re.sub(r"\[[^]]*]", "", value)
    value = re.sub(r"\((?:disk|disc|fd)\s*\d+[^)]*\)", "", value, flags=re.I)
    value = re.sub(r"\.(?:zip|hdi|fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf)$", "", value, flags=re.I)
    return "".join(char.lower() for char in unicodedata.normalize("NFKC", value)
                   if char.isalnum())


def reviewed_display(metadata, review, key):
    item = review.get(key)
    if item is None:
        return metadata
    if metadata["title"] != item["sourceTitle"]:
        raise ValueError(f"Display review title no longer matches: {key}")
    fields = item["fields"]
    if not fields or fields.keys() - {"title", "description", "aliases"}:
        raise ValueError(f"Invalid display review fields: {key}")
    # build() / validate_record() validate the final field values as usual.
    return {**metadata, **fields}


def generate(matches_path, gallery, assets, art_assets, ffmpeg, quality, reuse_art=False):
    """reuse_art keeps the committed artwork and provenance instead of re-encoding the
    gallery, for metadata-only changes on a machine without the gallery or ffmpeg."""
    matches = json.loads(matches_path.read_text(encoding="utf-8-sig"))["entries"]
    adult_review = json.loads((matches_path.parent / "adult-content-v1.json").read_text(encoding="utf-8"))["games"]
    descriptions_path = matches_path.parent / "descriptions-v1.json"
    description_notes = json.loads(descriptions_path.read_text(encoding="utf-8"))
    descriptions = description_notes["descriptions"]
    known_keys = {f'{entry["platform"]}:{entry["databaseId"]}' for entry in matches}
    display_notes = json.loads((matches_path.parent / "display-text-v1.json").read_text(encoding="utf-8"))
    if display_notes.get("schemaVersion") != 1:
        raise ValueError("Unsupported display text review schema")
    display_review = display_notes["games"]
    if display_review.keys() - known_keys:
        raise ValueError("Display text reviews without catalog matches")
    unknown = (descriptions.keys() | description_notes.get("additionalSources", {}).keys()) - known_keys
    if unknown:
        raise ValueError(f"Descriptions without catalog matches: {sorted(unknown)[:5]}")
    incomplete = [entry["title"] for entry in matches
        if any(group.startswith("translated:") for group in entry.get("sourceGroups", []))
        and not descriptions.get(f'{entry["platform"]}:{entry["databaseId"]}')]
    if incomplete:
        raise ValueError(f"Translated games missing descriptions: {incomplete[:5]}")
    profiles_path = matches_path.parent.parent / "startup-profiles-v1.json"
    profiles = json.loads(profiles_path.read_text(encoding="utf-8"))["games"] if profiles_path.is_file() else {}
    policy = json.loads((matches_path.parent.parent / "core-review-v1.json").read_text(encoding="utf-8"))
    excluded = set(policy["excluded"])
    if reuse_art:
        art_index = json.loads((matches_path.parent / "artwork-index-v1.json").read_text(encoding="utf-8"))
        committed = {key: {"artwork": value} for key, value in art_index.items()}
        gallery_index = {}
    else:
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
        if gallery_entry is None and not reuse_art:
            raise ValueError(f"Missing gallery entry: {key}")
        metadata = {"title": entry["title"]}
        description = descriptions.get(key, entry.get("description"))
        if description:
            metadata["description"] = description
        if entry.get("aliases"):
            metadata["aliases"] = entry["aliases"]
        metadata = reviewed_display(metadata, display_review, key)
        adult_status = adult_review.get(key, {}).get("status", "unreviewed")
        if adult_status == "eroge":
            metadata["heart"] = True
        for field in ("machine", "launch", "controller", "input"):
            if entry.get(field):
                metadata[field] = entry[field]
        artwork = dict(committed.get(key, {}).get("artwork", {})) if reuse_art else {}
        for gallery_kind, field, url_field in () if reuse_art else (
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
            metadata["artwork"] = compact_artwork(artwork)
        all_games[key] = metadata
        if entry.get("contentIds"):
            if any(content_id in profiles for content_id in entry["contentIds"]):
                for content_id in entry["contentIds"]:
                    source_games.append({"contentIds": [content_id], **metadata,
                                         **profiles.pop(content_id, {})})
            else:
                source_games.append({"contentIds": entry["contentIds"], **metadata})
        if entry["platform"] == "pc98":
            candidates = [entry["title"], *entry.get("aliases", []),
                          metadata["title"], *metadata.get("aliases", []),
                          *(group.rsplit(":", 1)[-1] for group in entry.get("sourceGroups", []))]
            for candidate in candidates:
                normalized = lookup_name(candidate)
                if len(normalized) >= 4:
                    names.setdefault(normalized, set()).add(key)
        if number % 500 == 0:
            print(f"{number}/{len(matches)} games, {copied} new images", flush=True)

    for content_id, profile in sorted(profiles.items()):
        source_games.append({"contentIds": [content_id], **profile})

    unique_names = {name: next(iter(keys)) for name, keys in sorted(names.items()) if len(keys) == 1}
    source = {"schemaVersion": 1, "datasets": [{"id": "reviewed-research-2026-09-24",
        "provenance": {"source": "Kairo98 local match catalog, LaunchBox image cache, and VNDB 2026-09-26 dump",
                       "license": "VNDB-derived metadata: ODbL 1.0; artwork redistribution rights audit pending",
                       "attribution": "LaunchBox Games Database contributors; VNDB contributors; Kairo98 original descriptions"},
        "games": source_games}]}
    # Separate every marked game, associated hashes and dependent media before generating indexes.
    for key, record in all_games.items():
        if record.get("heart"):
            assert key in excluded, f"Adult record missing from reviewed exclusions: {key}"
    excluded_ids = {content_id for entry in matches
                    if f'{entry["platform"]}:{entry["databaseId"]}' in excluded
                    for content_id in entry.get("contentIds", [])}
    from build_online_catalog import build_pack
    full = build_pack(source, {"schemaVersion": 1, "games": all_games, "names": unique_names})
    changed = True
    while changed:
        previous = set(excluded_ids)
        for content_id, record in full["games"].items():
            refs = {item["contentId"] for field in ("media", "diskSwaps") for item in record.get(field, [])}
            if content_id in excluded_ids: excluded_ids.update(refs)
            if refs & excluded_ids: excluded_ids.add(content_id)
        changed = previous != excluded_ids
    optional = {"schemaVersion": 1,
        "games": {key:value for key,value in full["games"].items() if key in excluded_ids},
        "nameIndex": {"schemaVersion": 1,
            "games": {key:value for key,value in all_games.items() if key in excluded},
            "names": {key:value for key,value in unique_names.items() if value in excluded}}}
    optional_dir = matches_path.parent.parent / "optional"
    optional_dir.mkdir(parents=True, exist_ok=True)
    (optional_dir / "data-v1.json").write_bytes(compact(optional))
    (matches_path.parent.parent / "excluded-ids-v1.json").write_bytes(compact({"schemaVersion": 1,
        "ids": sorted(excluded | excluded_ids), "artwork": sorted(image_paths(optional, expand_artwork))}))
    # Preserve the complete reviewed metadata and artwork for partition generation.
    (optional_dir / "full-v1.json").write_bytes(compact(full))
    approved = policy["approvedArtwork"]
    for dataset in source["datasets"]:
        kept = []
        for record in dataset["games"]:
            ids = record["contentIds"]
            if ids: kept.append({**record, "contentIds": ids})
        dataset["games"] = filter_artwork(kept, approved, expand_artwork, compact_artwork)
    all_games = filter_artwork(all_games,
                              approved, expand_artwork, compact_artwork)
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
    if not reuse_art:
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
    parser.add_argument("--assets", type=Path, default=Path("kairo98/src/main/assets"))
    parser.add_argument("--art-assets", type=Path, default=Path("kairo98/src/withImages/assets"))
    parser.add_argument("--ffmpeg", type=Path, default=Path("C:/bin/ffmpeg.exe"))
    parser.add_argument("--quality", type=int, default=25)
    parser.add_argument("--reuse-art", action="store_true",
                        help="keep committed artwork; for metadata changes without the gallery")
    args = parser.parse_args()
    generate(args.matches, args.gallery, args.assets, args.art_assets, args.ffmpeg, args.quality,
             args.reuse_art)
