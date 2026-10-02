#!/usr/bin/env python3
"""Verify staged catalog artifacts against staged source records before commit."""

import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SOURCE_FILES = (
    "catalog/research/matches-v1.json",
    "catalog/research/adult-content-v1.json",
    "catalog/research/descriptions-v1.json",
    "catalog/research/display-text-v1.json",
    "catalog/startup-profiles-v1.json",
    "catalog/core-review-v1.json",
    "catalog/research/artwork-index-v1.json",
)
GENERATED_FILES = (
    "catalog/source-v1.json",
    "catalog/online-v1.json",
    "kairo98/src/main/assets/catalog/manifest-v1.json",
    "kairo98/src/main/assets/catalog/name-index-v1.json",
)
SHARDS = "kairo98/src/main/assets/catalog/shards/"
BUILDERS = {
    "tools/build_review_catalog.py",
    "tools/build_catalog.py",
    "tools/artwork_references.py",
    "tools/build_catalog_packages.py",
    "tools/build_online_catalog.py",
}


def git(*args):
    return subprocess.run(("git", *args), cwd=ROOT, check=True,
                          stdout=subprocess.PIPE).stdout


def staged(path):
    result = subprocess.run(("git", "show", f":{path}"), cwd=ROOT,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return result.stdout if result.returncode == 0 else None


def main():
    changed = {name.decode("utf-8") for name in
               git("diff", "--cached", "--name-only", "-z").split(b"\0") if name}
    watched = set(SOURCE_FILES) | set(GENERATED_FILES) | BUILDERS
    watched.update(name.decode("utf-8") for name in git("ls-files", "--cached", "-z", "--", "catalog/parts/", "catalog/artwork-exclusions-v1.json", "catalog/optional/kairo98-art.nsfw.meta.json").split(b"\0") if name)
    if "--all" not in sys.argv[1:] and not any(
            path in watched or path.startswith(SHARDS) for path in changed):
        return 0
    unstaged = {name.decode("utf-8") for name in
                git("diff", "--name-only", "-z").split(b"\0") if name}
    conflicting = sorted(path for path in unstaged
                         if path in watched or path.startswith(SHARDS))
    if conflicting:
        print("Stage or discard unstaged catalog changes before committing:",
              ", ".join(conflicting), file=sys.stderr)
        return 1

    with tempfile.TemporaryDirectory(prefix="kairo98-catalog-check-") as directory:
        work = Path(directory)
        for path in (*SOURCE_FILES, GENERATED_FILES[3]):
            content = staged(path)
            if content is None:
                print(f"Missing staged catalog input: {path}", file=sys.stderr)
                return 1
            target = work / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        subprocess.run((sys.executable, str(ROOT / "tools/build_review_catalog.py"),
                        "--matches", str(work / SOURCE_FILES[0]),
                        "--assets", str(work / "kairo98/src/main/assets"),
                        "--art-assets", str(work / "kairo98/src/withImages/assets"),
                        "--reuse-art"), cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
        subprocess.run((sys.executable, str(ROOT / "tools/build_online_catalog.py"),
                        str(work / GENERATED_FILES[0]),
                        str(work / GENERATED_FILES[3]),
                        str(work / GENERATED_FILES[1])), cwd=ROOT, check=True,
                       stdout=subprocess.DEVNULL)

        expected = set(GENERATED_FILES) | {"catalog/optional/data-v1.json", "catalog/excluded-ids-v1.json"}
        expected.update(path.relative_to(work).as_posix()
                        for path in (work / SHARDS).glob("*.json"))
        tracked_shards = {name.decode("utf-8") for name in
                          git("ls-files", "--cached", "-z", "--", SHARDS).split(b"\0") if name}
        mismatches = [path for path in sorted(expected | tracked_shards)
                      if staged(path) != ((work / path).read_bytes()
                                          if (work / path).is_file() else None)]
        if mismatches:
            print("Generated catalog differs from staged source records:", file=sys.stderr)
            for path in mismatches:
                print(f"  {path}", file=sys.stderr)
            print("Regenerate and stage the catalog before committing.", file=sys.stderr)
            return 1
    subprocess.run((sys.executable, str(ROOT / "shared/tools/audit_core_catalog.py"), "pc98"), cwd=ROOT, check=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
