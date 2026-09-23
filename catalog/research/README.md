# Game catalog research

`reviewed-v1.json` is a **research ledger**, not the Android app's shipped catalog. Every entry corresponds to a manually checked LaunchBox game page and one or more on-disk game groups. It has a canonical title, any LaunchBox aliases, an independently written short description, one chosen box-front URL, and one chosen gameplay screenshot URL. The `contentIds` identify extracted HDI contents where the translated PC-98 archive has been hashed; other groups still need content hashing and import support.

Image URLs are source references, not permission to redistribute. No LaunchBox art, ROM, or source overview text is tracked here. A `checked` image was opened and checked for game/platform identity and visible content. A `flagged` image is **on hold**, even though its source URL is retained for review. See [flagged media](flags.md). No image should enter either the free or paid app without rights and content review.

## Progress and target

The current realistic milestone is **100 manually reviewed games**. It is complete: 40 PC-88 and 60 PC-98 records cover 124 on-disk groups. Twenty Japanese/English PC-98 translation pairs and two A-Train original/reissue pairs share records, with the English HDI hashes where available. Of 200 possible box/screenshot selections, 183 are checked and 17 flagged; none are packaged. One reviewed item, *Derby Stallion Expert Kit*, is a non-standalone expansion and will need a base-game dependency before launch support.

This is a small portion of the full library. The local inventory contains 2,037 PC-88 folders and 3,308 Japanese PC-98 game folders inside publisher ZIPs, plus 130 translated PC-98 title groups. Some folders may be compilations, utilities, or duplicates. Exact LaunchBox title/alias candidates exist for 884 PC-88 groups and 2,162 PC-98 groups. An exact match is only a review lead: the rest need alternate-title searches, and all matches need live page and image verification. Continue with LaunchBox until its useful records are exhausted, then evaluate other sources. No claim is made that the unreviewed games are cataloged.

The current Android catalog schema does not yet carry descriptions or PC-88 content IDs. Moving a reviewed record into the shipped app catalog requires schema/import work, media rights, and replacement or clearance of every flagged image.

## Rebuild the local review queue

Run from the repository root with Python 3.8 or newer. Input archives and all outputs under `.downloads/` and `roms/` are git-ignored.

```sh
python tools/extract_launchbox_snapshot.py \
  .downloads/launchbox/Metadata.zip .downloads/launchbox
python tools/inventory_neokobe.py \
  roms/PC-88/Untranslated/NeoKobe-2016 \
  roms/PC-98/Untranslated/NeoKobe-2017 \
  .downloads/launchbox/neokobe-inventory.json
python tools/prepare_catalog_backlog.py \
  .downloads/launchbox/neokobe-inventory.json \
  .downloads/launchbox/translated-queue.json \
  .downloads/launchbox/pc88-snapshot.json \
  .downloads/launchbox/pc98-snapshot.json \
  .downloads/launchbox/review-backlog.json
python tools/catalog_review.py validate \
  .downloads/launchbox/review-backlog.json \
  .downloads/launchbox/pc88-snapshot.json \
  .downloads/launchbox/pc98-snapshot.json \
  catalog/research/reviewed-v1.json
python tools/catalog_review.py coverage \
  .downloads/launchbox/review-backlog.json \
  catalog/research/reviewed-v1.json
```

`Metadata.zip` comes from the official [LaunchBox database dump](https://gamesdb.launchbox-app.com/Metadata.zip). The [LaunchBox Games Database](https://gamesdb.launchbox-app.com/) provides the live pages. For each proposed entry, compare the live page's title and platform with the disk group, read the game's description to write original prose, and open both selected images. Mark an absent image `missing`, a questionable one `flagged`, and record the reason in `notes`. A snapshot DatabaseID and a public page URL may use different numbers; check the live page rather than constructing an unverified link.
