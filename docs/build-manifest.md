# Android native build manifest

Status: stage 1 debug build, 22 September 2026. This is a native build record, not a completed binary license audit or a playable emulator.

## Toolchain and build

- Gradle 8.13 via the checked-in wrapper; Android Gradle Plugin 8.13.2; Kotlin plugin 2.2.20; JDK 17.
- Android platform API 36, target API 36, minimum API 26, build tools 36.0.0, CMake 3.22.1, NDK r28c (`28.2.13676358`).
- Application ID: `com.mrjackspade.kairo98`. Initial ABI: `arm64-v8a`.
- Build from the repository root with `ANDROID_HOME` pointing to an SDK containing those packages, then run `./gradlew :app:assembleDebug` (or `gradlew.bat` on Windows).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It is an internal diagnostic build.

The exact 185 base 21/W C source paths are frozen in [`app/src/main/cpp/np21w-sources.cmake`](../app/src/main/cpp/np21w-sources.cmake). CMake adds eight further 21/W sources: SDL2 host `dosio.c`, `timemng.c`, `joymng.c`, `mousemng.c`, and `fontmng.c`, plus `cbus/boardmo.c`, `lio/gpaint.c`, and `lio/groll.c`. Three project-owned C adapters (`android_host/np2sysp.c`, `platform.c`, `core_probe.c`) and one JNI C++ bridge complete the native library. The resulting APK contains `lib/arm64-v8a/libkairo98.so`.

The 21/W core compile defines `SUPPORT_LARGE_HDD` and `NP2_SDL2`, with signed `char` and strict aliasing disabled to match existing host build assumptions. Android's `compiler.h` derives from the imported iOS host header; `commng.h` derives from the Windows host; `mousemng.h` and `sysmng.h` derive from the SDL2 host. These copied headers are part of the 21/W attribution and must be checked in the release audit.

A detached clean worktree at commit `1dcdf06` also built `:app:assembleDebug` successfully using the pinned local SDK. This verifies that the build does not depend on untracked source files in the working tree.

## Deliberate first-stage limits

- The compiled source list excludes `fmgen`, GPL MAME, the omitted DOSBox FPU code, and the older `sound/mamebsd/` copy. ymfm integration is [issue #7](https://github.com/MrJackSpade/Kairo98/issues/7).
- This profile starts the portable 286 CPU and PC-98 machine state. PC-9821/IA-32 support is [issue #15](https://github.com/MrJackSpade/Kairo98/issues/15).
- `android_host/platform.c` currently uses disconnected serial/printer devices, a memory-only 640×400 frame surface, and no audio output. It has no Android disk picker or frontend intent. Those are later stages.
- Save states are unavailable: `statsave.c` is omitted and the two serialization entry points return failure. The optional NP2 guest-service commands are also stubbed. Both require deliberate follow-up before they can be advertised.
- The first-boot profile has no floppy seek sound, SCSI, or external ROM/firmware files. It does not bundle any game, BIOS, or operating system.

## Imported source adjustments

The following changes to the pinned 21/W snapshot were required for this arm64 build. They are local Kairo98 changes; there is no upstream sync remote.

- `sound/fmboard.h`: guard the Sound Blaster type when that optional board is disabled.
- `io/printif.c`: pass an integer zero through its integer-typed message API.
- `sdl2/dosio.c`: use Android's `futimens` for file timestamps in place of `futimes`.

## Stage 1 device check

On a Retroid Pocket Classic running Android 14 (API 34, `arm64-v8a`, 4 KB pages), the APK installed and loaded its native library. The diagnostic button calls `pccore_init()`, `pccore_reset()`, reads the CPU reset vector, then calls `pccore_term()`. It returned `CS:IP=f000:fff0` and remained running after two more repeated probes. [Device screenshot](evidence/stage1-retroid-reset.png). No game boot has been attempted.
