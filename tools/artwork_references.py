"""Kairo98's catalog artwork references: PC-88 and PC-98 platform codes, self-hosted images
served from this repository. The format lives in shared/tools/launchbox_artwork.py."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "shared/tools"))
from launchbox_artwork import References

_REFERENCES = References("Kairo98", (88, 98))
SELF_HOSTED_BASE = _REFERENCES.self_hosted_base
expand = _REFERENCES.expand
compact = _REFERENCES.compact
self_hosted = _REFERENCES.self_hosted
