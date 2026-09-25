# Typed startup choices and screen hashes

Kairo98 offers a labeled choice before boot for cataloged startup questions answered by a letter or digit. The label does not expose the guest key. Questions answered with arrows and Enter remain in the game. A player can use **Play manually** to start without answering the current or later questions.

The Android host samples the complete 640×400 RGB565 guest framebuffer about every 100 ms while a startup queue is active. `fnv1a64-rgb565-v1` starts at `14695981039346656037`, XORs each pixel's low byte then high byte in row order, and multiplies by `1099511628211` after each byte, wrapping at 64 bits. Catalog hashes are sixteen lowercase hexadecimal digits. Each queue step accepts any listed full-frame hash; both blinking-cursor phases are listed when present. A monotonically increasing sample serial prevents a previous frame from satisfying a later step. Hashing stays in the Android host; the PC-98 execution core only publishes pixels and receives ordinary key events.

The [startup profiles](../catalog/startup-profiles-v1.json) are keyed by extracted image content ID. The [catalog builder](../tools/build_review_catalog.py) merges them into the same sharded metadata as titles and artwork. `startupChoices` contains ordered questions, each with accepted `screenHashes` and labeled one-character options. An option may instead contain `steps`, each with its own hashes and one-character input, for a selection on a later menu page. The player still sees one episode label. `launch.screenHashes` contains one accepted hash set per fixed DOS command. User catalog additions and overrides can replace `startupChoices` as one field. Bad records are rejected by both the builder and the Android catalog reader.

For a selected game, the app collects its choices before boot. It queues fixed commands first and selected answers afterward. Each item waits for a fresh matching hash, then sends the cataloged character and optional Enter through the normal PC-98 input router. A timeout leaves the guest running and offers **Retry**, **Open keyboard**, or **Restart**. Stop, reset, a new game, and leaving the activity cancel pending input. A game without choices starts directly. The app can still use its DOS-prompt signal for a legacy fixed command that has no measured hashes yet; the measured GaoGao commands use only screen hashes.

`diskSwaps` uses the same full-frame hash sampler throughout a game session. Each catalog rule identifies a floppy by extracted-content hash, drive A or B, prompt hashes, and an optional follow-up key and Enter. The app checks that every referenced floppy exists when Play is tapped. On a match it prepares an app-private working copy, mounts it through the normal floppy command, then sends the optional key. A prompt must disappear for three samples before the same rule can fire again. Missing or unreadable disks leave the game running and offer Retry or manual floppy selection. Games that need two disks present from the first instruction instead use the `media.floppyB` catalog role; Reserve is the verified example.

## Retroid observations

The first listed path for each menu family was pressed on the device. The hashes were captured from the native guest buffer, not an Android screenshot. A repeated cold boot confirmed GaoGao's first DOS prompt hashes.

| Image or menu family | Cataloged choices | Observed result |
| --- | --- | --- |
| Cyber Arms Val-Kaizer | Start game, then New game | Two separate hashed prompts reached dialogue. Sound setup remains manual. |
| Dragon Knight and Easy Mode | Game, Bonus content | Game advanced after one digit. |
| Exciting Milk combined | Episode 1, Episode 2 | Episode 1 reached its arrow-operated game menu. |
| Farland Story | Start game, Watch opening | Start game reached the arrow-operated sound selection. |
| Foxy | Game, Special disk, Character edit | Game reached its intro. Other branches may need companion media. |
| Jewel Bem Hunter Lime combined, revisions 1.0 and 1.1 | Episodes 1–12 | Episodes 7–12 select the second page with `9`, then the episode digit after its screen hash matches. |
| Night Slave, BabaJeanmel translation | Regular driver, Disable MIDI | Regular driver reached the intro. This image boots directly to the driver question. |
| Rance II + Hint Disk, revisions 1.5 and 2.0 | Game, Hint Disk | Game reached its intro. Hint Disk may need companion media. |
| Starfire | 16-color, 256-color | The 16-color choice automatically reached the title from a fresh boot. |
| GaoGao! 2nd | `CD PW`, then `GAO2` | Both distinct DOS prompt hashes matched; the game artwork appeared automatically. |
| Cybernetic Hi-School Version 2.0 | Disk 2 in drive B at its insert prompt | Both blinking prompt hashes matched; the game advanced to its input selection without a key. |

Night Slave's other translated image boots straight into its intro, so it has no prelaunch choice. Neither translated image reached a DOS prompt in a command-suppressed boot, so the stale `NS` catalog command was removed. The first image alone has the driver-choice profile.

The alternate choices were transcribed from the displayed menus; only the paths described in the observation column were advanced on the device. A full-frame match is deliberately exact. Changes to guest font rendering, BIOS output, or a game's boot screen can require recapturing that image's hashes. The timeout recovery keeps the keyboard available in that case.

For local capture, a debug build accepts `kairo98.traceScreenHashes=true` and writes `files/startup-hash-trace.txt` in private app storage. `kairo98.skipStartupChoices=true` and `kairo98.skipLaunchCommands=true` allow the two kinds of input to be withheld during calibration. These flags have no effect in a distributable build. Captures and game images remain ignored local files.
