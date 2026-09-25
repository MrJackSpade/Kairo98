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
| withImages debug APK | `d431c0239b7f676f6120869a7fdc65f4389744f37a665f23bfa4b43cf0fa36e9` | 121,529,672 bytes | 6,273 | `54ffb05a6ae13962d416418b53278e0fa7ca4a06bbc549cafa006ab85c59397c` |
| withoutImages debug APK | `1dfa9b68813b4a21bd108ecf82fd32c918b3e71c831cbf675eebd9314cca7635` | 5,024,498 bytes | 149 | same debug library |
| withImages release AAB | `4e142edc35a538af4c2607f81c05806264c55366d73d188f375db9a12305d9c4` | 119,408,419 bytes | 6,273 | `dc8101728037e82ff83af1e767e8b344054ddbfcf576411554b51a70b344abca` |
| withoutImages release AAB | `638e9c7a2d9ef89bb45b5decac0a8f651ef345a6c7d60f36dc8b1835c1487434` | 2,652,235 bytes | 149 | same release library |

Each variant pair has 149 identical non-artwork assets, including notice SHA-256 `a7c61565dfc8535c6f78a080a61776fee0c479f1821253e59b1ae1b61acbe327`. No BIOS, font ROM, operating system, game image, fmgen, GPL MAME, or DOSBox FPU file was found in any inspected package. User-supplied BIOS and test games remain outside Git and the packages. These release AABs are local unsigned audit artifacts, not published Play binaries.

## Open distribution questions

The withImages variant contains low resolution LaunchBox catalog art and source URL provenance in `app/src/withImages/assets/art/catalog-provenance-v1.json`. The user reviewed and authorized bundling these images. That review is not a license grant from each artwork rights holder. The ownership and redistribution basis for those assets remains unresolved for GitHub and paid Play distribution. The withoutImages variant avoids that asset question. The project's own source license has not been chosen; this does not remove the third-party binary notice requirements.

The development APK booted Night Slave on a Retroid Pocket Classic, but the published `v0.2.0` binaries predate this audit and notice packaging. The AABs were inspected but not installed through a Play-generated APK set. Do not describe a binary as release-ready until its exact artifact has been inspected, a game boots on Android, and its remaining asset rights question is resolved.
