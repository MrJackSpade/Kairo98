#!/usr/bin/env python3
"""Build the downloadable catalog: core-v4.zip, the bundled catalog documents (the assets'
catalog folder) as shared catalog_documents.update_archive packs them, with each language's
descriptions in core-v4-<language>.zip. App versions before v4 read catalog/core-v3.json, which stays
published as it was last built (Kairo#77).

Usage: build_online_catalog.py <source-v1.json> <assets catalog folder> <output folder>"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

from build_catalog import compact, validate_record

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "shared/tools"))
import catalog_documents

# Disk records name their game's shared name index record instead of repeating it.


def build_pack(source, name_index):
    return catalog_documents.build_pack(source, name_index, validate_record, "sha256-hdi-v1:" + "0" * 64)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = json.loads(args.source.read_text(encoding="utf-8"))
    documents = {path.relative_to(args.catalog).as_posix(): json.loads(path.read_text(encoding="utf-8"))
                 for path in sorted(args.catalog.rglob("*.json"))}
    build_pack(source, documents["name-index-v1.json"])
    translations = {name for name in documents if name.startswith("descriptions/")}
    outputs = {"core-v4.zip": catalog_documents.update_archive(
        {name: document for name, document in documents.items() if name not in translations})}
    for name in sorted(translations):
        language = name.removeprefix("descriptions/").removesuffix(".json")
        outputs[f"core-v4-{language}.zip"] = catalog_documents.update_archive({name: documents[name]})
    args.output.mkdir(parents=True, exist_ok=True)
    for name, content in outputs.items():
        (args.output / name).write_bytes(content)
        metadata = {"schemaVersion": 1, "archive": name, "sha256": hashlib.sha256(content).hexdigest(),
                    "size": len(content)}
        (args.output / (name.rsplit(".", 1)[0] + ".meta.json")).write_text(
            json.dumps(metadata, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(outputs)} update archives")


if __name__ == "__main__":
    main()
