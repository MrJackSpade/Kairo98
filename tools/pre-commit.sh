#!/bin/sh
# Kairo98's research and the catalog the shared generator builds from it match what a commit stages
# in the working tree (tools/catalog_research.py, shared/tools/app_catalog.py).
set -eu

for python in py python3 python; do
    if command -v "$python" >/dev/null 2>&1; then
        [ "$python" = py ] && python="py -3"
        $python tools/catalog_research.py --check
        exec $python shared/tools/app_catalog.py . --check
    fi
done

echo "Python 3 is required to verify generated catalog files." >&2
exit 1
