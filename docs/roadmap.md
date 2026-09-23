# Roadmap

The detailed execution order, acceptance gates, and technical risks are in [migration-plan.md](migration-plan.md). This page is the short milestone summary.

## 0. Workspace

- Create the public Kairo98 repository with only `origin`.
- Commit project scope, source policy, architecture, licensing plan, and release parity.

## 1. Native proof of concept

- Complete the build-level audit of the imported 21/W BSD-only and ymfm source snapshots.
- Build the portable native core for arm64 Android and boot one known-compatible disk image on a device.
- Confirm video, input, disk I/O, and basic audio before replacing OPNA generation.
- Integrate ymfm and compare sound output and game behavior with the 21/W baseline.

## 2. Android app

- Implement file selection, persistent storage access, and direct launch intents.
- Add disk swapping, controller detection, arbitrary key mapping, and per-game profiles.
- Add the optional flyout PC-98 keyboard.

## 3. Release

- Test representative games, multi-disk flows, lifecycle behavior, and frontend launches.
- Complete source and binary license review.
- Publish matching GitHub and Google Play releases from one revision.

No milestone is marked complete merely because documentation exists.
