# Android build manifest

Current local development build: `0.2.0-icon-dev`, 25 September 2026. The [license audit](licensing.md) records the exact debug APK and release AAB hashes and remaining artwork rights question. The published `v0.2.0` binaries were built before ymfm and before notices were packaged.

## Toolchain and variants

Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.20, JDK 17, Android SDK API 36, CMake 3.22.1, NDK 28.2.13676358. Package ID `com.mrjackspade.kairo98`; minimum API 26; `arm64-v8a` only. Build both debug APKs and both release AABs from the same revision. All contain the same emulator, JSON catalog, third-party notices, and Kairo98 launcher icon; only `withImages` contains `art/` catalog assets. There are no app-level Maven runtime dependencies. C++ is statically linked into `libkairo98.so`.

The Android CMake compile database has 234 native translation units: 221 21/W rev104 BSD-only units, three pinned ymfm units, and ten project-owned host/bridge units. The explicit 21/W core source list is in [`app/src/main/cpp/np21w-sources.cmake`](../app/src/main/cpp/np21w-sources.cmake); extra host and board sources are in [`CMakeLists.txt`](../app/src/main/cpp/CMakeLists.txt). The pinned ymfm files compiled are exactly `third_party/ymfm/src/ymfm_opn.cpp`, `ymfm_adpcm.cpp`, and `ymfm_ssg.cpp`. Project-owned `android_host/ymfm_bridge.cpp` connects these to the 21/W OPNA bus and mixer. The core definitions include `CPUCORE_IA32`, `NP2_SDL2`, `SUPPORT_KAI_IMAGES`, `SUPPORT_LARGE_HDD`, `USE_TSC`, and `SUPPORT_YMFM`; no fmgen or GPL sound definition is enabled.

## Sound path

YM2203 and YM2608 FM, SSG, and YM2608 ADPCM-B register writes feed ymfm. The adapter uses the existing 21/W ADPCM RAM, generates at the minimum-fidelity ymfm native rate, and averages samples to 44.1 kHz stereo. FM/ADPCM and SSG buses have separate volume gains. The 21/W guest-visible timer/status/IRQ model remains in control of emulation timing; ymfm's timer callbacks clock its internal sound engine. The app can import an 8 KiB `ym2608_adpcm_rom.bin` into private firmware storage for ymfm's rhythm samples. Without it, the 21/W rhythm WAV stream remains available for optional user-provided `2608_*.wav` samples. No rhythm ROM or audio sample is bundled. The existing OPL3 implementation remains for non-OPNA sound boards. Legacy FM/SSG/ADPCM generators still compile for register-side behavior and other boards, but OPNA output uses ymfm.

Save states are not exposed on Android: `statsave.c` is omitted and serialization entry points return failure. Therefore the ymfm state is reset and restored from OPNA shadow registers during normal reset/rebind, but there is no user-facing state load to support yet. Future save-state work must serialize ymfm's full state rather than only 21/W register shadows.

The test program [`tools/ymfm_smoke.cpp`](../tools/ymfm_smoke.cpp) runs directly on the Retroid and checks YM2203/YM2608 FM and SSG, YM2608 ADPCM-B, Timer A status, reset, and timer progress while muted. Night Slave boots on the Retroid with the ymfm build. The left pane reports emulator frames, nonzero audio buffers, and AAudio xrun count for device checks. Further game-level sound comparisons are recorded in [ymfm validation](ymfm-validation.md).

## Redistribution

The APK contains one native library and the `THIRD_PARTY_NOTICES.txt` asset. About → Licenses displays the notice. `tools/generate_third_party_notices.ps1` regenerates it from the pinned 21/W, ymfm, Spleen, and Android NDK LLVM notice files. `tools/audit_distribution.ps1` checks compile paths and flags, forbidden package entries, notices, native library parity, and shared asset parity. No BIOS, font ROM, operating system, or game media is bundled.
