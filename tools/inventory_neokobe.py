#!/usr/bin/env python3
"""Inventory on-disk NeoKobe game groups without extracting media or game bytes."""

import argparse
import json
import re
import zipfile
from collections import defaultdict
from pathlib import Path


def pc88_groups(root):
    groups = []
    for directory in sorted(root.iterdir()):
        if not directory.is_dir():
            continue
        files = sorted(path.relative_to(root).as_posix() for path in directory.rglob("*.7z"))
        if not files:
            continue
        title = re.sub(r"\s+\([^()]+\)$", "", directory.name).strip()
        groups.append({"platform": "pc88", "groupId": "pc88:" + directory.name,
                       "titleHint": title, "source": directory.name,
                       "variants": files})
    return groups


def pc98_groups(root):
    groups = defaultdict(list)
    errors = []
    for archive_path in sorted(root.glob("*.zip")):
        try:
            with zipfile.ZipFile(archive_path) as archive:
                for info in archive.infolist():
                    if info.is_dir() or not info.filename.lower().endswith(".zip"):
                        continue
                    parts = info.filename.replace("\\", "/").split("/")
                    if any(part in ("", ".", "..") for part in parts):
                        errors.append(f"{archive_path.name}: unsafe path")
                        continue
                    folder = parts[-2] if len(parts) >= 3 else Path(parts[-1]).stem
                    title = re.sub(r"\s*\[[^]]*\]", "", folder).strip()
                    key = (archive_path.name, folder)
                    groups[key].append(info.filename)
        except (OSError, ValueError, zipfile.BadZipFile) as error:
            errors.append(f"{archive_path.name}: {error}")
    result = [{"platform": "pc98", "groupId": "pc98:" + archive + ":" + folder,
               "titleHint": re.sub(r"\s*\[[^]]*\]", "", folder).strip(),
               "source": archive, "variants": sorted(entries)}
              for (archive, folder), entries in sorted(groups.items())]
    return result, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pc88_root", type=Path)
    parser.add_argument("pc98_root", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    pc98, errors = pc98_groups(args.pc98_root)
    inventory = {"schemaVersion": 1, "groups": pc88_groups(args.pc88_root) + pc98,
                 "errors": errors}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PC-88: {sum(x['platform'] == 'pc88' for x in inventory['groups'])} groups")
    print(f"PC-98: {len(pc98)} groups; {len(errors)} archive/path errors")


if __name__ == "__main__":
    main()
