# Kairo98 game catalog

Kairo98 identifies HDI files with `sha256-hdi-v1:` and supported floppy images with `sha256-fd-v1:`, followed by the lowercase SHA-256 digest of the extracted disk bytes. ZIP compression, member order, and sidecar files do not change a disk's identity. A ZIP with multiple disks produces separate library entries. Hash matches take priority over fallback title matches.

The library reads HDI and supported FDI, D88, NFD, FDD, DCP/DCU, HDM, and XDF floppies. A game can require a boot floppy or disk in drive B. The app checks required media before play and offers manual disk changes during a session. Working copies remain in app-private storage so guest writes do not change the source disk.

## Metadata and overrides

Catalog schema version 1 uses a `games` object keyed by content ID. Records can contain a title, description, aliases, artwork references, machine settings, controller bindings, media roles, launch commands, startup choices, disk-swap rules, and input mode. A `hidden` support disk remains available for swaps but does not appear as a playable entry.

A catalog command is bounded guest text sent as PC-98 key events. It is never an Android process or host shell command. [Startup choices and screen hashes](startup-choices.md) describe guarded commands, menu answers, and automatic disk swaps.

User catalog additions and explicit overrides live in app-private storage. Overrides replace individual metadata fields; removing an override exposes the latest catalog value. The library cache records source locations and content IDs, not game bytes, and can be rebuilt by scanning the selected folder.

## Updating the catalog

The bundled catalog is sharded by content ID. Kairo98 also checks [the online metadata snapshot](../catalog/online-v1.json) and offers **Update game catalog** in the library menu. An invalid or unavailable update leaves the existing catalog in place. Artwork downloads are separate. Lookup order is bundled catalog, downloaded catalog, user additions, then explicit overrides.

The source records are in [catalog research](../catalog/research/README.md). To generate the Android shards and online metadata snapshot after reviewing a source change:

```sh
python tools/build_catalog.py catalog/source-v1.json kairo98/src/main/assets/catalog
python tools/build_online_catalog.py catalog/source-v1.json kairo98/src/main/assets/catalog/name-index-v1.json catalog/online-v1.json
```

The builder validates identifiers, field bounds, paths, and collisions. Catalog source links and art provenance do not grant redistribution rights; see [licensing](licensing.md) and [artwork rights](art-rights.md).
