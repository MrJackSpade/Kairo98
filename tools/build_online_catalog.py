#!/usr/bin/env python3
"""Build the separately downloadable catalog from the reviewed source and name index."""

import argparse
import json
from pathlib import Path

from build_catalog import build, compact, validate_record


def build_pack(source, name_index):
    _, shards = build(source)
    if name_index.get("schemaVersion") != 1 or not isinstance(name_index.get("games"), dict) or not isinstance(name_index.get("names"), dict):
        raise ValueError("invalid name index")
    for key, record in name_index["games"].items():
        if not isinstance(key, str) or not isinstance(record, dict):
            raise ValueError("invalid name index record")
        validate_record({**record, "contentIds": ["sha256-hdi-v1:" + "0" * 64]})
    if any(not isinstance(key, str) or not key.isalnum() or len(key) not in range(4, 129) or
           not isinstance(value, str) or value not in name_index["games"]
           for key, value in name_index["names"].items()):
        raise ValueError("invalid name index alias")
    games = {}
    for shard in shards.values():
        games.update(shard)
    return {"schemaVersion": 1, "games": games, "nameIndex": name_index}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("name_index", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = json.loads(args.source.read_text(encoding="utf-8"))
    names = json.loads(args.name_index.read_text(encoding="utf-8"))
    result = compact(build_pack(source, names))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(result)
    print(f"{len(result)} bytes, {len(json.loads(result)['games'])} content IDs")


if __name__ == "__main__":
    main()
