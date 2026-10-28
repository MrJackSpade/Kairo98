#!/bin/sh
# Kairo98's own checks, after the shared ones (shared/tools/check-app.sh): its catalog parts and
# packages match their generator.
set -e
python3 tools/build_catalog_packages.py
git diff --exit-code -- catalog/parts catalog/optional/kairo98-art-v2.nsfw.meta.json catalog/optional/kairo98-art-v2.nsfw.zip
