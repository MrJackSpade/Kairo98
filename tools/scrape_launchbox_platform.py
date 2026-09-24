#!/usr/bin/env python3
"""Cache public LaunchBox platform listings and their direct game-page URLs.

The website renders 100 games per HTML page and links to the next page at
``/platforms/games/<platform>/page/<number>``. This script only reads those
public listing pages; it does not download images or game files.
"""

import argparse
import json
import re
import subprocess
import time
from datetime import datetime, timezone
from html import unescape
from pathlib import Path


PLATFORMS = {
    "pc88": "192-nec-pc-8801",
    "pc98": "193-nec-pc-9801",
}
BASE = "https://gamesdb.launchbox-app.com"
LINK = re.compile(r'<a class="list-item link-no-underline" href="(/games/details/(\d+)[^"]*)">')
TITLE = re.compile(r"<h3\b[^>]*>(.*?)</h3>", re.DOTALL)
PAGE_COUNT = re.compile(r"\bpages:\s*(\d+)")
TAG = re.compile(r"<[^>]+>")


def parse_listing(source):
    pages = PAGE_COUNT.search(source)
    if not pages:
        raise ValueError("listing has no pagination count")
    entries = {}
    for link in LINK.finditer(source):
        heading = TITLE.search(source, link.end(), link.end() + 2200)
        if not heading:
            raise ValueError("game link has no nearby title: " + link.group(1))
        title = unescape(TAG.sub("", heading.group(1))).strip()
        if not title:
            raise ValueError("game link has empty title: " + link.group(1))
        game_id = link.group(2)
        entry = {"id": game_id, "title": title, "pageUrl": BASE + unescape(link.group(1))}
        if game_id in entries and entries[game_id] != entry:
            raise ValueError("conflicting listing for game " + game_id)
        entries[game_id] = entry
    if not entries:
        raise ValueError("listing contains no game links")
    return int(pages.group(1)), list(entries.values())


def download(url):
    # The bundled Android-toolchain Python lacks SSL support on Windows.
    result = subprocess.run(
        ["curl.exe", "-L", "--fail", "--silent", "--show-error", "--retry", "2",
         "--max-time", "40", "-A", "Kairo98 catalog research (public platform pages)", url],
        check=True, capture_output=True,
    )
    return result.stdout.decode("utf-8")


def scrape(platform, cache_dir, refresh=False, delay=0.5):
    slug = PLATFORMS[platform]
    base_url = f"{BASE}/platforms/games/{slug}"
    entries = {}
    page = 1
    page_count = None
    while page_count is None or page <= page_count:
        path = cache_dir / f"{platform}-page-{page:02d}.html"
        if refresh or not path.exists():
            if page > 1:
                time.sleep(delay)
            url = base_url if page == 1 else f"{base_url}/page/{page}"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(download(url), encoding="utf-8")
        count, games = parse_listing(path.read_text(encoding="utf-8"))
        if page_count is None:
            page_count = count
        elif count != page_count:
            raise ValueError(f"page count changed on page {page}: {count} vs {page_count}")
        for game in games:
            if game["id"] in entries:
                raise ValueError(f"game {game['id']} occurs on multiple pages")
            entries[game["id"]] = game
        print(f"{platform} page {page}/{page_count}: {len(games)} games", flush=True)
        page += 1
    return {
        "sourceUrl": base_url,
        "retrievedUtc": datetime.now(timezone.utc).isoformat(),
        "platform": platform,
        "pages": page_count,
        "entries": sorted(entries.values(), key=lambda game: (game["title"].casefold(), int(game["id"]))),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=PLATFORMS)
    parser.add_argument("output", type=Path)
    parser.add_argument("--cache-dir", type=Path, default=Path(".downloads/launchbox/platform-pages"))
    parser.add_argument("--refresh", action="store_true")
    args = parser.parse_args()
    result = scrape(args.platform, args.cache_dir, args.refresh)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {len(result['entries'])} {args.platform} games to {args.output}")


if __name__ == "__main__":
    main()
