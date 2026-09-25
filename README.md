# Kairo98

Kairo98 is a PC-98 emulator for Android, built for phones and handhelds. Browse your games, open one to see its details, and play with a controller or touch controls.

Kairo98 is in active development, and game compatibility varies.

## Screenshots

| Game library | Game details |
| --- | --- |
| ![Kairo98 game library showing translated PC-98 games](docs/screenshots/library.png) | ![Rusty game details in Kairo98](docs/screenshots/game-details.png) |
| Playing a game | On-screen keyboard |
| ![Rusty running in Kairo98 on Android](docs/screenshots/running-game.png) | ![Kairo98 on-screen PC-98 keyboard](docs/screenshots/keyboard.png) |

These screenshots show a development build on a Retroid Pocket Classic. Game images in the screenshots are not bundled with the non-artwork app.

## Get started

1. Download the non-artwork APK from [GitHub Releases](https://github.com/MrJackSpade/Kairo98/releases) and install it on an **ARM64 device running Android 8.0 or newer**.
2. On first launch, choose the folder containing your PC-98 disk images. You can skip this and choose a folder later from the library menu.
3. Tap a game to open its details, then tap **Play**. A controller can navigate the library too.

The library reads HDI hard disks and supported floppy formats, including FDI, D88, NFD, HDM, and XDF. Images can be loose files or inside ZIP archives. Kairo98 matches known games by the disk image's contents, so recompressing a ZIP does not change its catalog match. Some games need a separate boot floppy or a specific setup; support for those is still being expanded.

The current release uses the `com.loxifi.kairo98` Android package and does not bundle game artwork. Earlier releases used a different package ID, so moving from `v0.3.0` or earlier requires a new install. Use **Library menu → Download missing images** to fetch artwork for games in your library. Downloads are saved in the app and can be retried. The paid Google Play edition is built from the same source revision with the same features and behavior.

Games and firmware are not included with Kairo98.

## Playing and controls

- Map controller buttons to PC-98 keys, joystick buttons, mouse actions, or app controls. Keep a global layout or customize one game.
- Turn on on-screen controls for phones and position them separately in portrait and landscape.
- Swipe in from the **right edge** for the PC-98 keyboard. Tapping the game screen opens the keyboard or acts as a mouse touchpad, depending on the input mode. You can change that mode for each game if Auto chooses poorly.
- On a dual-screen Android handheld, Kairo98 covers the second screen and shows the PC-98 keyboard there during play. The keyboard's arrow button swaps the game and keyboard between screens. If the second screen disconnects, the right-edge keyboard remains available.
- Open the in-game menu with **Android Back**, a swipe from the **left edge**, or a controller Menu/Mode button when the device sends it to the app. From there you can pause, restart, change disks, open settings, or return to the library.
- The default display mode keeps the entire picture visible with integer scaling. Cropped integer scaling and fit-to-screen scaling are optional.

You can import your own BIOS ROM, font bitmap, and YM2608 rhythm ROM from **Machine** settings. Kairo98 stores imported files in its private app storage. It does not ship games, operating systems, or firmware.

## Help and project information

Kairo98 is still being tested across PC-98 games. If a game fails to boot, has graphics or sound problems, or needs a disk change the app cannot handle, [open an issue](https://github.com/MrJackSpade/Kairo98/issues) with the game name and what happened.

Kairo98 is an independent project built from a pinned Neko Project 21/W core with [ymfm](https://github.com/aaronsgiles/ymfm) sound emulation. It is not an official Neko Project 21/W release.

See the [roadmap](docs/roadmap.md), [game catalog notes](docs/game-catalog.md), and [licensing and credits](docs/licensing.md) for more detail.
