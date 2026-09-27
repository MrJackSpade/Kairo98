#!/usr/bin/env python3
"""Review PC-88/98 catalog titles against an extracted official VNDB database dump.

Only platform-specific, complete, official, non-patch releases decide a label.
Ambiguous title matches and conflicting release flags remain unreviewed.
"""

import argparse
import hashlib
import json
import unicodedata
from collections import defaultdict
from pathlib import Path


def rows(directory, table):
    with (directory / table).open(encoding="utf-8") as source:
        for line in source:
            yield line.rstrip("\n").split("\t")


def normalized(value):
    return "".join(character.casefold() for character in unicodedata.normalize("NFKC", value)
                   if character.isalnum())


def review(matches, directory, dump_sha256):
    platforms = defaultdict(set)
    for release, platform in rows(directory, "releases_platforms"):
        if platform in ("p88", "p98"):
            platforms[release].add(platform)
    eligible = set()
    for fields in rows(directory, "releases"):
        release = fields[0]
        if (release in platforms and fields[18] == "f" and fields[21] == "t"
                and fields[17] in ("t", "f")):
            eligible.add(release)
    release_flags = {fields[0]: fields[17] == "t" for fields in rows(directory, "releases")
                     if fields[0] in eligible}
    vn_releases = defaultdict(list)
    for release, vn, release_type in rows(directory, "releases_vn"):
        if release in eligible and release_type == "complete":
            vn_releases[vn].append(release)
    titles = defaultdict(set)
    for vn, _lang, _official, title, latin in rows(directory, "vn_titles"):
        if vn in vn_releases:
            for candidate in (title, latin):
                if candidate != "\\N" and len(normalized(candidate)) >= 5:
                    titles[normalized(candidate)].add(vn)
    for fields in rows(directory, "vn"):
        if fields[0] in vn_releases and fields[12] != "\\N":
            for alias in fields[12].split("\\n"):
                if len(normalized(alias)) >= 5:
                    titles[normalized(alias)].add(fields[0])

    reviewed = {}
    for entry in matches:
        key = f'{entry["platform"]}:{entry["databaseId"]}'
        platform = "p88" if entry["platform"] == "pc88" else "p98"
        candidates = set()
        for name in (entry["title"], *entry.get("aliases", [])):
            candidates.update(titles.get(normalized(name), ()))
        candidates = {vn for vn in candidates if any(platform in platforms[release]
                      for release in vn_releases[vn])}
        result = {"status": "unreviewed"}
        if candidates:
            result["vndbIds"] = sorted(candidates, key=lambda value: int(value[1:]))
        if len(candidates) == 1:
            vn = next(iter(candidates))
            release_ids = sorted((release for release in vn_releases[vn]
                                  if platform in platforms[release]), key=lambda value: int(value[1:]))
            flags = {release_flags[release] for release in release_ids}
            result["releaseIds"] = release_ids
            if len(flags) == 1:
                result["status"] = "eroge" if True in flags else "nonadult"
            else:
                result["reason"] = "conflicting platform releases"
        elif len(candidates) > 1:
            result["reason"] = "ambiguous title"
        else:
            result["reason"] = "no platform title match"
        reviewed[key] = result
    return {"schemaVersion": 1, "source": "VNDB database dump",
            "sourceUrl": "https://dl.vndb.org/dump/",
            "dumpSha256": dump_sha256, "license": "ODbL-1.0",
            "method": "exact normalized title or alias; official complete non-patch PC-88/PC-98 releases; unanimous has_ero",
            "games": reviewed}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dump", type=Path, help="downloaded vndb-db-YYYY-MM-DD.tar.zst")
    parser.add_argument("tables", type=Path, help="directory of extracted db tables")
    parser.add_argument("--matches", type=Path, default=Path("catalog/research/matches-v1.json"))
    parser.add_argument("--output", type=Path, default=Path("catalog/research/adult-content-v1.json"))
    args = parser.parse_args()
    matches = json.loads(args.matches.read_text(encoding="utf-8-sig"))["entries"]
    with args.dump.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    result = review(matches, args.tables, digest)
    manual_path = args.matches.parent / "adult-content-manual-v1.json"
    if manual_path.is_file():
        manual = json.loads(manual_path.read_text(encoding="utf-8"))["games"]
        for key, evidence in manual.items():
            if key not in result["games"] or evidence["status"] not in ("eroge", "nonadult"):
                raise ValueError(f"Invalid manual review: {key}")
            result["games"][key] = evidence
    args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n",
                           encoding="utf-8")
    counts = {status: sum(game["status"] == status for game in result["games"].values())
              for status in ("eroge", "nonadult", "unreviewed")}
    print(counts)


if __name__ == "__main__":
    main()
