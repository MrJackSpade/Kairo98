#!/usr/bin/env python3
"""Import reviewed local art into a generated catalog and Android assets."""

import argparse
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image, UnidentifiedImageError

from build_catalog import CONTENT_ID, compact, require, valid_image_url

MAX_SOURCE_BYTES = 32 * 1024 * 1024
MAX_PIXELS = 16_000_000
MAX_OUTPUT_BYTES = 2 * 1024 * 1024


def import_art(source_path, assets_root):
    assets_root = assets_root.resolve()
    assets_root.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".art-import-", dir=assets_root) as temporary:
        return _import_art(source_path, assets_root, Path(temporary))


def _import_art(source_path, assets_root, staging):
    source_path = source_path.resolve()
    source = json.loads(source_path.read_text(encoding="utf-8"))
    require(source.get("schemaVersion") == 1 and isinstance(source.get("assets"), list),
            "unsupported art manifest")
    catalog = assets_root / "catalog"
    catalog_manifest = json.loads((catalog / "manifest-v1.json").read_text(encoding="utf-8"))
    require(catalog_manifest.get("schemaVersion") == 1, "unsupported catalog manifest")
    shards = {}
    provenance = []
    by_game = {}
    art_dir = assets_root / "art"
    previous_manifest = art_dir / "provenance-v1.json"
    if previous_manifest.is_file():
        previous = json.loads(previous_manifest.read_text(encoding="utf-8"))
        require(previous.get("schemaVersion") == 1 and isinstance(previous.get("assets"), list),
                "unsupported previous art provenance")
        for old in previous["assets"]:
            require(isinstance(old, dict) and isinstance(old.get("contentId"), str) and
                    CONTENT_ID.fullmatch(old["contentId"]) and old.get("kind") in ("boxArt", "preview") and
                    isinstance(old.get("asset"), str) and old["asset"].startswith("art/"),
                    "invalid previous art provenance")
            prefix = old["contentId"].split(":", 1)[1][:2]
            if prefix not in catalog_manifest["shards"]:
                continue
            if prefix not in shards:
                shards[prefix] = json.loads((catalog / "shards" / f"{prefix}.json").read_text(encoding="utf-8"))
            game = shards[prefix]["games"].get(old["contentId"])
            if isinstance(game, dict):
                artwork = game.get("artwork", {})
                if artwork.get(old["kind"]) == old["asset"]:
                    del artwork[old["kind"]]
                    artwork.pop("boxArtUrl" if old["kind"] == "boxArt" else "previewUrl", None)
                    if not artwork:
                        game.pop("artwork", None)
    elif art_dir.is_dir():
        require(not any(art_dir.glob("*.webp")), "existing art has no provenance; review before import")
    for record in source["assets"]:
        require(isinstance(record, dict), "art record must be an object")
        content_id = record.get("contentId")
        kind = record.get("kind")
        require(isinstance(content_id, str) and CONTENT_ID.fullmatch(content_id), "invalid art content ID")
        require(kind in ("boxArt", "preview"), "invalid art kind")
        require(record.get("freeAndPaidRedistribution") is True and
                record.get("derivativesAllowed") is True, "art permission does not cover both builds and resizing")
        require(all(isinstance(record.get(key), str) and record[key].strip() for key in
                    ("creator", "source", "license", "permissionEvidence", "sha256", "file")),
                "incomplete art provenance")
        require(len(record["sha256"]) == 64, "invalid source digest")
        source_image_url = record.get("sourceImageUrl")
        require(source_image_url is None or valid_image_url(source_image_url),
                "invalid source image URL")
        relative = Path(record["file"])
        require(not relative.is_absolute() and ".." not in relative.parts, "unsafe source path")
        image_path = (source_path.parent / relative).resolve()
        require(image_path.is_relative_to(source_path.parent), "source escapes manifest folder")
        require(image_path.is_file() and image_path.stat().st_size <= MAX_SOURCE_BYTES, "art source missing or oversized")
        raw = image_path.read_bytes()
        require(hashlib.sha256(raw).hexdigest() == record["sha256"].lower(), "art source digest mismatch")
        prefix = content_id.split(":", 1)[1][:2]
        require(prefix in catalog_manifest["shards"], "art game is missing from catalog")
        if prefix not in shards:
            shards[prefix] = json.loads((catalog / "shards" / f"{prefix}.json").read_text(encoding="utf-8"))
        game = shards[prefix]["games"].get(content_id)
        require(isinstance(game, dict), "art game is missing from catalog shard")
        require((content_id, kind) not in by_game, "duplicate art kind for game")
        try:
            with Image.open(image_path) as original:
                require(original.width * original.height <= MAX_PIXELS, "art dimensions too large")
                image = original.convert("RGB")
                image.thumbnail((960, 360), Image.Resampling.LANCZOS)
                import io
                output = io.BytesIO()
                image.save(output, format="WEBP", quality=75, method=6)
                encoded = output.getvalue()
        except (UnidentifiedImageError, OSError) as error:
            raise ValueError(f"unreadable art: {image_path}") from error
        require(len(encoded) <= MAX_OUTPUT_BYTES, "compressed art too large")
        digest = hashlib.sha256(encoded).hexdigest()
        asset_path = f"art/{digest}.webp"
        (staging / f"{digest}.webp").write_bytes(encoded)
        game.setdefault("artwork", {})[kind] = asset_path
        if source_image_url:
            game["artwork"]["boxArtUrl" if kind == "boxArt" else "previewUrl"] = source_image_url
        by_game[content_id, kind] = asset_path
        provenance.append({key: record[key] for key in
                           ("contentId", "kind", "creator", "source", "license", "permissionEvidence", "sha256")}
                          | {"asset": asset_path, "assetSha256": digest}
                          | ({"sourceImageUrl": source_image_url} if source_image_url else {}))
    staged_shards = staging / "shards"
    staged_shards.mkdir()
    for prefix, shard in shards.items():
        (staged_shards / f"{prefix}.json").write_bytes(compact(shard))
    packaged_bytes = sum(path.stat().st_size for path in staging.glob("*.webp"))
    coverage = {"catalogGames": catalog_manifest["games"],
                "gamesWithArt": len({game_id for game_id, _ in by_game}),
                "boxArt": sum(kind == "boxArt" for _, kind in by_game),
                "previews": sum(kind == "preview" for _, kind in by_game),
                "packagedImageBytes": packaged_bytes}
    staged_provenance = staging / "provenance-v1.json"
    staged_provenance.write_bytes(compact({"schemaVersion": 1,
        "coverage": coverage,
        "assets": sorted(provenance, key=lambda x: (x["contentId"], x["kind"]))}))
    art_dir.mkdir(parents=True, exist_ok=True)
    for staged_image in staging.glob("*.webp"):
        staged_image.replace(art_dir / staged_image.name)
    for staged_shard in staged_shards.glob("*.json"):
        staged_shard.replace(catalog / "shards" / staged_shard.name)
    staged_provenance.replace(art_dir / "provenance-v1.json")
    approved = {Path(path).name for path in by_game.values()}
    for previous_image in art_dir.glob("*.webp"):
        if previous_image.name not in approved:
            previous_image.unlink()
    return coverage


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("assets", type=Path, help="Android assets root with generated catalog")
    args = parser.parse_args()
    coverage = import_art(args.source, args.assets)
    print(f"{coverage['gamesWithArt']}/{coverage['catalogGames']} catalog games with art, "
          f"{coverage['boxArt']} box images, {coverage['previews']} previews, "
          f"{coverage['packagedImageBytes']} packaged image bytes")


if __name__ == "__main__":
    main()
