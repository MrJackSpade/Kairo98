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

Pinned 21/W and ymfm source snapshots have been imported. A stage 1 arm64 Android debug APK now builds, and the 21/W core initializes and resets on a Retroid Pocket Classic. It has not booted a disk; video output, audio output, media access, controller mapping, and ymfm integration are still in progress.

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
