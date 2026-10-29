#!/bin/sh
# Kairo98's own checks, after the shared ones (shared/tools/check-app.sh, which checks the catalog the
# shared generator builds): the research it is built from is assembled.
set -e
python3 tools/catalog_research.py --check
