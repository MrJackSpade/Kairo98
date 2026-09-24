#!/usr/bin/env python3
"""Run each translated archive on a connected debug build and capture its screen.

Screenshots and the run log are written under an ignored local directory. The
script uses the app's cached content IDs, so it never guesses from ZIP names.
Review a sequence of captures before assigning a boot outcome. A single frame
can show a normal intro fade, loading screen, prompt, or app preparation error.
"""

import argparse
import base64
import json
import subprocess
import time
from pathlib import Path


PACKAGE = "com.mrjackspade.kairo98"
ACTIVITY = f"{PACKAGE}/.MainActivity"


def adb(executable, *args, binary=False):
    result = subprocess.run([str(executable), *args], capture_output=True, check=True)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def choose_entries(entries):
    archives = {}
    for entry in entries:
        path = entry.get("path", "")
        if not path.startswith("Translated/") or not entry.get("contentId") or entry.get("error"):
            continue
        archives.setdefault(path, []).append(entry)
    selected = []
    for path, disks in sorted(archives.items()):
        def priority(disk):
            name = (disk.get("zipEntry") or disk.get("path") or "").lower()
            return (0 if name.endswith(".hdi") else 1,
                    0 if "boot" in name else 1,
                    0 if "disk 1" in name or "disk 01" in name else 1,
                    name)
        selected.append(min(disks, key=priority))
    return selected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, default=Path("C:/bin/platform-tools/adb.exe"))
    parser.add_argument("--output", type=Path, default=Path(".downloads/boot-survey"))
    parser.add_argument("--seconds", type=float, default=12.0)
    parser.add_argument("--capture-at", type=str,
                        help="comma-separated wall-clock seconds after launch, e.g. 10,30,60")
    parser.add_argument("--start", type=int, default=0)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--indices", type=str, help="comma-separated archive indices to capture")
    args = parser.parse_args()
    if args.seconds < 2:
        parser.error("--seconds must be at least 2")
    try:
        capture_times = ([float(value.strip()) for value in args.capture_at.split(",")]
                         if args.capture_at else [args.seconds])
    except ValueError:
        parser.error("--capture-at must contain numbers")
    if (not capture_times or any(value < 2 for value in capture_times) or
            capture_times != sorted(set(capture_times))):
        parser.error("--capture-at must contain increasing, unique times of at least 2 seconds")
    cache = json.loads(adb(args.adb, "exec-out", "run-as", PACKAGE,
                           "cat", "files/library-v1.json"))
    entries = choose_entries(cache["entries"])
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "plan.json").write_text(json.dumps([
        {"index": number, "path": entry["path"],
         "zipEntry": entry.get("zipEntry"), "contentId": entry["contentId"]}
        for number, entry in enumerate(entries)
    ], indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    end = len(entries) if args.limit is None else min(len(entries), args.start + args.limit)
    indices = [int(value) for value in args.indices.split(",")] if args.indices else list(range(args.start, end))
    if any(number < 0 or number >= len(entries) for number in indices):
        parser.error("--indices contains an out-of-range archive index")
    print(f"{len(entries)} translated archives; running {len(indices)} selected", flush=True)
    for number in indices:
        entry = entries[number]
        outputs = ([args.output / f"{number:03d}.png"] if not args.capture_at else
                   [args.output / f"{number:03d}-{seconds:g}s.png" for seconds in capture_times])
        if all(output.exists() for output in outputs):
            print(f"{number + 1}/{len(entries)} cached: {entry['path']}", flush=True)
            continue
        query = base64.urlsafe_b64encode(entry["contentId"].encode()).decode().rstrip("=")
        started = time.monotonic()
        try:
            adb(args.adb, "shell", "am", "start", "-S", "-n", ACTIVITY,
                "--es", "kairo98.launchGame64", query)
            for seconds, output in zip(capture_times, outputs):
                time.sleep(max(0, started + seconds - time.monotonic()))
                picture = adb(args.adb, "exec-out", "screencap", "-p", binary=True)
                if not picture.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise ValueError("ADB did not return a PNG screenshot")
                output.write_bytes(picture)
            status = "captured"
        except (subprocess.CalledProcessError, OSError, ValueError) as error:
            status = f"error: {error}"
        record = {"index": number, "path": entry["path"],
                  "zipEntry": entry.get("zipEntry"), "contentId": entry["contentId"],
                  "status": status, "elapsedSeconds": round(time.monotonic() - started, 2),
                  "captures": [str(output) for output in outputs if output.exists()]}
        with (args.output / "runs.jsonl").open("a", encoding="utf-8") as log:
            log.write(json.dumps(record, ensure_ascii=False) + "\n")
        print(f"{number + 1}/{len(entries)} {status}: {entry['path']}", flush=True)


if __name__ == "__main__":
    main()
