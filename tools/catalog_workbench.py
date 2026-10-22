#!/usr/bin/env python3
"""Local PC-98 archive scan and LaunchBox candidate lookup."""

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "shared/tools"))
import content_ids
from launchbox import candidates, key, load_metadata

MAX_HDI = 4 * 1024**3
MAX_FLOPPY = 64 * 1024**2
MEDIA_FORMATS_VERSION = 4
MAX_CD = 2 * 1024**3
FLOPPY_EXTENSIONS = {".fdi", ".d88", ".88d", ".d98", ".98d", ".nfd",
                     ".fdd", ".dcp", ".dcu", ".hdm", ".xdf"}


def save(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def read(path, default=None):
    path = Path(path)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else default


def image_kind(name):
    suffix = Path(name).suffix.lower()
    return ("hdi" if suffix == ".hdi" else "fd" if suffix in FLOPPY_EXTENSIONS else
            "cd" if suffix == ".ccd" else None)


def cd_data(name):
    """A CloneCD descriptor's track data, which its content ID hashes."""
    return name[:name.rfind(".")] + ".img"


def size_limit(kind):
    return MAX_FLOPPY if kind == "fd" else MAX_CD if kind == "cd" else MAX_HDI


def media_data(name, kind):
    return cd_data(name) if kind == "cd" else name


MEDIA = content_ids.Media(image_kind, media_data, size_limit, MEDIA_FORMATS_VERSION)


def scan(root, previous=None):
    """Scan direct disk images and ordinary ZIPs; nested ZIPs are queued for later."""
    return content_ids.scan(root, MEDIA, previous)


def groups(index, games):
    grouped = defaultdict(lambda: {"sources": [], "contentIds": set()})
    for item in index["archives"]:
        group = grouped[key(item["titleHint"])]
        group["titleHint"] = item["titleHint"]
        group["sources"].append({"path": item["path"], "status": item["status"],
                                 "entries": [disk["entry"] for disk in item["disks"]]})
        group["contentIds"].update(disk["contentId"] for disk in item["disks"])
    return [{"groupId": name, "titleHint": group["titleHint"],
             "sources": group["sources"], "contentIds": sorted(group["contentIds"]),
             "candidates": candidates(games, group["titleHint"])}
            for name, group in sorted(grouped.items())]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    scan_cmd = sub.add_parser("scan", help="hash local disk images; cache unchanged archives")
    scan_cmd.add_argument("root")
    scan_cmd.add_argument("index")
    query_cmd = sub.add_parser("query", help="search cached LaunchBox metadata")
    query_cmd.add_argument("metadata")
    query_cmd.add_argument("title")
    query_cmd.add_argument("--id", help="inspect a specific database ID")
    queue_cmd = sub.add_parser("queue", help="group translated archives and list title candidates")
    queue_cmd.add_argument("index")
    queue_cmd.add_argument("metadata")
    queue_cmd.add_argument("output")
    args = parser.parse_args(argv)
    if args.command == "scan":
        result = scan(args.root, read(args.index))
        save(args.index, result)
        print(json.dumps(dict(Counter(item["status"] for item in result["archives"])), indent=2))
    elif args.command == "query":
        games = load_metadata(args.metadata)
        result = games.get(args.id) if args.id else candidates(games, args.title)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    elif args.command == "queue":
        data = {"schemaVersion": 1, "groups": groups(read(args.index), load_metadata(args.metadata))}
        save(args.output, data)
        print(f"{len(data['groups'])} translated title groups")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"catalog workbench: {error}", file=sys.stderr)
        sys.exit(1)
