#!/usr/bin/env python3
"""Create non-game source records for catalog size and device lookup benchmarks."""

import argparse
import hashlib
from pathlib import Path

from build_catalog import compact


def synthetic_source(count):
    if not 1 <= count <= 100_000:
        raise ValueError("synthetic game count must be 1-100000")
    games = []
    for index in range(count):
        digest = hashlib.sha256(f"kairo98-synthetic:{index}".encode()).hexdigest()
        games.append({"contentIds": [f"sha256-hdi-v1:{digest}"],
                      "title": f"Synthetic Game {index:05d}",
                      "machine": {"baseClockTenthsMHz": 25},
                      "controller": {"profile": "standard-v1", "bindings": []}})
    return {"schemaVersion": 1, "datasets": [{"id": "synthetic-performance",
            "provenance": {"source": "Kairo98 deterministic generated fixture",
                           "license": "Project-owned test fixture",
                           "attribution": "Kairo98 project"},
            "games": games}]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--games", type=int, default=10_000)
    args = parser.parse_args()
    args.output.write_bytes(compact(synthetic_source(args.games)))
    print(f"{args.games} synthetic records written to {args.output}")


if __name__ == "__main__":
    main()
