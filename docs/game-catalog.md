# Kairo98 game catalog, version 1

The app identifies one playable HDI by `sha256-hdi-v1:` followed by the lowercase SHA-256 hex digest of its **uncompressed HDI bytes**. A standalone HDI and that HDI inside any ZIP have the same ID. ZIP metadata, compression, and sidecar files do not enter the digest. A ZIP with two HDIs yields two library entries and two IDs. This identifier is for matching metadata; the source document URI and ZIP entry name remain separate launch locators.

The shipped catalog and optional user catalog additions use the same JSON envelope:

```json
{"schemaVersion":1,"games":{"sha256-hdi-v1:0000000000000000000000000000000000000000000000000000000000000000":{"title":"Example Disk","aliases":["Example"],"artwork":{"boxArt":"art/example-box.webp","preview":"art/example-screen.webp"},"machine":{"baseClockTenthsMHz":25},"controller":{"profile":"standard-v1","bindings":[]},"media":[{"role":"boot","contentId":"sha256-hdi-v1:0000000000000000000000000000000000000000000000000000000000000000"}],"launch":{"type":"guestCommand","text":"GAME","ready":"dosPrompt","timeoutMs":30000}}}}
```

The shipped asset may be empty. Future catalogs may be split into shards under a manifest, but each shard uses this envelope. Relative artwork paths must stay below the app's approved art directory. Metadata never contains Android intent commands, process commands, or host shell scripts. A `guestCommand` is bounded text sent as PC-98 key events after the selected disk reaches the documented ready state; absent launch metadata means normal boot with no injected keys.

User overrides live in app-private `overrides-v1.json` and have the same `schemaVersion` plus a `games` object keyed by content ID. Each record contains only fields the user changed. Top-level fields (`title`, `artwork`, `machine`, `controller`, `media`, `launch`) replace the corresponding whole field from the base record. Resetting a field deletes that field from the override record; resetting all deletes the override record. This makes a future catalog update visible for every field the user did not override. User catalog additions use `user-catalog-v1.json` and load between shipped defaults and overrides. Unknown fields are ignored for forward compatibility; wrong types, oversized documents, and malformed records are rejected individually without hiding other games.

Lookup order is shipped catalog, user catalog additions, then explicit user overrides. If no resolved title exists, the app derives one from the HDI/ZIP entry filename. If artwork is missing or unreadable, the card displays the resolved title. A source entry and its content ID are not interchangeable: two files can share metadata yet retain separate locations and writable working copies.

The local `library-v1.json` is a separate cache of source locators, document fingerprints, content IDs, and scan errors. It contains no game bytes and can be rebuilt from the ROM tree. A cached hash is reused only when size and modification time are both available and unchanged; the user can force a rehash. Files without a trustworthy fingerprint are hashed again on refresh. JSON writes use an atomic replacement, and unsupported future schema versions leave the existing data untouched while the UI offers recovery.

Schema version 1 is immutable once released. A later incompatible change increments `schemaVersion` and migrates local documents transactionally. The app does not silently reinterpret unknown versions. Catalog files have a 64 MiB cap; local override and cache files have smaller explicit limits in the implementation. Field text and asset paths have bounded lengths. These limits protect startup from malformed user additions and provider data.

Fixtures in `docs/fixtures/catalog/` cover an empty catalog, a known title, an unknown hash, a partial override, and reset after a shipped default update. They contain no commercial game data.

`tools/build_catalog.py` accepts a source JSON file with a `datasets` array. Each dataset includes an ID, `provenance` (`source`, `license`, `attribution`), and `games` records. A game lists one or more `contentIds` for known variants and the fields from the catalog envelope above. The generator validates supported fields, refuses conflicting records that claim the same content ID, and writes sorted two-digit-prefix shards plus `manifest-v1.json`. The app reads the small manifest at startup and loads only the matching shard on first lookup, caching up to eight shards. Generate the packaged index with:

```sh
python tools/build_catalog.py docs/fixtures/catalog/source-v1.json app/src/main/assets/catalog
```

The checked-in source fixture is synthetic. The generated-file manifest retains each dataset's attribution; source provenance and rights must be reviewed before adding a real dataset. `python -m unittest discover -s tools -p 'test_*.py'` checks deterministic output, a 10,000-record synthetic corpus, validation, and duplicate-hash rejection.
