# Licensing plan

This is a planning record and preliminary source check, not a completed binary license audit. Do not distribute a binary until the final build inputs have been checked.

## Native components

The [21/W maintainer's license table](https://simk98.github.io/np21w/download.html) says most code is under a modified BSD license and provides a BSD-only source package based on rev104. It also identifies files with different terms. In particular, `sound/fmgen` is described as generally unavailable for commercial use. The imported BSD-only snapshot has no `sound/fmgen/` directory. Android build definitions must keep `SUPPORT_FMGEN` disabled. The optional GPLv2 `sound/mame/` and DOSBox FPU source files were also absent in the imported snapshot. Preserve `third_party/np21w/LICENSES/` and check each compiled component.

[ymfm](https://github.com/aaronsgiles/ymfm) identifies its sound cores as BSD-3-Clause. Its `LICENSE` notice is present in `third_party/ymfm/`, pinned to the commit recorded in [source import](source-import.md).

The project's own source license has not been selected. The public GitHub distribution and paid Play distribution must include the same license notices for shared code and dependencies.

## Firmware and game data

Do not bundle proprietary PC-98 BIOS, font ROMs, operating systems, or commercial game images. Users provide any files they are entitled to use. The app should document which files are optional or required for each emulated configuration.

## Release gate

Before the first public binary, review the complete source list, generated assets, bundled native libraries, build flags, notices, and APK/AAB contents. Record the results here. The source package's license table is version-specific, so this review repeats if the native source changes.
