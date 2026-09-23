#!/usr/bin/env python3
"""Validate source metadata and emit deterministic, content-ID-keyed Android shards."""

import argparse
import hashlib
import json
import re
from pathlib import Path

CONTENT_ID = re.compile(r"sha256-hdi-v1:[0-9a-f]{64}\Z")
ART_PATH = re.compile(r"art/(?!.*\.\.)[A-Za-z0-9_./-]{1,252}\Z")
FIELDS = {"title", "aliases", "artwork", "machine", "controller", "media", "launch"}
CONTROLLER_INPUT = re.compile(r"(?:button:[0-9]{1,3}|(?:axis|hat):[0-9]{1,2}:[+-])\Z")
CONTROLLER_ACTIONS = {"menu", "pause", "restart", "exit"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def valid_bindings(bindings):
    if not isinstance(bindings, list) or len(bindings) > 128:
        return False
    seen = set()
    for binding in bindings:
        if not isinstance(binding, dict) or set(binding) not in ({"input", "keys"}, {"input", "action"}):
            return False
        source = binding.get("input")
        if not isinstance(source, str) or not CONTROLLER_INPUT.fullmatch(source) or source in seen:
            return False
        seen.add(source)
        if "keys" in binding:
            keys = binding["keys"]
            if not isinstance(keys, list) or not 1 <= len(keys) <= 4:
                return False
            if any(type(key) is not int or not 0 <= key <= 127 for key in keys):
                return False
            if len(keys) != len(set(keys)):
                return False
        elif not isinstance(binding["action"], str) or binding["action"] not in CONTROLLER_ACTIONS:
            return False
    return True


def validate_record(record):
    require(isinstance(record, dict), "game must be an object")
    require(set(record) <= FIELDS | {"contentIds"}, "unknown game field")
    ids = record.get("contentIds")
    require(isinstance(ids, list) and ids and len(ids) <= 32, "contentIds must contain 1–32 hashes")
    require(all(isinstance(value, str) and CONTENT_ID.fullmatch(value) for value in ids), "invalid content ID")
    require(len(ids) == len(set(ids)), "duplicate content ID in one game")
    title = record.get("title")
    require(isinstance(title, str) and 0 < len(title.strip()) <= 256, "invalid title")
    aliases = record.get("aliases", [])
    require(isinstance(aliases, list) and len(aliases) <= 64 and
            all(isinstance(x, str) and 0 < len(x.strip()) <= 256 for x in aliases), "invalid aliases")
    artwork = record.get("artwork", {})
    require(isinstance(artwork, dict) and set(artwork) <= {"boxArt", "preview"} and
            all(isinstance(x, str) and ART_PATH.fullmatch(x) for x in artwork.values()), "invalid artwork")
    machine = record.get("machine", {})
    require(isinstance(machine, dict) and set(machine) <= {"baseClockTenthsMHz"} and
            type(machine.get("baseClockTenthsMHz", 25)) is int and
            machine.get("baseClockTenthsMHz", 25) in (20, 25), "invalid machine")
    controller = record.get("controller", {})
    require(isinstance(controller, dict) and set(controller) <= {"profile", "bindings"} and
            isinstance(controller.get("profile", ""), str) and
            ("profile" not in controller or 1 <= len(controller["profile"]) <= 64) and
            valid_bindings(controller.get("bindings", [])), "invalid controller")
    media = record.get("media", [])
    require(isinstance(media, list) and len(media) <= 16 and
            all(isinstance(x, dict) and isinstance(x.get("role"), str) and
                re.fullmatch(r"[A-Za-z0-9_-]{1,32}", x["role"]) and
                isinstance(x.get("contentId"), str) and CONTENT_ID.fullmatch(x["contentId"])
                for x in media), "invalid media")
    launch = record.get("launch")
    if launch is not None:
        require(isinstance(launch, dict) and launch.get("type") == "guestCommand" and
                launch.get("ready", "dosPrompt") == "dosPrompt" and
                isinstance(launch.get("text"), str) and
                0 < len(launch["text"]) <= 128 and
                all(c.isascii() and (c.isalnum() or c in " \\/._:-") for c in launch["text"]) and
                type(launch.get("timeoutMs", 30000)) is int and
                1000 <= launch.get("timeoutMs", 30000) <= 120000, "invalid launch")
    return {key: value for key, value in record.items() if key != "contentIds"}


def compact(obj):
    return (json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8")


def build(source):
    require(source.get("schemaVersion") == 1, "unsupported source schema")
    datasets = source.get("datasets")
    require(isinstance(datasets, list), "datasets must be an array")
    by_id = {}
    provenance = []
    dataset_ids = set()
    for dataset in datasets:
        require(isinstance(dataset, dict), "dataset must be an object")
        dataset_id = dataset.get("id")
        info = dataset.get("provenance")
        require(isinstance(dataset_id, str) and dataset_id and dataset_id not in dataset_ids,
                "invalid or duplicate dataset ID")
        require(isinstance(info, dict) and all(isinstance(info.get(k), str) and info[k]
                                               for k in ("source", "license", "attribution")),
                "dataset needs source, license and attribution")
        require(isinstance(dataset.get("games"), list), "games must be an array")
        dataset_ids.add(dataset_id)
        provenance.append({"id": dataset_id, **{k: info[k] for k in ("source", "license", "attribution")}})
        for game in dataset["games"]:
            normalized = validate_record(game)
            for content_id in game["contentIds"]:
                require(content_id not in by_id or by_id[content_id] == normalized,
                        f"conflicting identity for {content_id}; review before merging")
                by_id[content_id] = normalized
    shards = {}
    for content_id, metadata in sorted(by_id.items()):
        prefix = content_id.split(":", 1)[1][:2]
        shards.setdefault(prefix, {})[content_id] = metadata
    manifest = {"schemaVersion": 1, "shards": sorted(shards),
                "datasets": sorted(provenance, key=lambda x: x["id"]), "games": len(by_id)}
    return manifest, shards


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path, help="catalog asset directory")
    args = parser.parse_args()
    manifest, shards = build(json.loads(args.source.read_text(encoding="utf-8")))
    shard_dir = args.output / "shards"
    shard_dir.mkdir(parents=True, exist_ok=True)
    for prefix, games in shards.items():
        (shard_dir / f"{prefix}.json").write_bytes(compact({"schemaVersion": 1, "games": games}))
    (args.output / "manifest-v1.json").write_bytes(compact(manifest))
    digest = hashlib.sha256(compact(manifest) + b"".join(
        compact({"schemaVersion": 1, "games": shards[name]}) for name in sorted(shards))).hexdigest()
    print(f"{len(shards)} shards, {manifest['games']} hashes, sha256 {digest}")


if __name__ == "__main__":
    main()
