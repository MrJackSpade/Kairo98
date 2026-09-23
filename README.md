# Kairo98

Kairo98 is a standalone Android PC-98 emulator in development.

## Project goals

- Run PC-98 games as a native Android app, including launches from frontends such as LaunchBox.
- Port a fixed snapshot of Neko Project 21/W's emulation code to Android.
- Use [ymfm](https://github.com/aaronsgiles/ymfm) for supported Yamaha sound chips. Do not ship fmgen.
- Map gamepad controls to arbitrary PC-98 keys, key combinations, mouse actions, and app actions, with global and per-game profiles.
- Provide an optional flyout PC-98 keyboard.
- Publish a free GitHub build and a paid Google Play build with the same features and behavior.

## Current status

Pinned 21/W and ymfm source snapshots have been imported. A Stage 2 arm64 Android debug APK boots a user-supplied HDI to a usable PC-98 MS-DOS prompt on a Retroid Pocket Classic, with video, physical keyboard input, initial audio output, and clean disk shutdown. Full game compatibility, controller mapping, frontend launch, and ymfm integration remain in progress.

## Current Android controls

An imported HDI starts automatically when Kairo98 opens. The left flyout contains Continue, Restart, Choose HDI, Pause/Resume, and Exit. Graphics, Machine, Audio, and About open focused dialogs. Graphics defaults to contained integer scaling so every source pixel remains visible. An optional cropped integer mode uses the next whole-pixel multiple and hides any excess at the display edges; Fit display uses a fractional scale while preserving the 640×400 aspect ratio. The unused display area is black.

Open the flyout with a controller Mode/Home event if Android sends it to the app, Android Back or Menu, or a touchscreen swipe from the left edge. [Android reserves the system Home key](https://developer.android.com/reference/android/view/KeyEvent#KEYCODE_HOME), so that key cannot directly open an app menu. No persistent menu control covers the game. Settings for scaling, base clock, and mute persist across launches. Gamepad mapping and the optional PC-98 keyboard remain separate roadmap work.

## Documentation

- [Migration plan](docs/migration-plan.md)
- [Native build manifest](docs/build-manifest.md)
- [Compatibility evidence](docs/compatibility.md)
- [Architecture](docs/architecture.md)
- [Source import](docs/source-import.md)
- [Licensing](docs/licensing.md)
- [Release parity](docs/release-parity.md)
- [Roadmap](docs/roadmap.md)

This is an independent project. It is not an official Neko Project 21/W release.
