# Licensing plan

This is a planning record and preliminary source check, not a completed binary license audit. The user requested the public `v0.2.0` GitHub Release before completing that audit. Its notes identify the debug build configuration and the open audit. Do not describe the binary as release-ready until the final build inputs have been checked and a game boot has been verified on the exact release build.

## Native components

The [21/W maintainer's license table](https://simk98.github.io/np21w/download.html) says most code is under a modified BSD license and provides a BSD-only source package based on rev104. It also identifies files with different terms. In particular, `sound/fmgen` is described as generally unavailable for commercial use. The imported BSD-only snapshot has no `sound/fmgen/` directory. Android build definitions must keep `SUPPORT_FMGEN` disabled. The optional GPLv2 `sound/mame/` and DOSBox FPU source files were also absent in the imported snapshot. Preserve `third_party/np21w/LICENSES/` and check each compiled component.

The Android build uses the snapshot's portable IA-32 CPU sources and its disabled FPU instruction stubs. The DOSBox FPU implementations remain excluded. The expanded compiled-source list requires review before distribution.

[ymfm](https://github.com/aaronsgiles/ymfm) identifies its sound cores as BSD-3-Clause. Its `LICENSE` notice is present in `third_party/ymfm/`, pinned to the commit recorded in [source import](source-import.md).

The compiled ASCII bitmap glyphs come from [Spleen 8x16](https://github.com/fcambus/spleen), BSD-2-Clause. Its source BDF, generator, and copyright/license notice are in `third_party/spleen/`. Both GitHub and Play binary packages must reproduce the BSD-2-Clause notice in their documentation or other distributed materials.

The project's own source license has not been selected. The public GitHub distribution and paid Play distribution must include the same license notices for shared code and dependencies.

## Firmware and game data

Do not bundle proprietary PC-98 BIOS, font ROMs, operating systems, or commercial game images. Users provide any files they are entitled to use. The optional `FONT.BMP` import is validated and copied into private app storage; it is absent from Git and both APK variants. Without it, the app generates its font from Android device fonts and the pinned Spleen glyphs. The app should document which files are optional or required for each emulated configuration.

The reviewed 360-pixel catalog artwork is bundled in the `withImages` build at the user's direction. Its source URLs and packaged hashes are recorded in `app/src/withImages/assets/art/catalog-provenance-v1.json`; creator and redistribution rights for both distribution channels still require the final asset audit. The user-supplied `bios.rom` used in device tests remains in private app storage and is absent from Git and APK/AAB assets.

## Release gate

Before a stable release, review the complete source list, generated assets, bundled native libraries, build flags, notices, and APK/AAB contents. Record the results here. The source package's license table is version-specific, so this review repeats if the native source changes. The beta APKs passed catalog parity checks and were scanned for bundled BIOS or game images; that does not complete the license audit.
