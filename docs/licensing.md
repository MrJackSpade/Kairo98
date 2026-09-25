# License audit: Android ymfm development build

Audited 25 September 2026 against two locally built `0.2.0-ymfm-dev` debug APKs and two release AABs. This audit applies to these exact inputs and packages, not the already published `v0.2.0` binaries. Repeat it after changes to sources or packaging. The [21/W maintainer's license table](https://simk98.github.io/np21w/download.html) identifies the rev104 BSD-only package and separately warns about fmgen and GPL components. Archive hashes and import details are in [source-import.md](source-import.md).

## Native source and flags

The Android CMake compile database lists 234 translation units: 221 from the pinned 21/W rev104 BSD-only snapshot, three from pinned ymfm commit `81aec25ccbb98f4873a255f7551ac4dadac59b4a`, and ten project-owned Android adapter files. The core is built for arm64, Android API 26, portable IA-32, with `SUPPORT_YMFM` and without `SUPPORT_FMGEN`, `USE_MAME`, GPL MAME, or DOSBox FPU definitions. The compiled 21/W sources include `fpdummy.c` and SIMD stubs, so their notices apply. `lio/gcircle.c` invokes its MIT notice. No `sound/fmgen`, GPL `sound/mame`, DOSBox FPU, or `np2tool` translation unit is compiled. `tools/audit_distribution.ps1` checks the selected source paths and definitions against `compile_commands.json`.

The generated Spleen 8x16 ASCII glyph table derives from pinned commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. The binary notices include the relevant 21/W base, IA-32, FPU, SIMD, and LIO texts, plus the full [ymfm BSD-3-Clause license](https://github.com/aaronsgiles/ymfm/blob/main/LICENSE) and [Spleen BSD-2-Clause license](https://github.com/fcambus/spleen/blob/master/LICENSE). `tools/generate_third_party_notices.ps1` assembles `app/src/main/assets/THIRD_PARTY_NOTICES.txt` from those pinned files. Both APKs and AABs include the same notice asset, accessible in About → Licenses. Neither Neko Project 21/W nor ymfm is used as the app's brand.

The sole packaged native library is `lib/arm64-v8a/libkairo98.so`; `llvm-readelf` reports only Android platform dependencies (`liblog`, `libandroid`, `libaaudio`, `libm`, `libdl`, `libc`). C++ is linked statically. The Gradle app module declares no external runtime libraries. Android Gradle Plugin, Kotlin, Gradle, JDK, SDK, and NDK are build tools rather than bundled app code.

## Package inspection

`tools/audit_distribution.ps1` checks both APKs and both AABs, rejects forbidden native paths and firmware/game image filenames, verifies notices, compares native library hashes by build type, and compares every shared asset byte for byte. Results for the local build:

| Variant | Package SHA-256 | Size | Assets | Native library SHA-256 |
| --- | --- | ---: | ---: | --- |
| withImages debug APK | `3cc28b85ae62e01ce774d71184eb7dca6d34d08fae6ce1f553acad14340abe0c` | 121,529,944 bytes | 6,273 | `d5c46af8f70001003e51e5e2ddea2a2345f54b6a2470bf2d17a0a663780aabde` |
| withoutImages debug APK | `b283da94d8cd0a0c15930aa2ee8667a6e6f9ccbe1f79fc2aa71630b0a7c410ab` | 5,024,770 bytes | 149 | same debug library |
| withImages release AAB | `9e4349dc8581842a69b8c1fc8b8b6e6f137ef36880a4f080811991d309f1ad89` | 119,409,034 bytes | 6,273 | `22d5306b1c68f0373b3ad046d0312fc7c57564a6954ff10159f1572739ed0df5` |
| withoutImages release AAB | `93199c1f130e84b020b11ed594a8aad4821acf71b8aef2bc3fde7aace4a921e5` | 2,652,850 bytes | 149 | same release library |

Each variant pair has 149 identical non-artwork assets, including notice SHA-256 `a7c61565dfc8535c6f78a080a61776fee0c479f1821253e59b1ae1b61acbe327`. No BIOS, font ROM, operating system, game image, fmgen, GPL MAME, or DOSBox FPU file was found in any inspected package. User-supplied BIOS and test games remain outside Git and the packages. These release AABs are local unsigned audit artifacts, not published Play binaries.

## Open distribution questions

The withImages variant contains low resolution LaunchBox catalog art and source URL provenance in `app/src/withImages/assets/art/catalog-provenance-v1.json`. The user reviewed and authorized bundling these images. That review is not a license grant from each artwork rights holder. The ownership and redistribution basis for those assets remains unresolved for GitHub and paid Play distribution. The withoutImages variant avoids that asset question. The project's own source license has not been chosen; this does not remove the third-party binary notice requirements.

The development APK booted Night Slave on a Retroid Pocket Classic, but the published `v0.2.0` binaries predate this audit and notice packaging. The AABs were inspected but not installed through a Play-generated APK set. Do not describe a binary as release-ready until its exact artifact has been inspected, a game boots on Android, and its remaining asset rights question is resolved.
