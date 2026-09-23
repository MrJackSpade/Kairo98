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

Pinned 21/W and ymfm source snapshots have been imported. Android application code and a working native build do not exist yet. No APK exists.

## Documentation

- [Architecture](docs/architecture.md)
- [Source import](docs/source-import.md)
- [Licensing](docs/licensing.md)
- [Release parity](docs/release-parity.md)
- [Roadmap](docs/roadmap.md)

This is an independent project. It is not an official Neko Project 21/W release.
