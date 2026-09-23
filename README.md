# Kairo98

Kairo98 is a standalone Android PC-98 emulator in development.

## Project goals

- Run PC-98 games as a native Android app, including launches from frontends such as LaunchBox.
- Port a fixed snapshot of Neko Project 21/W's emulation code to Android.
- Use [ymfm](https://github.com/aaronsgiles/ymfm) for supported Yamaha sound chips. Do not ship fmgen.
- Map gamepad controls to arbitrary PC-98 keys, key combinations, joystick 1 directions and buttons, and app actions, with global and per-game profiles.
- Provide an optional flyout PC-98 keyboard.
- Publish a free GitHub build and a paid Google Play build with the same features and behavior.

## Current status

Pinned 21/W and ymfm source snapshots have been imported. An arm64 Android debug APK boots a user-supplied HDI or an HDI inside a ZIP on a Retroid Pocket Classic, with video, physical and touch keyboard input, initial audio output, controller key mapping, and clean disk shutdown. Full game compatibility, mouse behavior across games, frontend launch, and ymfm integration remain in progress.

## Current Android controls

Kairo98 opens a ROM folder picker on first launch, then scans that folder for HDIs and ZIPs containing HDIs. A short tap or controller A launches a library entry. Long press a game to inspect its content ID and edit or reset per-game title, clock, guest command, and art references. A refreshed library reuses hashes when the provider reports an unchanged size and modification time; Rehash verifies content again. A ZIP and a standalone file containing identical HDI bytes share one game ID, while each source keeps its own writable working copy.

The left flyout contains Continue, Restart, Game library, Choose HDI, Pause/Resume, and Exit. Graphics, Machine, Audio, and About open focused dialogs. Graphics defaults to contained integer scaling so every source pixel remains visible. An optional cropped integer mode uses the next whole-pixel multiple and hides any excess at the display edges; Fit display uses a fractional scale while preserving the 640×400 aspect ratio. The unused display area is black.

Open the session flyout with a controller Mode/Home event if Android sends it to the app, Android Back or Menu, or a touchscreen swipe from the left edge. [Android reserves the system Home key](https://developer.android.com/reference/android/view/KeyEvent#KEYCODE_HOME), so that key cannot directly open an app menu. No persistent menu control covers the game. Settings for scaling, base clock, mute, and global input mode persist across launches.

Input Mode defaults to Auto with a Keyboard fallback. On a keyboard screen, tap the emulated display to open the optional PC-98 keyboard. When Auto observes sustained guest mouse reads without keyboard activity, touch acts as a mouse touchpad: drag to move the guest cursor, tap to click it, or hold then drag for a button drag. Swipe inward from the right edge to open the keyboard in any mode. The session Input mode menu and game details can set Keyboard or Mouse explicitly for a game; user overrides can be reset to catalog defaults. Auto is a heuristic and mixed-input titles may need a per-game choice.

## Documentation

- [Migration plan](docs/migration-plan.md)
- [Native build manifest](docs/build-manifest.md)
- [Compatibility evidence](docs/compatibility.md)
- [Architecture](docs/architecture.md)
- [Source import](docs/source-import.md)
- [Licensing](docs/licensing.md)
- [Release parity](docs/release-parity.md)
- [Game catalog and content IDs](docs/game-catalog.md)
- [Artwork intake and rights](docs/art-rights.md)
- [Roadmap](docs/roadmap.md)

This is an independent project. It is not an official Neko Project 21/W release.
