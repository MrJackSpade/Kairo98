#!/usr/bin/env python3
"""Audit release APK variants and the Play AAB that will be distributed."""

import hashlib
import pathlib
import re
import sys
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
BLOCKED = re.compile(
    r"(?i)(^|/)(?:fmgen|mame|dosbox)(?:/|$)|"
    r"(^|/)(?:bios\d*\.(?:rom|bin)|font\.bmp|ym2608_adpcm_rom\.bin)$|"
    r"\.(?:hdi|fdi|d88|nfd|hdm|xdf|iso|chd)$"
)
REQUIRED = (
    "assets/THIRD_PARTY_NOTICES.txt",
    "assets/PRIVACY_POLICY.txt",
)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def inspect(path: pathlib.Path, bundle: bool, allow_art: bool = False) -> dict[str, str]:
    prefix = "base/" if bundle else ""
    with zipfile.ZipFile(path) as archive:
        files = {entry.filename for entry in archive.infolist() if not entry.is_dir()}
        assert not any(BLOCKED.search(name) for name in files), f"Forbidden file in {path}"
        if not allow_art:
            assert not any(name.startswith(prefix + "assets/art/") for name in files), (
                f"Bundled game artwork in {path}"
            )
        native = {name for name in files if name.startswith(prefix + "lib/")}
        assert native == {prefix + "lib/arm64-v8a/libkairo98.so"}, (
            f"Unexpected native libraries in {path}: {native}"
        )
        for asset in REQUIRED:
            assert prefix + asset in files, f"Missing {asset} in {path}"
            expected = (ROOT / "app/src/main" / asset).read_bytes()
            assert archive.read(prefix + asset) == expected, (
                f"Packaged {asset} differs from source in {path}"
            )
        assert any(name.startswith(prefix + "assets/catalog/") for name in files), (
            f"Catalog metadata missing from {path}"
        )
        notices = archive.read(prefix + REQUIRED[0]).decode("utf-8")
        for required in ("Neko Project 21/W", "ymfm", "Spleen", "Shinonome", "Android NDK"):
            assert required in notices, f"Missing {required} notice in {path}"
        assets = {
            name[len(prefix):]: digest(archive.read(name))
            for name in files if name.startswith(prefix + "assets/")
        }
        print(f"{path.name}: {len(files)} files, {len(assets)} assets, "
              f"SHA-256 {digest(path.read_bytes())}")
        return assets


def main() -> None:
    if len(sys.argv) not in (3, 4):
        raise SystemExit("Usage: audit_play_release.py WITHOUT_IMAGES_APK AAB [WITH_IMAGES_APK]")
    apk, bundle = (pathlib.Path(argument) for argument in sys.argv[1:3])
    apk_assets = inspect(apk, False)
    bundle_assets = inspect(bundle, True)
    assert apk_assets == bundle_assets, "APK and AAB assets differ"
    print("Non-artwork APK and AAB have matching assets and required notices")
    if len(sys.argv) == 4:
        with_images = pathlib.Path(sys.argv[3])
        with_assets = inspect(with_images, False, allow_art=True)
        artwork = {name for name in with_assets if name.startswith("assets/art/")}
        assert any(name.endswith(".webp") for name in artwork), "Artwork APK has no images"
        assert "assets/art/catalog-provenance-v1.json" in artwork, (
            "Artwork APK has no provenance manifest"
        )
        shared_assets = {name: value for name, value in with_assets.items() if name not in artwork}
        assert shared_assets == apk_assets, "Shared APK assets differ"
        print(f"Artwork APK has {len(artwork)} artwork assets and matching shared assets")


if __name__ == "__main__":
    main()
