#!/bin/sh
set -eu

if command -v py >/dev/null 2>&1; then
    exec py -3 tools/check_generated_catalog.py
elif command -v python3 >/dev/null 2>&1; then
    exec python3 tools/check_generated_catalog.py
elif command -v python >/dev/null 2>&1; then
    exec python tools/check_generated_catalog.py
fi

echo "Python 3 is required to verify generated catalog files." >&2
exit 1
