#!/usr/bin/env python3
"""Local PC-98 archive scan and LaunchBox candidate lookup."""

import argparse
import hashlib
import json
import re
import sys
import unicodedata
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET

MAX_HDI = 4 * 1024**3
MAX_FLOPPY = 64 * 1024**2
CHUNK = 1024 * 1024
MEDIA_FORMATS_VERSION = 3
FLOPPY_EXTENSIONS = {".fdi", ".d88", ".88d", ".d98", ".98d", ".nfd",
                     ".fdd", ".dcp", ".dcu", ".hdm", ".xdf"}
ANNOTATION = re.compile(r"\s*\[[^]]*\]")
NON_WORD = re.compile(r"[^\w]+", re.UNICODE)


def save(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def read(path, default=None):
    path = Path(path)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else default


def title_hint(name):
    return ANNOTATION.sub("", Path(name).stem).strip(" -_")


def key(value):
    value = unicodedata.normalize("NFKC", value).casefold()
    return NON_WORD.sub("", value)


def valid_entry(info):
    name = info.filename.replace("\\", "/")
    parts = name.split("/")
    return (not info.is_dir() and not name.startswith("/") and
            all(part not in ("", ".", "..") for part in parts) and
            not re.match(r"^[a-zA-Z]:", name))


def image_kind(name):
    suffix = Path(name).suffix.lower()
    return "hdi" if suffix == ".hdi" else "fd" if suffix in FLOPPY_EXTENSIONS else None


def digest(stream, kind):
    sha = hashlib.sha256()
    while block := stream.read(CHUNK):
        sha.update(block)
    return f"sha256-{kind}-v1:" + sha.hexdigest()


def scan(root, previous=None):
    """Scan direct disk images and ordinary ZIPs; nested ZIPs are queued for later."""
    root = Path(root).resolve()
    old = {item["path"]: item for item in (previous or {}).get("archives", [])}
    formats_current = (previous or {}).get("mediaFormatsVersion", 1) >= MEDIA_FORMATS_VERSION
    results = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or (path.suffix.lower() != ".zip" and not image_kind(path.name)):
            continue
        relative = path.relative_to(root).as_posix()
        stat = path.stat()
        fingerprint = {"size": stat.st_size, "mtimeNs": stat.st_mtime_ns}
        cached = old.get(relative)
        unchanged = cached and cached.get("fingerprint") == fingerprint
        if unchanged and formats_current:
            results.append(cached)
            continue
        cached_disks = {disk["entry"]: disk["contentId"] for disk in cached.get("disks", [])} if unchanged else {}
        item = {"path": relative, "titleHint": title_hint(path.name),
                "fingerprint": fingerprint, "disks": [], "status": "pending"}
        try:
            if path.suffix.lower() != ".zip":
                kind = image_kind(path.name)
                if stat.st_size <= 0 or stat.st_size > (MAX_FLOPPY if kind == "fd" else MAX_HDI):
                    raise ValueError("disk image exceeds size limit")
                with path.open("rb") as source:
                    content_id = cached_disks.get(None) or digest(source, kind)
                    item["disks"].append({"entry": None, "contentId": content_id})
            else:
                with zipfile.ZipFile(path) as archive:
                    entries = archive.infolist()
                    for info in entries:
                        if not valid_entry(info):
                            raise ValueError("unsafe ZIP entry")
                        if info.filename.lower().endswith(".zip"):
                            item["nestedZip"] = True
                        kind = image_kind(info.filename)
                        if not kind:
                            continue
                        limit = MAX_FLOPPY if kind == "fd" else MAX_HDI
                        if (info.file_size <= 0 or info.file_size > limit or
                                info.compress_size <= 0 or info.file_size // info.compress_size > 1000):
                            raise ValueError("disk image exceeds size or expansion limit")
                        content_id = cached_disks.get(info.filename)
                        if not content_id:
                            with archive.open(info) as source:
                                content_id = digest(source, kind)
                        item["disks"].append({"entry": info.filename, "contentId": content_id})
            item["status"] = "ready" if item["disks"] else ("nested-zip" if item.get("nestedZip") else "no-supported-image")
        except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
            item["status"] = "error"
            item["error"] = str(error)
        results.append(item)
    return {"schemaVersion": 1, "mediaFormatsVersion": MEDIA_FORMATS_VERSION,
            "root": str(root), "archives": results}


def load_metadata(path):
    if Path(path).suffix.lower() == ".json":
        return read(path)
    games = {}
    aliases = defaultdict(list)
    images = defaultdict(lambda: Counter())
    for _, node in ET.iterparse(path, events=("end",)):
        if node.tag == "Game":
            game_id = node.findtext("DatabaseID")
            if game_id:
                games[game_id] = {tag: node.findtext(tag) or "" for tag in
                                  ("Name", "Overview", "Developer", "Publisher", "ReleaseDate", "Platform")}
                games[game_id]["DatabaseID"] = game_id
            node.clear()
        elif node.tag == "GameAlternateName":
            aliases[node.findtext("DatabaseID")].append(node.findtext("AlternateName") or "")
            node.clear()
        elif node.tag == "GameImage":
            images[node.findtext("DatabaseID")][node.findtext("Type") or "Other"] += 1
            node.clear()
    for game_id, game in games.items():
        game["aliases"] = sorted(set(aliases[game_id]))
        game["imageCounts"] = dict(images[game_id])
    return games


def candidates(games, title, limit=8):
    import difflib
    sought = key(title)
    ranked = []
    for game in games.values():
        names = [game["Name"], *game["aliases"]]
        score = max((1.0 if key(name) == sought else
                     difflib.SequenceMatcher(None, sought, key(name)).ratio()) for name in names)
        if score >= 0.46:
            ranked.append((score, game))
    ranked.sort(key=lambda pair: (-pair[0], pair[1]["Name"], pair[1]["DatabaseID"]))
    return [{"score": round(score, 3), "DatabaseID": game["DatabaseID"],
             "Name": game["Name"], "aliases": game["aliases"],
             "imageCounts": game["imageCounts"]} for score, game in ranked[:limit]]


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
