#!/usr/bin/env python3
"""Local, review-gated PC-98 catalog research. Outputs belong in .downloads/."""

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
CHUNK = 1024 * 1024
CONTENT_PREFIX = "sha256-hdi-v1:"
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


def digest(stream):
    sha = hashlib.sha256()
    while block := stream.read(CHUNK):
        sha.update(block)
    return CONTENT_PREFIX + sha.hexdigest()


def scan(root, previous=None):
    """Scan direct HDIs and ordinary ZIPs; nested ZIPs are queued for later."""
    root = Path(root).resolve()
    old = {item["path"]: item for item in (previous or {}).get("archives", [])}
    results = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in (".zip", ".hdi"):
            continue
        relative = path.relative_to(root).as_posix()
        stat = path.stat()
        fingerprint = {"size": stat.st_size, "mtimeNs": stat.st_mtime_ns}
        cached = old.get(relative)
        if cached and cached.get("fingerprint") == fingerprint:
            results.append(cached)
            continue
        item = {"path": relative, "titleHint": title_hint(path.name),
                "fingerprint": fingerprint, "disks": [], "status": "pending"}
        try:
            if path.suffix.lower() == ".hdi":
                if stat.st_size > MAX_HDI:
                    raise ValueError("HDI exceeds 4 GiB")
                with path.open("rb") as source:
                    item["disks"].append({"entry": None, "contentId": digest(source)})
            else:
                with zipfile.ZipFile(path) as archive:
                    entries = archive.infolist()
                    for info in entries:
                        if not valid_entry(info):
                            raise ValueError("unsafe ZIP entry")
                        if info.filename.lower().endswith(".zip"):
                            item["nestedZip"] = True
                        if not info.filename.lower().endswith(".hdi"):
                            continue
                        if info.file_size > MAX_HDI or info.file_size > max(64 * 1024**2, info.compress_size * 250):
                            raise ValueError("HDI exceeds size or expansion limit")
                        with archive.open(info) as source:
                            item["disks"].append({"entry": info.filename,
                                                  "contentId": digest(source)})
            item["status"] = "ready" if item["disks"] else ("nested-zip" if item.get("nestedZip") else "no-hdi")
        except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
            item["status"] = "error"
            item["error"] = str(error)
        results.append(item)
    return {"schemaVersion": 1, "root": str(root), "archives": results}


def load_metadata(path):
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


def local_images(root, game):
    if not root:
        return []
    names = {key(name) for name in [game["Name"], *game["aliases"]]}
    return [path.relative_to(root).as_posix() for path in sorted(Path(root).rglob("*"))
            if path.is_file() and key(path.stem) in names]


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
             "candidates": candidates(games, group["titleHint"]), "review": "needs-review"}
            for name, group in sorted(grouped.items())]


def record_review(queue, decisions, group_id, game_id, evidence, notes="", description="",
                  box_url="", screenshot_url="", image_status="unreviewed"):
    groups_by_id = {group["groupId"]: group for group in queue["groups"]}
    if group_id not in groups_by_id:
        raise ValueError("unknown group ID")
    if not groups_by_id[group_id]["contentIds"]:
        raise ValueError("group has no supported HDI content IDs")
    if not (evidence.startswith("https://") and game_id.isdigit()):
        raise ValueError("review requires a numeric LaunchBox ID and HTTPS evidence URL")
    if not notes.strip():
        raise ValueError("manual review notes are required")
    if image_status not in ("unreviewed", "visually-checked", "flagged"):
        raise ValueError("invalid image review status")
    if any(url and not url.startswith("https://") for url in (box_url, screenshot_url)):
        raise ValueError("image sources must be HTTPS URLs")
    if image_status == "visually-checked" and not (box_url or screenshot_url):
        raise ValueError("visually-checked requires an image source link")
    decisions[group_id] = {"DatabaseID": game_id, "evidence": evidence,
                           "notes": notes.strip(), "draftDescription": description.strip(),
                           "imageReview": image_status,
                           "imageSources": {name: url for name, url in
                                            (("boxArt", box_url), ("screenshot", screenshot_url)) if url}}
    return decisions


def report(queue, decisions, games):
    reviewed = defaultdict(lambda: {"contentIds": set(), "sourceGroups": [], "evidence": []})
    for group in queue["groups"]:
        choice = decisions.get(group["groupId"])
        if not choice:
            continue
        game_id = choice["DatabaseID"]
        if game_id not in games:
            raise ValueError(f"reviewed LaunchBox ID missing from snapshot: {game_id}")
        result = reviewed[game_id]
        result["contentIds"].update(group["contentIds"])
        result["sourceGroups"].append(group["groupId"])
        result["evidence"].append(choice["evidence"])
        result.setdefault("reviewNotes", []).append(choice["notes"])
        result.setdefault("imageReviews", []).append({"status": choice["imageReview"],
                                                       "sources": choice["imageSources"]})
        if choice.get("draftDescription"):
            result.setdefault("draftDescriptions", []).append(choice["draftDescription"])
    return [{"DatabaseID": game_id, "title": games[game_id]["Name"],
             "contentIds": sorted(value["contentIds"]),
             "sourceGroups": value["sourceGroups"], "evidence": value["evidence"],
             "reviewNotes": value.get("reviewNotes", []),
             "draftDescriptions": value.get("draftDescriptions", []),
             "imageReviews": value.get("imageReviews", []),
             "overviewForReview": games[game_id]["Overview"],
             "imageCountsForReview": games[game_id]["imageCounts"]}
            for game_id, value in sorted(reviewed.items())]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    scan_cmd = sub.add_parser("scan", help="hash local HDIs; cache unchanged archives")
    scan_cmd.add_argument("root")
    scan_cmd.add_argument("index")
    query_cmd = sub.add_parser("query", help="search cached LaunchBox metadata; no automatic acceptance")
    query_cmd.add_argument("metadata")
    query_cmd.add_argument("title")
    query_cmd.add_argument("--id", help="inspect a specific database ID")
    query_cmd.add_argument("--images", help="local LaunchBox image directory")
    queue_cmd = sub.add_parser("queue", help="create unreviewed candidate queue")
    queue_cmd.add_argument("index")
    queue_cmd.add_argument("metadata")
    queue_cmd.add_argument("output")
    review_cmd = sub.add_parser("review", help="record manual identity decision")
    review_cmd.add_argument("queue")
    review_cmd.add_argument("decisions")
    review_cmd.add_argument("group_id")
    review_cmd.add_argument("database_id")
    review_cmd.add_argument("evidence_url")
    review_cmd.add_argument("--notes", required=True, help="identity and image checks, including mismatches")
    review_cmd.add_argument("--description", default="", help="original prose drafted after source review")
    review_cmd.add_argument("--box-url", default="", help="source link for selected box art")
    review_cmd.add_argument("--screenshot-url", default="", help="source link for selected screenshot")
    review_cmd.add_argument("--image-status", choices=("unreviewed", "visually-checked", "flagged"),
                            default="unreviewed")
    report_cmd = sub.add_parser("report", help="show reviewed, merged draft entries")
    report_cmd.add_argument("queue")
    report_cmd.add_argument("decisions")
    report_cmd.add_argument("metadata")
    args = parser.parse_args(argv)
    if args.command == "scan":
        result = scan(args.root, read(args.index))
        save(args.index, result)
        print(json.dumps(dict(Counter(item["status"] for item in result["archives"])), indent=2))
    elif args.command == "query":
        games = load_metadata(args.metadata)
        result = games.get(args.id) if args.id else candidates(games, args.title)
        if args.id and result and args.images:
            result["localImagesForReview"] = local_images(args.images, result)
        elif args.images:
            for candidate in result:
                candidate["localImagesForReview"] = local_images(args.images, games[candidate["DatabaseID"]])
        print(json.dumps(result, ensure_ascii=False, indent=2))
    elif args.command == "queue":
        data = {"schemaVersion": 1, "groups": groups(read(args.index), load_metadata(args.metadata))}
        save(args.output, data)
        print(f"{len(data['groups'])} groups; all require manual review")
    elif args.command == "review":
        decisions = record_review(read(args.queue), read(args.decisions, {}),
                                  args.group_id, args.database_id, args.evidence_url,
                                  args.notes, args.description, args.box_url,
                                  args.screenshot_url, args.image_status)
        save(args.decisions, decisions)
        print(f"Recorded manual review for {args.group_id}")
    else:
        print(json.dumps(report(read(args.queue), read(args.decisions, {}),
                                load_metadata(args.metadata)), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"catalog workbench: {error}", file=sys.stderr)
        sys.exit(1)
