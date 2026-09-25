# License audit: Android development build

Audited 25 September 2026 against two locally built `0.2.0-icon-dev` debug APKs and two release AABs. This audit applies to these exact inputs and packages, not the already published `v0.2.0` binaries. Repeat it after changes to sources or packaging. The [21/W maintainer's license table](https://simk98.github.io/np21w/download.html) identifies the rev104 BSD-only package and separately warns about fmgen and GPL components. Archive hashes and import details are in [source-import.md](source-import.md).

## Native source and flags

The Android CMake compile database lists 234 translation units: 221 from the pinned 21/W rev104 BSD-only snapshot, three from pinned ymfm commit `81aec25ccbb98f4873a255f7551ac4dadac59b4a`, and ten project-owned Android adapter files. The core is built for arm64, Android API 26, portable IA-32, with `SUPPORT_YMFM` and without `SUPPORT_FMGEN`, `USE_MAME`, GPL MAME, or DOSBox FPU definitions. The compiled 21/W sources include `fpdummy.c` and SIMD stubs, so their notices apply. `lio/gcircle.c` invokes its MIT notice. No `sound/fmgen`, GPL `sound/mame`, DOSBox FPU, or `np2tool` translation unit is compiled. `tools/audit_distribution.ps1` checks the selected source paths and definitions against `compile_commands.json`.

The generated Spleen 8x16 ASCII glyph table derives from pinned commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. The binary notices include the relevant 21/W base, IA-32, FPU, SIMD, and LIO texts, plus the full [ymfm BSD-3-Clause license](https://github.com/aaronsgiles/ymfm/blob/main/LICENSE) and [Spleen BSD-2-Clause license](https://github.com/fcambus/spleen/blob/master/LICENSE). The C++ runtime is statically linked from Android NDK 28.2.13676358, so the pinned NDK LLVM toolchain `NOTICE` is included as well; it covers LLVM exception and legacy libc++/libc++abi terms. `tools/generate_third_party_notices.ps1` assembles `app/src/main/assets/THIRD_PARTY_NOTICES.txt` from those pinned files. Both APKs and AABs include the same notice asset, accessible in About → Licenses. Neither Neko Project 21/W nor ymfm is used as the app's brand.

The sole packaged native library is `lib/arm64-v8a/libkairo98.so`; `llvm-readelf` reports only Android platform dependencies (`liblog`, `libandroid`, `libaaudio`, `libm`, `libdl`, `libc`). C++ is linked statically. The Gradle app module declares no external runtime libraries. Android Gradle Plugin, Kotlin, Gradle, JDK, SDK, and NDK are build tools rather than bundled app code.

## Package inspection

`tools/audit_distribution.ps1` checks both APKs and both AABs, rejects forbidden native paths and firmware/game image filenames, verifies notices and the launcher artwork, compares native library hashes by build type, and compares every shared asset byte for byte. Results for the local build:

| Variant | Package SHA-256 | Size | Assets | Native library SHA-256 |
| --- | --- | ---: | ---: | --- |
| withImages debug APK | `96bf5212ea7103a62c714845b4e7af790c755374684c0de1497f28950f478063` | 121,446,830 bytes | 6,273 | `d5c46af8f70001003e51e5e2ddea2a2345f54b6a2470bf2d17a0a663780aabde` |
| withoutImages debug APK | `cb89ee68386fd346975573ae0ae793984a3f67d52dd09d9c2407f2f88c38b590` | 4,939,648 bytes | 149 | same debug library |
| withImages release AAB | `b8c3f9dbbadb8f235aaaeab5f492e72ce5e61e01ca0bfa7020c4475697783850` | 120,842,144 bytes | 6,273 | `22d5306b1c68f0373b3ad046d0312fc7c57564a6954ff10159f1572739ed0df5` |
| withoutImages release AAB | `01be4ea5298a56bb60ccb0949d0b46b6c7243588a3b0a27f7db3f75576aed363` | 4,085,993 bytes | 149 | same release library |

Each variant pair has 149 identical non-artwork assets, including notice SHA-256 `c18e990fa124eca8f0767c3a95ccf4474eddae3135c483a4f9ce2befc6a0d3b6`. The Kairo98 launcher icon appears in both variants. APKs preserve the source PNG byte for byte (SHA-256 `855676faeb93c96a852415aa2b305c71479d2603db0fae09fbffddf1109bb947`); AAB resource processing recompresses it, but decoded RGBA pixels were compared and matched exactly. No BIOS, font ROM, operating system, game image, fmgen, GPL MAME, or DOSBox FPU file was found in any inspected package. The test BIOS and games remain outside Git and the packages. These release AABs are local unsigned audit artifacts, not published Play binaries.

## Open distribution questions

The withImages variant contains low resolution LaunchBox catalog art and source URL provenance in `app/src/withImages/assets/art/catalog-provenance-v1.json`. The images were approved for inclusion in the development bundle on 24 September 2026. This project-level approval does not grant a license from each artwork rights holder. The ownership and redistribution basis for those assets remains unresolved for GitHub and paid Play distribution. The withoutImages variant avoids that asset question. The project's own source license has not been chosen; this does not remove the third-party binary notice requirements.

The development APK booted Night Slave on a Retroid Pocket Classic, but the published `v0.2.0` binaries predate this audit and notice packaging. The AABs were inspected but not installed through a Play-generated APK set. Do not describe a binary as release-ready until its exact artifact has been inspected, a game boots on Android, and its remaining asset rights question is resolved.

## 0.3.1 non-artwork release audit

The signed `withoutImagesRelease` APK and AAB were built from the Loxifi `0.3.1` source changes on 25 September 2026. `tools/audit_play_release.py` inspected both archives: the APK contains 169 entries, the AAB 179, and both contain the same 150 non-artwork assets byte for byte, including the third-party notices and privacy policy. Neither contains artwork catalogs, BIOS, ROM, operating system, or game images. The release compile database lists 234 translation units (221 pinned 21/W, three pinned ymfm, ten project adapter files), without fmgen, MAME, DOSBox, or np2tool sources or enabling definitions.

The APK SHA-256 is `7636dfee789499f6e2c104c1fca8dd45366259e3009bf138d871fd018eb64964` (4,463,060 bytes). The AAB SHA-256 is `a175948a661c5309fd214476f9f7dff9e6e11be6f92691dbe5338d5ecc9c84e4` (4,115,908 bytes). Both have the persistent release signing certificate SHA-256 `c321ec6b35e2509741362fcc3fede816c81510aa8133d791ea55ce0e1594a51c`. The signed APK declares the sole installable package ID `com.loxifi.kairo98`, version `0.3.1` (code 301), and is not debuggable. The older `com.mrjackspade.kairo98` name remains only as the internal Android/Kotlin namespace for class and JNI compatibility.

That exact signed APK was installed on a Retroid Pocket Classic. With a user-provided external test folder, Acrojet launched to its illustrated title screen. The local screenshot evidence is saved outside the distribution under `.downloads/release-evidence/acrojet-release-boot.png`. The AAB has been audited as a package and cryptographically signed, but Google Play has not yet generated or installed APKs from it.
