"""Kairo98's catalog as the shared generator builds it, for its research tools and tests: the name
index from catalog/parts, and the name form a file is looked up by (shared app_catalog.lookup_name)."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "shared/tools"))
sys.path.insert(0, str(ROOT / "tools"))
import app_catalog
import catalog_config
from catalog_parts import merge

_EXTENSIONS = re.compile(catalog_config.MEDIA_EXTENSIONS, re.I)


def lookup_name(value):
    """A name's index form, as the app looks a file's name up."""
    return app_catalog.lookup_name(value, _EXTENSIONS)


def documents():
    """The generated catalog's documents, every part merged."""
    return merge(*(json.loads((ROOT / f"catalog/parts/{part}.json").read_bytes())["documents"]
                   for part in ("data", "controls", "art", "art.nsfw", "data.nsfw")))


def name_index():
    return documents()["name-index-v1.json"]
