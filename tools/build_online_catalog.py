#!/usr/bin/env python3
"""Build the separately downloadable catalog from the reviewed source and name index."""

import argparse
import hashlib
import json
import sys
from pathlib import Path

from build_catalog import compact, validate_record

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "shared/tools"))
import catalog_documents

# Disk records name their game's shared name index record instead of repeating it.
CORE_VERSION = 3


def build_pack(source, name_index):
    return catalog_documents.build_pack(source, name_index, validate_record, "sha256-hdi-v1:" + "0" * 64)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("name_index", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = json.loads(args.source.read_text(encoding="utf-8"))
    names = json.loads(args.name_index.read_text(encoding="utf-8"))
    result = compact({**build_pack(source, names), "coreVersion": CORE_VERSION})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(result)
    metadata = {"schemaVersion": 1, "archive": args.output.name,
                "sha256": hashlib.sha256(result).hexdigest(), "size": len(result)}
    args.output.with_suffix(".meta.json").write_text(
        json.dumps(metadata, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(result)} bytes, {len(json.loads(result)['games'])} content IDs")


if __name__ == "__main__":
    main()
