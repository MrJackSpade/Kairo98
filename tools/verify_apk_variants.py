#!/usr/bin/env python3
"""Check that tagged APK variants share catalog data and split embedded artwork."""

import hashlib
import sys
import zipfile


def assets(apk):
    with zipfile.ZipFile(apk) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in archive.namelist() if name.startswith("assets/") and not name.endswith("/")}


def verify(with_images, without_images):
    full = assets(with_images)
    lite = assets(without_images)
    art = {name for name in full if name.startswith("assets/art/")}
    assert len([name for name in art if name.endswith(".webp")]) > 0, "Artwork APK has no images"
    assert not any(name.startswith("assets/art/") for name in lite), "Image-free APK contains artwork"
    assert {name: digest for name, digest in full.items() if name not in art} == lite, \
        "Shared APK assets differ"
    assert any(name.startswith("assets/catalog/") for name in lite), "Catalog metadata missing"
    print(f"Verified {len(art)} artwork assets and {len(lite)} identical shared assets")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: verify_apk_variants.py WITH_IMAGES.apk WITHOUT_IMAGES.apk")
    verify(sys.argv[1], sys.argv[2])
