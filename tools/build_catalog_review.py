#!/usr/bin/env python3
"""Make a compact review gallery from downloaded LaunchBox source images.

By default PNG images become JPG. With --webp, images are encoded as WebP and
smaller originals that already meet the cap are retained. --max-height reduces
taller images without upscaling. The manifest retains original source URLs.
"""

import argparse
import json
import os
import shutil
import struct
import subprocess
from pathlib import Path

from download_catalog_images import write_gallery


def source_items(roots):
    seen = set()
    result = []
    for root in roots:
        manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
        for item in manifest["entries"]:
            if item["pageUrl"] in seen:
                raise ValueError(f"duplicate source page: {item['pageUrl']}")
            seen.add(item["pageUrl"])
            result.append((root, item))
    return sorted(result, key=lambda pair: (pair[1]["platform"], pair[1]["title"].casefold()))


def safe_path(root, relative):
    path = Path(relative)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise ValueError(f"unsafe media path: {relative}")
    return root / path


def image_format(source):
    with source.open("rb") as stream:
        head = stream.read(12)
    if head.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    if head.startswith(b"\xff\xd8"):
        return ".jpg"
    if head.startswith((b"GIF87a", b"GIF89a")):
        return ".gif"
    if head.startswith(b"RIFF") and head[8:12] == b"WEBP":
        return ".webp"
    raise ValueError(f"unrecognized image format: {source}")


def image_dimensions(source, kind):
    if kind == ".png":
        with source.open("rb") as stream:
            head = stream.read(24)
        return struct.unpack(">II", head[16:24])
    if kind == ".gif":
        with source.open("rb") as stream:
            head = stream.read(10)
        return struct.unpack("<HH", head[6:10])
    if kind == ".webp":
        with source.open("rb") as stream:
            head = stream.read(30)
        chunk = head[12:16]
        if chunk == b"VP8X" and len(head) >= 30:
            return (1 + int.from_bytes(head[24:27], "little"),
                    1 + int.from_bytes(head[27:30], "little"))
        if chunk == b"VP8 " and head[23:26] == b"\x9d\x01\x2a":
            return (int.from_bytes(head[26:28], "little") & 0x3fff,
                    int.from_bytes(head[28:30], "little") & 0x3fff)
        if chunk == b"VP8L" and len(head) >= 25 and head[20] == 0x2f:
            bits = int.from_bytes(head[21:25], "little")
            return (1 + (bits & 0x3fff), 1 + ((bits >> 14) & 0x3fff))
        raise ValueError(f"WebP has no dimensions: {source}")
    if kind == ".jpg":
        with source.open("rb") as stream:
            if stream.read(2) != b"\xff\xd8":
                raise ValueError(f"invalid JPEG: {source}")
            while True:
                byte = stream.read(1)
                if not byte:
                    raise ValueError(f"JPEG has no dimensions: {source}")
                if byte != b"\xff":
                    continue
                marker = stream.read(1)
                while marker == b"\xff":
                    marker = stream.read(1)
                if marker in (b"\xd8", b"\xd9"):
                    continue
                length = int.from_bytes(stream.read(2), "big")
                if marker[0] in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                                 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                    stream.read(1)
                    height = int.from_bytes(stream.read(2), "big")
                    width = int.from_bytes(stream.read(2), "big")
                    return width, height
                stream.seek(length - 2, 1)
    result = subprocess.run(
        ["ffprobe.exe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height", "-of", "csv=s=x:p=0", str(source)],
        capture_output=True, text=True, timeout=30, check=True,
    )
    width, height = result.stdout.strip().split("x")
    return int(width), int(height)


def transcode(source, destination, dimensions=None):
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.stem + ".part" + destination.suffix)
    command = ["ffmpeg.exe", "-hide_banner", "-loglevel", "error", "-y",
               "-threads", "1", "-i", str(source)]
    if dimensions:
        command.extend(["-vf", f"scale={dimensions[0]}:{dimensions[1]}"])
    command.extend(["-frames:v", "1"])
    if destination.suffix == ".jpg":
        command.extend(["-c:v", "mjpeg", "-q:v", "4"])
    elif destination.suffix == ".png":
        command.extend(["-c:v", "png", "-compression_level", "9"])
    elif destination.suffix == ".gif":
        command.extend(["-c:v", "gif"])
    elif destination.suffix == ".webp":
        command.extend(["-c:v", "libwebp", "-quality", "75"])
    else:
        raise ValueError(f"unsupported output format: {destination}")
    command.append(str(temporary))
    result = subprocess.run(
        command,
        capture_output=True, text=True, timeout=30,
    )
    if result.returncode or not temporary.is_file() or not temporary.stat().st_size:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(f"image conversion failed for {source}: {result.stderr.strip()}")
    temporary.replace(destination)


def link_or_copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        os.link(source, destination)
    except OSError:
        shutil.copy2(source, destination)


def build(roots, output, max_height=None, webp=False):
    output.mkdir(parents=True, exist_ok=True)
    gallery = []
    converted = webp_encoded = resized = larger = 0
    original_bytes = review_bytes = 0
    for root, item in source_items(roots):
        new_item = {"platform": item["platform"], "title": item["title"],
                    "pageUrl": item["pageUrl"], "images": {}}
        for kind, image in item["images"].items():
            source = safe_path(root, image["path"])
            if not source.is_file() or not source.stat().st_size:
                raise FileNotFoundError(source)
            relative = Path(image["path"])
            actual_format = image_format(source)
            width, height = image_dimensions(source, actual_format)
            convert = actual_format == ".png"
            resize = max_height is not None and height > max_height
            output_format = ".webp" if webp else (
                ".jpg" if convert or (kind == "box" and resize) else actual_format)
            relative = relative.with_suffix(output_format)
            destination = safe_path(output, relative)
            if not destination.is_file() or not destination.stat().st_size:
                if webp or convert or resize:
                    dimensions = (max(1, round(width * max_height / height)), max_height) if resize else None
                    transcode(source, destination, dimensions)
                else:
                    link_or_copy(source, destination)
            if webp and not resize and destination.stat().st_size >= source.stat().st_size:
                destination.unlink()
                relative = Path(image["path"]).with_suffix(actual_format)
                destination = safe_path(output, relative)
                if not destination.is_file() or not destination.stat().st_size:
                    link_or_copy(source, destination)
            if convert and not webp:
                converted += 1
            if webp and destination.suffix == ".webp" and (actual_format != ".webp" or resize):
                webp_encoded += 1
            if resize:
                resized += 1
            if (webp and destination.suffix == ".webp") or (not webp and (convert or resize)):
                larger += destination.stat().st_size > source.stat().st_size
            new_item["images"][kind] = {"url": image["url"],
                                        "path": relative.as_posix()}
            original_bytes += source.stat().st_size
            review_bytes += destination.stat().st_size
        gallery.append(new_item)
        if len(gallery) % 200 == 0:
            operation = (f"{webp_encoded} WebP images encoded" if webp else
                         f"{converted} PNG images converted")
            print(f"{len(gallery)} games; {operation}; {resized} images resized", flush=True)
    (output / "manifest.json").write_text(
        json.dumps({"entries": gallery}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    write_gallery(output, gallery)
    operation = (f"{webp_encoded} WebP images encoded" if webp else
                 f"{converted} PNG images converted")
    print(f"Done: {len(gallery)} games, {operation}, {resized} images resized, "
          f"{larger} outputs larger than source; {original_bytes} -> {review_bytes} bytes",
          flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("roots", nargs="+", type=Path)
    parser.add_argument("--max-height", type=int, help="maximum image height in pixels")
    parser.add_argument("--webp", action="store_true", help="use WebP when smaller than source")
    args = parser.parse_args()
    if args.max_height is not None and args.max_height < 1:
        parser.error("--max-height must be positive")
    build(args.roots, args.output, args.max_height, args.webp)


if __name__ == "__main__":
    main()
