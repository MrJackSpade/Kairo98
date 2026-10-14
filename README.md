<p align="center">
  <a href="https://discord.gg/ZkYQ3bPTTq"><img src="docs/discord-banner.svg" alt="Join the Kairo Discord" width="49%"></a>
  <a href="https://github.com/MrJackSpade/KairoDos"><img src="docs/kairodos-banner.svg" alt="KairoDos: the companion DOS emulator for Android" width="49%"></a>
</p>

# Kairo98

**Play your own PC-98 games on Android phones and handhelds.** Kairo98 gives you a game library with artwork, controller and touch controls, an on-screen PC-98 keyboard, save states, and per-game settings. It runs a pinned [Neko Project 21/W](https://simk98.github.io/np21w/) core with [ymfm](https://github.com/aaronsgiles/ymfm) sound emulation. It is an independent project, not an official release of either core.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.loxifi.kairo98)

## Contents

- [Download](#download)
- [Screenshots](#screenshots)
- [Quick start](#quick-start)
- [Supported disk formats](#supported-disk-formats)
- [Managing your library](#managing-your-library)
- [Controls](#controls)
- [Firmware and fonts](#firmware-and-fonts)
- [Launching from other frontends](#launching-from-other-frontends)
- [Documentation](#documentation)
- [Help and community](#help-and-community)
- [Building from source](#building-from-source)
- [License](#license)

## Download

- **[Google Play](https://play.google.com/store/apps/details?id=com.loxifi.kairo98)** (paid): updates automatically. Buying it supports development.
- **[GitHub Releases](https://github.com/MrJackSpade/Kairo98/releases)** (free): install the APK yourself. The larger `-with-images` APK also bundles catalog artwork.

Both editions are built from the same source and have the same features. Requires an ARM64 device running Android 8.0 or newer.

## Screenshots

| Game library | Game details |
| --- | --- |
| ![Kairo98 game library](docs/screenshots/library.png) | ![Game details in Kairo98](docs/screenshots/game-details.png) |
| **Playing a game** | **On-screen keyboard** |
| ![A game running in Kairo98](docs/screenshots/running-game.png) | ![Kairo98 PC-98 keyboard](docs/screenshots/keyboard.png) |

Games shown in screenshots are user-provided and are not bundled with the app.

## Quick start

1. Install Kairo98 from [Google Play](https://play.google.com/store/apps/details?id=com.loxifi.kairo98) or [GitHub Releases](https://github.com/MrJackSpade/Kairo98/releases).
2. Open Kairo98 and select a folder containing your PC-98 disks. You can change it later in **Menu › Manage library**.
3. Select a game, review its details, and tap **Play**.

Games, operating systems, and firmware are not supplied. Some games need a separate boot disk, a manual disk swap, or [imported firmware](#firmware-and-fonts).

## Supported disk formats

| Type | Formats |
| --- | --- |
| Hard disks | HDI |
| Floppies | FDI, D88, NFD, FDD, DCP/DCU, HDM, XDF |
| Archives | Any of the above inside ZIP files |

Games are recognized by their disk contents, so recompressing a ZIP does not change how it matches. A ZIP holding several disks shows up as separate library entries. Details: [game catalog](docs/game-catalog.md).

## Managing your library

- **Artwork.** **Menu › Manage library** can download missing artwork for games Kairo98 recognizes.
- **Catalog updates.** **Update catalogs** on the same page fetches the latest titles, descriptions, and launch settings without an app update.
- **Save states.** Saved in private app storage by default. To use a folder of your choice, choose **Menu › Configurations › Save folder › Custom folder**. Changing the folder moves your save states; if a slot exists in both places, you choose which copy to keep.
- **Deleting a game file.** Open the game's **Game settings** and choose **Delete game file**. Kairo98 names the file before deleting it; deleting a ZIP removes every game inside it. Saves and settings are kept. If the folder grant is read-only, select the folder again to grant write access.
- **Startup choices.** Some cataloged games ask a menu question before play; Kairo98 can answer it for you or let you **Play manually**. See [startup choices](docs/startup-choices.md).

## Controls

| Action | How |
| --- | --- |
| Open the game menu | Android Back, a left-edge swipe, or the controller Menu/Mode button |
| Show the PC-98 keyboard | Swipe from the right edge |
| Remap a controller | Map buttons and sticks to PC-98 keys, joystick, mouse, or app actions; layouts are saved per game |
| Use touch controls | The same per-game layout works with on-screen controls |
| Use a second screen | A second Android display can show the keyboard or touchpad during play |

The game menu offers save states, disk changes, game and app settings, restart, and return to the library.

## Firmware and fonts

Kairo98 does not include BIOS ROMs or font ROMs. If a game needs them, import your own BIOS, font bitmap, or YM2608 rhythm ROM from **Machine** settings.

A `FONT.BMP` placed in a game's ZIP or library folder overrides the imported font for that game; the one nearest the disk image wins.

## Launching from other frontends

Kairo98 can open a game sent from another Android frontend; no library folder is needed. Step-by-step setup:

- [LaunchBox for Android](docs/frontends.md#launchbox-for-android)
- [ES-DE](docs/frontends.md#es-de)
- [iiSU](docs/frontends.md#iisu)
- [Beacon](docs/frontends.md#beacon)

## Documentation

- [External frontend setup](docs/frontends.md): LaunchBox, ES-DE, iiSU, and Beacon configuration
- [Game catalog](docs/game-catalog.md): how games are matched, catalog updates, metadata overrides
- [Startup choices](docs/startup-choices.md): automatic menu answers and disk swaps before play
- [Release notes](docs/releases/README.md): changes in every version
- [Licensing and credits](docs/licensing.md): code license and third-party components
- [Source provenance](docs/source-import.md): imported emulator, sound, and font sources and local changes
- [Artwork rights](docs/art-rights.md): how bundled artwork is sourced and reviewed
- [Catalog source data](catalog/research/README.md): research records behind the catalog

## Help and community

- **Report a game problem** in [Issues](https://github.com/MrJackSpade/Kairo98/issues). Include the title, disk format, and what happened.
- **Chat** with other players on [Discord](https://discord.gg/ZkYQ3bPTTq).
- **English patches** for PC-98 games live in the separate [Kairo98 translation project](https://github.com/MrJackSpade/Kairo98-Patches).

### Related projects

- [KairoDos](https://github.com/MrJackSpade/KairoDos): companion DOS emulator for Android ([Google Play](https://play.google.com/store/apps/details?id=com.loxifi.kairodos))
- [Kairo](https://github.com/MrJackSpade/Kairo): shared frontend used by both apps for the library and controls
- [Kairo98-Patches](https://github.com/MrJackSpade/Kairo98-Patches): English translation patches for PC-98 games

## Building from source

Clone with submodules to get the pinned shared frontend:

```sh
git clone --recurse-submodules https://github.com/MrJackSpade/Kairo98.git
```

## License

First-party code is [GPL-2.0-or-later](LICENSE.md). Third-party components keep their own licenses; see [licensing and credits](docs/licensing.md).
