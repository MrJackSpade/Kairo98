#!/usr/bin/env python3
"""Fill missing catalog media URLs from each matched LaunchBox game page.

Identification comes from the existing catalog and cached LaunchBox database.
This only curls those exact game pages, caches HTML under an ignored directory,
and reads image URLs from page markup; it does not inspect the images.
"""

import argparse
import html
import json
import re
import subprocess
import time
from pathlib import Path
from urllib.parse import urlparse


PAGE_ID = re.compile(r"^/games/details/(\d+)(?:-[^/]*)?$")
TITLE = re.compile(r"<title>(.*?) - LaunchBox Games Database</title>", re.DOTALL)
SECTION = re.compile(r"<article\b[^>]*>\s*<h3\b[^>]*>(.*?)</h3>(.*?)</article>", re.DOTALL)
IMAGE = re.compile(r'<img\b[^>]*\bsrc="(https://(?:images\.launchbox-app\.com|gamesdb-images\.launchbox\.gg)/+[^\"]+)"', re.DOTALL)
BOX_TYPES = ("Box - Front", "Fanart - Box - Front")
SCREENSHOT_TYPES = ("Screenshot - Gameplay", "Screenshot - Game Title", "Screenshot - Game Select")


def page_id(url):
    parsed = urlparse(url)
    match = PAGE_ID.fullmatch(parsed.path)
    if parsed.scheme != "https" or parsed.netloc != "gamesdb.launchbox-app.com" or not match:
        raise ValueError(f"unexpected game page URL: {url}")
    return match.group(1)


def parse_page(source, expected_title):
    title = TITLE.search(source)
    if not title or html.unescape(title.group(1)).strip() != expected_title.strip():
        raise ValueError(f"page title differs from catalog title: {expected_title}")
    sections = {}
    for section in SECTION.finditer(source):
        category = html.unescape(re.sub(r"<[^>]*>", "", section.group(1))).strip()
        image = IMAGE.search(section.group(2))
        if image:
            sections.setdefault(category, html.unescape(image.group(1)).replace(
                "https://images.launchbox-app.com//", "https://images.launchbox-app.com/"))
    return {
        "boxArtUrl": next((sections[kind] for kind in BOX_TYPES if kind in sections), ""),
        "screenshotUrl": next((sections[kind] for kind in SCREENSHOT_TYPES if kind in sections), ""),
    }


def fetch(url, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".part")
    result = subprocess.run(
        ["curl.exe", "-L", "--fail", "--silent", "--show-error", "--retry", "2",
         "--connect-timeout", "15", "--max-time", "45", "-o", str(temporary), url],
        capture_output=True, text=True,
    )
    if result.returncode or not temporary.exists() or not temporary.stat().st_size:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(result.stderr.strip() or "empty page response")
    temporary.replace(destination)


def enrich(catalog, cache_dir, limit=None, delay=0.1, all_pages=False):
    fetched = filled = cached = 0
    errors = []
    for entry in catalog["entries"]:
        missing = [field for field in ("boxArtUrl", "screenshotUrl") if not entry[field]]
        if not missing and not all_pages:
            continue
        path = cache_dir / f"game-{page_id(entry['pageUrl'])}.html"
        if not path.is_file():
            if limit is not None and fetched >= limit:
                continue
            try:
                fetch(entry["pageUrl"], path)
                fetched += 1
                if delay:
                    time.sleep(delay)
            except (OSError, RuntimeError) as error:
                errors.append({"pageUrl": entry["pageUrl"], "error": str(error)})
                continue
        else:
            cached += 1
        try:
            media = parse_page(path.read_text(encoding="utf-8"), entry["title"])
        except (OSError, ValueError) as error:
            errors.append({"pageUrl": entry["pageUrl"], "error": str(error)})
            continue
        for field in missing:
            if media[field]:
                entry[field] = media[field]
                filled += 1
        if (fetched + cached) % 25 == 0:
            print(f"pages {fetched} fetched, {cached} cached; {filled} media URLs filled; "
                  f"{len(errors)} errors", flush=True)
    print(f"Done: {fetched} pages fetched, {cached} cached; {filled} media URLs filled; "
          f"{len(errors)} errors", flush=True)
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("cache_dir", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--limit", type=int, help="fetch at most this many new pages")
    parser.add_argument("--delay", type=float, default=0.1)
    parser.add_argument("--all-pages", action="store_true",
                        help="cache and check every matched game page, including entries with media")
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    errors = enrich(catalog, args.cache_dir, args.limit, args.delay, args.all_pages)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".tmp")
    temporary.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(args.output)
    (args.cache_dir / "media-page-errors.json").write_text(
        json.dumps(errors, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
