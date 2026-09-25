# Game catalog matches

`matches-v1.json` is the consolidated research catalog for PC-88 and PC-98 games found in the local disk inventory. `tools/build_review_catalog.py` derives the Android app's shipped hash catalog and name index from it. Each record links a LaunchBox game to one or more on-disk groups, with the source title, aliases, page link, any existing original description, and at most one cover and one screenshot URL. `contentIds` contain extracted HDI or floppy hashes where available; many PC-88 and Japanese PC-98 groups still need content hashing and import support.

`descriptions-v1.json` contains original, concise descriptions for all 134 matched translated PC-98 records. It replaces the early single-sentence drafts when the catalog is built. Each record's `pageUrl` in `matches-v1.json` is its primary research link; `additionalSources` records the other pages used where the LaunchBox snapshot had no overview or lacked gameplay detail. The text describes premise and play without copying the source overview. The builder rejects any translated record that lacks a description. PC-88 and untranslated PC-98 descriptions are a separate follow-up in issue #52.

The first 195 game records retain manually selected source links and independently written descriptions. The alias lists preserve LaunchBox alternate names and add distinct names from linked on-disk groups; these local aliases are search names, not claims about the official release title. Most additional records were joined by an exact normalized disk title or LaunchBox alias from a local LaunchBox metadata snapshot. Another 420 disk groups were linked by explicit title, edition, translation, publisher, series-number, parenthetical-name, cross-platform, subtitle, or episode evidence. The PC-98 homebrew archive groups files by creator, so this backlog now separates the game titles inside each ZIP filename; automatic exact joins from that archive check source creator against LaunchBox developer or publisher credits when available. Four distinctive exact-title matches were chosen manually despite different creator credits in the folder and LaunchBox record; those credit conflicts still need correction in the final catalog pass. These are identity candidates for final catalog correction. No image was opened or checked as part of the bulk join. The catalog has no approval, pending, or flagged state. An empty image URL means the source has no chosen image in that category.

Selected image URLs can be downloaded for local review with `python tools/download_catalog_images.py catalog/research/matches-v1.json .downloads/catalog-images`. The script is resumable and writes an ignored `index.html` gallery and `manifest.json`; it does not inspect image content or add images to Git.

The ignored `.downloads/catalog-height360` gallery contains all 6,299 selected images for 3,468 LaunchBox records in 215,201,448 bytes, with a 360-pixel maximum height. Its manifest retains each original LaunchBox image URL, and clicking a thumbnail in the gallery opens that larger source image. Rebuild it from the original downloaded galleries with `python tools/build_catalog_review.py .downloads/catalog-height360 .downloads/catalog-images .downloads/catalog-unmatched-images --max-height 360 --webp`. The builder retains smaller originals that already meet the cap. These review copies remain separate from bundled Android art assets.

The local gallery currently contains all 6,123 selected images for the 3,342 matched records. On 2026-09-24, `tools/enrich_catalog_media.py` fetched and checked all 3,342 exact game pages against the catalog titles. The 526 pages whose cached metadata lacked a cover or screenshot supplied no additional image in either missing category. All source pages and the gallery stay under ignored `.downloads/`.

Image URLs are source references, **not** redistribution permission. The 360-pixel images were approved for inclusion in the development app on 24 September 2026. The bundled assets retain their source URLs and file hashes in `app/src/withImages/assets/art/catalog-provenance-v1.json`; no ROM, game data, or LaunchBox overview text is packaged. The license audit remains a release gate; see [art rights](../../docs/art-rights.md).

## Coverage

The current match catalog has **3,342 game records** linking **3,535 of 6,363** on-disk groups: 1,015 of 2,040 PC-88 groups and 2,520 of 4,323 PC-98 groups. The counts separate CD or floppy volumes from five NeoKobe folders, five floppy games from the *Disc Station Vol. 10* CD compilation, and individual games from 550 creator folders in the PC-98 homebrew archive. Combined releases, demos, and extras retain their parent group. The newly linked PC-88 *Space War-88* and *Star Fleet-B* folders contain extras only, so these metadata matches do not imply playable disks. The Japanese and translated disk groups for *Lightning Warrior Raidy* and *Love Potion* share one catalog record each, with the Japanese titles kept as aliases. Both PC-88 and PC-98 disk titles for *Tenshi-tachi no Gogo II: Bangaihen* now share their respective platform records; PC-88 *Saotome Military Academy* and *Nukenin Densetsu Bangaihen* were also matched through title and publisher evidence. PC-88 *Princess wa Story Girl* and PC-98 *Super Daifugo Gakuen* are identity candidates for the corresponding LaunchBox titles with the same publishers; their differing title words are retained as aliases for the final catalog correction. Three dated *Oshioki Kirai 2* disks share one LaunchBox record, and *RPG Wei Wu Shu Shi* links to the same Soft Plan release listed as *RPG Gi Go Shoku Shi*. The Techno Grard *Guynarock: Galactic Guardian* disk group now links to the original *Ginsei Senshin Guynarock* instead of the later Sogna *Guynarock R*. The Dexter *Ninja-kun* disk now links to *Majou no Bouken* rather than Micro Cabin?s release; View Tech?s *Dragon Princess* is separated from Koei?s *Dragon & Princess*; and Heart Soft?s *Falla no Jikekkai* uses the richer *Faara no Jikekkai* record. *Pro Yakyuu Fan* base and *Yousei Gibs* power-up kit disks are now separate groups. Seven PC-88 title collisions were unlinked: Champion Soft *Misty*, Dream7 *Labyrinth*, Apollo Technica *Tsumeshougi*, Run Tech *The Dragon Princess*, Team-DS *G Senryaku*, S. Miyoshi *Koikoi*, and Noripy *Tetris* have creator credits that conflict with the corresponding LaunchBox records. The Soft Studio Wing *Shiro to Kuro no Densetsu Series - Destruction* demo now shares the *Destruction Gekan* record with its full Gekan disk; the public game page confirms the series alias and creator. The Champion Soft *Planetarium Orion* disk links provisionally to *Orion: Planetarium Part II*; the omitted ?Part II? remains a point for final catalog correction. Two *Disc Station Vol. 10* floppies link to *Rude Breaker* and *Runner's High*; its *Nazo Puyo* floppy remains unlinked because its identity with the standalone 1994 release is unconfirmed. It has 3,112 cover URLs, 3,011 screenshot URLs, and 1,587 aliases. Original box fronts and gameplay screenshots take priority; front-cover fanart and title-screen and game-select screenshots fill blanks. The seven multiple-candidate exact matches have explicit selections. Of 130 translated PC-98 title groups, 128 are linked. *Exciting Milk* and *Jewel Bem Hunter Lime* each have a combined HDI without one corresponding LaunchBox game record; their 14 separately hashed episode HDIs now have individual source groups and are linked to their episode records. Another 2,828 groups remain unlinked; most have no exact title or alias candidate in this snapshot. The remaining four exact-title homebrew candidates have conflicting creator credits; *Mayumi* and *Twins* are known title collisions, so all four are left unlinked pending stronger identity evidence. Some on-disk groups may be compilations, utilities, or duplicates; a catalog match does not establish that an image boots in the emulator.

LaunchBox uses different numeric IDs in its downloadable metadata and public game URLs. The catalog now uses exact canonical links from every page of the public [PC-88 listing](https://gamesdb.launchbox-app.com/platforms/games/192-nec-pc-8801) and [PC-98 listing](https://gamesdb.launchbox-app.com/platforms/games/193-nec-pc-9801), retrieved on 2026-09-24. All 1,080 PC-88 and 2,388 PC-98 listing titles mapped uniquely to the corresponding metadata snapshot titles. This corrected two numeric page IDs: *Battle Block Alfin* and *The Tower of Zarbartz*. The local HTML and parsed listings are ignored; rerun the scraper and `sync-pages` to refresh these links. The direct links establish source identity, but do not verify media or disk behavior.

## Remaining LaunchBox gaps

Of the snapshot's 3,468 platform records, 3,342 have an on-disk match in this catalog. The remaining 126 are 77 PC-88 and 49 PC-98 records. Five PC-98 records have an exact normalized title or alias overlap with an already matched identity (*Ikazuchi no Senshi Raidy*, *Etsuraku no Gakuen*, *Falla no Jikekkai*, *Danger Angel*, and *Gakuen Sodom*), so adding them would duplicate a game. A search of every unmatched disk variant path for the other source titles and aliases found no unambiguous game match: *Elite Plus* and *All About Mark-Flint* appear only in extras folders, *Card Force for Child* has a different edition name, and *ZuraZura Island* names the creator folder for *Pazura*. Creator-qualified similarity checks likewise surfaced different editions or compilations, including *Soko-Ban 2* versus *Soukoban 1 & 2 with Editor*, *Magnet World 2* versus *Magnet World*, and *The Queen of Duellist* versus its 5-in-1 collection. An overview-text scan found *Tokuichi Tiger* only as a playable demo inside *CD Takarabako*. These checks do not prove that every remaining disk is absent from LaunchBox; they explain why no further identity link is currently supported by the cached data.

Of the 2,828 unmatched disk groups, 1,312 PC-98 groups come from the doujin/homebrew archive; another 173 PC-98 groups are utilities, 33 are OS disks, and 10 are BIOS disks. On PC-88, 611 unmatched folders are labeled doujin, 30 are labeled compilations, 64 utilities, 26 OS disks, and 17 BIOS disks. The remaining groups include demos, expansions, extras, and named games that need another metadata source or direct catalog correction.

## Rebuild

Run from the repository root with Python 3.8 or newer. Input archives and all outputs under `.downloads/` and `roms/` are git-ignored.

```sh
python tools/extract_launchbox_snapshot.py \
  .downloads/launchbox/Metadata.zip .downloads/launchbox
python tools/inventory_neokobe.py \
  roms/PC-88/Untranslated/NeoKobe-2016 \
  roms/PC-98/Untranslated/NeoKobe-2017 \
  .downloads/launchbox/neokobe-inventory.json
python tools/catalog_workbench.py scan \
  roms/PC-98/Translated/nec-pc-9801-translations \
  .downloads/launchbox/translated-index.json
python tools/catalog_workbench.py queue \
  .downloads/launchbox/translated-index.json \
  .downloads/launchbox/pc98-snapshot.json \
  .downloads/launchbox/translated-queue.json
python tools/prepare_catalog_backlog.py \
  .downloads/launchbox/neokobe-inventory.json \
  .downloads/launchbox/translated-queue.json \
  .downloads/launchbox/pc88-snapshot.json \
  .downloads/launchbox/pc98-snapshot.json \
  .downloads/launchbox/match-backlog.json \
  --translated-index .downloads/launchbox/translated-index.json
python tools/scrape_launchbox_platform.py pc88 \
  .downloads/launchbox/pc88-live-listing.json
python tools/scrape_launchbox_platform.py pc98 \
  .downloads/launchbox/pc98-live-listing.json
python tools/catalog_match.py sync-pages \
  .downloads/launchbox/match-backlog.json \
  .downloads/launchbox/pc88-snapshot.json \
  .downloads/launchbox/pc98-snapshot.json \
  catalog/research/matches-v1.json \
  .downloads/launchbox/pc88-live-listing.json \
  .downloads/launchbox/pc98-live-listing.json \
  catalog/research/matches-v1.json
python tools/catalog_match.py validate \
  .downloads/launchbox/match-backlog.json \
  .downloads/launchbox/pc88-snapshot.json \
  .downloads/launchbox/pc98-snapshot.json \
  catalog/research/matches-v1.json
python tools/catalog_match.py coverage \
  .downloads/launchbox/match-backlog.json \
  catalog/research/matches-v1.json
python tools/enrich_catalog_media.py \
  catalog/research/matches-v1.json \
  .downloads/launchbox \
  .downloads/launchbox/media-enriched.json \
  --all-pages
python tools/download_catalog_images.py \
  catalog/research/matches-v1.json \
  .downloads/catalog-images
```

`Metadata.zip` comes from the official [LaunchBox database dump](https://gamesdb.launchbox-app.com/Metadata.zip). The [LaunchBox Games Database](https://gamesdb.launchbox-app.com/) supplies the public game pages. The platform scraper reads 11 PC-88 and 24 PC-98 listing pages and caches their exact game links under `.downloads/`. `tools/catalog_match.py consolidate` can repeat the exact join from the current catalog without losing selected group links. `tools/catalog_match.py choose` applies group-to-DatabaseID corrections from a JSON object. `tools/catalog_match.py add-live` adds a record from a direct public game page when the snapshot lacks it; these entries use `live:<page ID>` as `databaseId`.
