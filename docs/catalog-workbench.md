# Local catalog workbench

The expanded PC-88/PC-98 inventory, 80 manually reviewed game records, review commands, and held artwork are documented in [catalog research](../catalog/research/README.md).

`tools/catalog_workbench.py` starts research from the local LaunchBox PC-98 metadata snapshot. It **does not accept fuzzy matches automatically** or publish game data. Its outputs go under the ignored `.downloads/launchbox/` directory. The ROM tree and copied LaunchBox images are also ignored.

The Android device currently supplies `Metadata/NEC PC-9801.xml`, `Data/Platforms/NEC PC-9801.xml`, and `Images/NEC PC-9801/` from `/storage/3338-3231/LaunchBox/`. The workbench reads the metadata XML; platform library records are hints for later review, never a source of truth. The official [LaunchBox Games Database](https://gamesdb.launchbox-app.com/) supplies live pages for manual verification. The [LaunchBox media guide](https://feedback.launchbox-app.com/en/help/articles/7997304-using-games-database-media-in-launchbox) explains the local metadata and image layout.

Run from the repository root, using an installed Python 3.8 or newer:

```sh
python tools/catalog_workbench.py scan \
  roms/PC-98/Translated/nec-pc-9801-translations \
  .downloads/launchbox/translated-index.json
python tools/catalog_workbench.py queue \
  .downloads/launchbox/translated-index.json \
  .downloads/launchbox/metadata.xml \
  .downloads/launchbox/translated-queue.json
python tools/catalog_workbench.py query \
  .downloads/launchbox/metadata.xml "Night Slave" \
  --images .downloads/launchbox/images
python tools/catalog_workbench.py query \
  .downloads/launchbox/metadata.xml "Night Slave" --id 156056 \
  --images .downloads/launchbox/images
```

`scan` hashes the extracted HDI stream, so ZIP compression and metadata do not change the `sha256-hdi-v1` ID. It reuses a prior hash only when archive size and modification time match; delete the index to force a rehash. It checks unsafe ZIP paths and the app's HDI size/expansion limits. Floppy-only ZIPs remain `no-hdi`; publisher ZIPs containing game ZIPs remain `nested-zip` until a nested-archive workflow is added. The NeoKobe PC-98 library uses these publisher ZIPs, so **do not** treat the top-level publisher archive as one game. [Issue #33](https://github.com/MrJackSpade/Kairo98/issues/33) tracks floppy-only support.

`queue` groups translated archive variants by a normalized filename hint and lists candidate LaunchBox records. Every group starts `needs-review`, even with a perfect title score. Inspect the source ZIP names and content IDs, search the live game page, check platform/edition/aliases and overview, and open each intended box image and screenshot. Search beyond the suggested candidates when the title differs. Record an identity decision with the *snapshot* DatabaseID and the *live page* URL (these number spaces can differ):

```sh
python tools/catalog_workbench.py review \
  .downloads/launchbox/translated-queue.json \
  .downloads/launchbox/translated-decisions.json \
  nightslave 156056 \
  https://gamesdb.launchbox-app.com/games/details/106056-night-slave \
  --notes "Checked edition, platform, box and gameplay image" \
  --description "An original, manually written description." \
  --box-url https://images.launchbox-app.com/59e76cd5-5c77-40b0-8493-9c9aaeb651f4.jpg \
  --screenshot-url https://images.launchbox-app.com/e5e0175b-fc9e-45a9-a34d-38bd1d11689c.png \
  --image-status visually-checked
python tools/catalog_workbench.py report \
  .downloads/launchbox/translated-queue.json \
  .downloads/launchbox/translated-decisions.json \
  .downloads/launchbox/metadata.xml
```

Image sources are recorded only when an image has actually been selected. Use `flagged` for wrong-game art, possible nudity, or any uncertain image. Keep such art out of Git and request censorship/review before publishing it. `visually-checked` records that a human looked at the selected images; it does **not** grant redistribution rights. Each candidate image needs its own source link. Unselected images remain unreviewed. The draft report merges multiple reviewed title groups under one canonical game ID and unions their HDI hashes. It includes the source overview **for local review only** and the separately authored description. Neither text nor art is packaged by this tool.

The first pilot scanned 173 translated ZIPs: 147 had at least one HDI and 26 were floppy-only. It produced 130 title groups. Manual checks found the cached `Box - Front - Full/Farland Story.jpg` depicts *Farland Story IV: The Silver Wings*, despite its filename; that image is flagged and not selected for *Farland Story*. The Night Slave box and gameplay screenshot matched the live [Night Slave page](https://gamesdb.launchbox-app.com/games/details/106056-night-slave) on inspection. These are two pilot decisions, not a validation of the rest of the cache.

No LaunchBox media or descriptions are committed or shipped. Before a reviewed draft can become a release entry, confirm rights for both the free GitHub and paid Play editions, replace source overviews with original prose, inspect each chosen image for nudity and wrong-game content, and extend the app catalog schema to support descriptions. The current catalog source format supports shared metadata across multiple `contentIds`, but not descriptions. `tools/import_art.py` separately enforces explicit redistribution and derivative permissions for both editions; see [art rights](art-rights.md).
