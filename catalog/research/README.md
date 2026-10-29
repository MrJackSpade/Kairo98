# Catalog source data

This directory contains source records used to build Kairo98's hash-keyed game catalog and name index. Records link game identities, alternate titles, descriptions, media references, and disk content IDs. A catalog match describes a title; it does not establish that a particular disk boots.

[`tools/catalog_research.py`](../../tools/catalog_research.py) assembles the research records into `games-v1.json`, from which the shared generator (`shared/tools/app_catalog.py`) builds the catalog. `artwork-provenance-v1.json` records each bundled image's source and checksum. [Catalog behavior and schema](../../docs/game-catalog.md) describe matching and updates.

Game disks and private research downloads are not part of this repository.
