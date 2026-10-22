# Kairo98 game catalog

[← Kairo98](../README.md) · [All documentation](../README.md#documentation)

**In short:** Kairo98 recognizes your games by the contents of their disks and fills in the title, description, artwork, and launch settings from its catalog. Unrecognized games still appear and can still be played. **Menu › Manage library › Update catalogs** fetches the latest catalog without an app update, and any title or setting you change yourself always wins over the catalog.

The rest of this page describes matching, the catalog format, and how the catalog is generated.

- [Disk identity and formats](#disk-identity-and-formats)
- [Metadata and overrides](#metadata-and-overrides)
- [Updating the catalog](#updating-the-catalog)
- [Reviewed display language](#reviewed-display-language)
- [Compact artwork references (0.9.5)](#compact-artwork-references-095)

## Disk identity and formats

Kairo98 identifies HDI files with `sha256-hdi-v1:`, supported floppy images with `sha256-fd-v1:`, and CDs with `sha256-cd-v1:`, followed by the lowercase SHA-256 digest of the extracted disk bytes. ZIP compression, member order, and sidecar files do not change a disk's identity. A ZIP with multiple disks produces separate library entries. Hash matches take priority over fallback title matches.

The library reads HDI and supported FDI, D88, NFD, FDD, DCP/DCU, HDM, and XDF floppies, and CloneCD images. A CD is its `.ccd` file with the `.img` of the same name beside it, in a folder or a ZIP; its content ID hashes the `.img`. The machine has an IDE CD-ROM drive, and the disc goes in with the game that runs it: the CD its catalog record names with the `cd` media role, or the one CD in the same ZIP or folder. PC-98 CDs do not boot, so starting a CD starts the disk its `bootDisk` role names, or the one hard disk or floppy beside it. **Menu › Disks › CD-ROM** changes or ejects the disc. A game can require a boot floppy or disk in drive B. The app checks required media before play and offers manual disk changes during a session. Working copies remain in app-private storage so guest writes do not change the source disk.

## Metadata and overrides

Catalog schema version 1 uses a `games` object keyed by content ID. A disk record names its game's shared record, in the name index, with `"game": "pc98:<id>"`, and stores only its own fields, such as `setDisks`, `media`, startup data, or a title of its own; those override the shared record. A game's title, description, aliases, artwork and heart are stored once for all its disks. Every catalog layer adds its copy of the shared record beneath its disk records. Records can contain a title, description, aliases, artwork references, machine settings, controller bindings, media roles, launch commands, startup choices, disk-swap rules, and input mode. A `hidden` support disk remains available for swaps but does not appear as a playable entry.

A multi-disk set appears as one entry. The set's entry point, usually disk 1 or the set's only hard disk, carries `setDisks`: the content IDs of the set's other disks. While the entry point is in the library, those disks get no rows of their own. Boot floppies, drive B, disk swaps, and the Floppy A/B menu still use them. Without the entry point, they appear normally.

`tools/import_disks.py` records disks, sets and CDs. `library` reads a device's `library-v2.bin`. `neokobe` reads a `catalog_workbench.py scan` of the extracted NeoKobe PC-98 tree and adds every disk in a linked inventory group to that game's research entry. Both group the images in each ZIP or release folder that share a game name apart from disk labels such as "(Disk 2)", "(System disk)" or "(User disk)". A set starts from its hard disk, then a boot, system, program, master or game disk, then the lowest numbered or lettered disk. Untagged copies come before tagged copies, alternates, and unmodified originals. A set with no single best disk is reported, not recorded. Existing `setDisks` are merged, since releases can share an entry point. In a NeoKobe game, "[CD floppy]" boot disks and "(CD version)" installs, or its hard disk releases when it has only CD and hard disk releases, get the CD release with the same qualifier, or the game's one CD release, as their `cd` media, and the CD gets no row beside them.

A catalog command is bounded guest text sent as PC-98 key events. It is never an Android process or host shell command. [Startup choices and screen hashes](startup-choices.md) describe guarded commands, menu answers, and automatic disk swaps.

User catalog additions and explicit overrides live in app-private storage. Overrides replace individual metadata fields; removing an override exposes the latest catalog value. The library cache records source locations and content IDs, not game bytes, and can be rebuilt by scanning the selected folder.

## Updating the catalog

The bundled catalog is sharded by content ID. Kairo98 checks the small [revision file](../catalog/core-v3.meta.json) before fetching [the online metadata snapshot](../catalog/core-v3.json), and offers **Update catalogs** in Menu › Manage library. An unchanged SHA-256 skips the catalog download. An invalid or unavailable update leaves the existing catalog in place. Artwork downloads are separate. Lookup order is bundled catalog, downloaded catalog, user additions, then explicit overrides.

The source records are in [catalog research](../catalog/research/README.md). To generate the Android shards and online metadata snapshot after reviewing a source change:

```sh
python tools/build_catalog.py catalog/source-v1.json kairo98/src/main/assets/catalog
python tools/build_online_catalog.py catalog/source-v1.json kairo98/src/main/assets/catalog/name-index-v1.json catalog/core-v3.json
```

Games LaunchBox lacks can come from VNDB: `tools/add_vndb_games.py` reads `catalog/research/vndb-additions-v1.json`, which names each NeoKobe folder's VNDB visual novel or release with an original description, and adds its research entry, adult review, and core-review exclusion from the VNDB dump. VNDB's descriptions and images are not covered by the dump's license, so neither is copied. `tools/add_mobygames_games.py` does the same from a `shared/tools/mobygames.py` dump with `catalog/research/mobygames-additions-v1.json`: MobyGames records are reference only, descriptions are original, and the PC-98 front cover and a mid-game screenshot are encoded at most 360 pixels high into `catalog/artwork/art/catalog/moby/` and served from this repository, a source the app accepts besides LaunchBox. A game MobyGames gives the Adult theme leaves the core catalog. `tools/add_inventory_games.py` reads `catalog/research/inventory-additions-v1.json` for the NeoKobe folders no database describes: each becomes a `neokobe-` entry titled after its folder, or joins an existing entry. Those entries are recognized only by their disks, never by file name. Run `tools/import_disks.py neokobe` after any of these. `tools/build_catalog_packages.py` regenerates everything at once. `core-v2.json`, `online-v1.json` and the v1 adult and artwork packages are frozen for app versions up to 1.1.1, which read records with each game's metadata repeated; they keep that catalog until they update. The builder validates identifiers, field bounds, paths, and collisions. Catalog source links and art provenance do not grant redistribution rights; see [licensing](licensing.md) and [artwork rights](art-rights.md).

## Reviewed display language

`catalog/research/display-text-v1.json` supplies reviewed title and alias spellings
using `♥` within explicit words. The builder applies these only to display fields;
the name index retains original names and also recognizes the edited spellings.
Content IDs, disk names, launch settings, artwork, and user overrides are unchanged.
The existing adult-content review still controls the separate heart tag. A missing
heart is not an age rating. Existing descriptions were reviewed for graphic wording
and retained where they already describe the premise and gameplay in restrained terms.

Regenerate after changing review inputs:

```sh
python tools/build_review_catalog.py --reuse-art
python tools/build_online_catalog.py catalog/source-v1.json kairo98/src/main/assets/catalog/name-index-v1.json catalog/core-v3.json
```

The generated-catalog hook includes the display review input, preventing a later
regeneration from restoring the original display wording.

## Compact artwork references (0.9.5)

Distributed artwork objects contain numeric `platform` (88 or 98) and `game`
asset-group identifiers. Optional `boxArt` and `preview` objects each hold an
image UUID (`id`), `format` (0 for JPG, 1 for PNG), and `revision` (0 for the
ordinary image path, 2 for its r2 variant). No artwork URL or file path is stored
in these records. Empty artwork remains empty, and absent image kinds stay absent.

The URL template lives in `Pc98ArtworkReferences`. Existing local paths are
reconstructed from the asset group and the first twelve hex characters of the
URL's SHA-256, preserving bundled artwork and previously downloaded files. Both
paths and URLs are expanded before field layering, so user overrides and source
tracking keep the same behavior. Legacy paths and approved URLs remain readable.
The generator requires exact round-trip equality and fails on unrepresentable
source data instead of discarding it. Old app versions reject the new snapshot
and keep their current catalog until updated; 0.9.5 reads both representations.
Source research and artwork provenance remain available separately; this format
does not change image-server access permissions.
