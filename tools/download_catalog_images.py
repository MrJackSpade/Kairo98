#!/usr/bin/env python3
"""Download selected LaunchBox art to an ignored local review gallery.

This fetches the URLs already selected in the research catalog. It never
copies media into the Android project or the tracked catalog.
"""

import argparse
import hashlib
import html
import json
import re
import subprocess
import time
from pathlib import Path
from urllib.parse import urlparse


FIELDS = (("boxArtUrl", "box"), ("screenshotUrl", "screenshot"))
GAME_ID = re.compile(r"/games/details/(\d+)")
ALLOWED_HOSTS = {"images.launchbox-app.com", "gamesdb-images.launchbox.gg"}
SUFFIXES = {".jpg", ".png"}


def gallery_items(catalog):
    items = []
    for entry in catalog["entries"]:
        page = GAME_ID.search(entry["pageUrl"])
        if not page:
            raise ValueError(f"missing public game ID: {entry['pageUrl']}")
        item = {"platform": entry["platform"], "title": entry["title"],
                "pageUrl": entry["pageUrl"], "images": {}}
        for field, kind in FIELDS:
            url = entry[field]
            if not url:
                continue
            parsed = urlparse(url)
            suffix = Path(parsed.path).suffix.lower()
            if parsed.scheme != "https" or parsed.netloc not in ALLOWED_HOSTS or suffix not in SUFFIXES:
                raise ValueError(f"unexpected image URL: {url}")
            digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:12]
            local = f"{entry['platform']}/{page.group(1)}/{kind}-{digest}{suffix}"
            item["images"][kind] = {"url": url, "path": local}
        items.append(item)
    return items


def write_gallery(root, items):
    cards = []
    for item in items:
        images = "".join(
            f'<figure><a href="{html.escape(image["url"], quote=True)}" target="_blank" '
            f'rel="noopener noreferrer" title="Open larger source image">'
            f'<img loading="lazy" src="{html.escape(image["path"], quote=True)}" '
            f'alt="{html.escape(kind, quote=True)}"></a>'
            f'<figcaption>{html.escape(kind)}</figcaption></figure>'
            for kind, image in item["images"].items()
        )
        cards.append(
            f'<article data-search="{html.escape((item["platform"] + " " + item["title"]).casefold(), quote=True)}">'
            f'<h2>{html.escape(item["title"])}</h2>'
            f'<p>{html.escape(item["platform"].upper())} · '
            f'<a href="{html.escape(item["pageUrl"], quote=True)}">LaunchBox</a></p>'
            f'<div class="images">{images}</div></article>'
        )
    document = ("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\">"
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                "<title>Kairo98 catalog media</title>"
                "<style>body{background:#111;color:#eee;font:16px system-ui;margin:1.5rem}"
                "a{color:#9cf}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:1rem}"
                "article{background:#222;border:1px solid #444;border-radius:8px;padding:1rem;min-width:0}"
                "h2{font-size:1.1rem;margin:0}p{color:#aaa}.images{display:flex;gap:.5rem}"
                "figure{margin:0;flex:1;min-width:0}img{width:100%;height:220px;object-fit:contain;background:#000}"
                "figcaption{text-align:center;color:#aaa;font-size:.8rem}</style>"
                "<h1>Kairo98 catalog media</h1><p>Local research copies for catalog review. "
                "Click an image to open its larger source.</p>"
                "<label>Filter titles <input id=\"filter\" type=\"search\" autocomplete=\"off\"></label>"
                "<main>" + "".join(cards) + "</main>"
                "<script>document.getElementById('filter').addEventListener('input',e=>{"
                "const q=e.target.value.toLocaleLowerCase();"
                "document.querySelectorAll('article').forEach(a=>a.hidden=!a.dataset.search.includes(q));"
                "});</script></html>\n")
    (root / "index.html").write_text(document, encoding="utf-8")


def download(url, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".part")
    result = subprocess.run(
        ["curl.exe", "-L", "--fail", "--silent", "--show-error", "--retry", "2",
         "--connect-timeout", "15", "--max-time", "45", "-o", str(temporary), url],
        capture_output=True, text=True,
    )
    if result.returncode or not temporary.exists() or temporary.stat().st_size == 0:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(result.stderr.strip() or "empty download")
    temporary.replace(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("root", type=Path)
    parser.add_argument("--limit", type=int, help="download at most this many images this run")
    parser.add_argument("--delay", type=float, default=0.1,
                        help="seconds between requests; default 0.1")
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    items = gallery_items(catalog)
    args.root.mkdir(parents=True, exist_ok=True)
    (args.root / "manifest.json").write_text(
        json.dumps({"entries": items}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_gallery(args.root, items)
    images = [(image["url"], args.root / image["path"])
              for item in items for image in item["images"].values()]
    downloaded = skipped = 0
    errors = []
    for number, (url, destination) in enumerate(images, 1):
        if destination.is_file() and destination.stat().st_size:
            skipped += 1
            continue
        if args.limit is not None and downloaded >= args.limit:
            break
        try:
            download(url, destination)
            downloaded += 1
        except (OSError, RuntimeError) as error:
            errors.append({"url": url, "error": str(error)})
            print(f"Failed {number}/{len(images)}: {error}", flush=True)
        if number % 50 == 0:
            print(f"{number}/{len(images)}: {downloaded} downloaded, {skipped} already present, "
                  f"{len(errors)} failed", flush=True)
        if args.delay:
            time.sleep(args.delay)
    (args.root / "download-errors.json").write_text(
        json.dumps(errors, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Done: {downloaded} downloaded, {skipped} already present, "
          f"{len(errors)} failed of {len(images)} selected images", flush=True)


if __name__ == "__main__":
    main()
