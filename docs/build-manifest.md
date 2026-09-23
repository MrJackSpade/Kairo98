# Android native build manifest

Status: Stage 2 debug build, 22 September 2026. The Android device booted a user-supplied HDI to DOS; this is not a completed binary license audit or a game compatibility claim.

## Toolchain and build

- Gradle 8.13 via the checked-in wrapper; Android Gradle Plugin 8.13.2; Kotlin plugin 2.2.20; JDK 17.
- Android platform API 36, target API 36, minimum API 26, build tools 36.0.0, CMake 3.22.1, NDK r28c (`28.2.13676358`).
- Application ID: `com.mrjackspade.kairo98`. Initial ABI: `arm64-v8a`.
- Build from the repository root with `ANDROID_HOME` pointing to an SDK containing those packages, then run `./gradlew :app:assembleDebug` (or `gradlew.bat` on Windows).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. It is an internal diagnostic build.

The exact 185 base 21/W C source paths are frozen in [`app/src/main/cpp/np21w-sources.cmake`](../app/src/main/cpp/np21w-sources.cmake). CMake adds eight further 21/W sources: SDL2 host `dosio.c`, `timemng.c`, `joymng.c`, `mousemng.c`, and `fontmng.c`, plus `cbus/boardmo.c`, `lio/gpaint.c`, and `lio/groll.c`. Four project-owned C adapters (`android_host/np2sysp.c`, `platform.c`, `core_probe.c`, `machine.c`) and one JNI C++ bridge complete the native library. The resulting APK contains `lib/arm64-v8a/libkairo98.so`.

The 21/W core compile defines `SUPPORT_LARGE_HDD` and `NP2_SDL2`, with signed `char` and strict aliasing disabled to match existing host build assumptions. Android's `compiler.h` derives from the imported iOS host header; `commng.h` derives from the Windows host; `mousemng.h` and `sysmng.h` derive from the SDL2 host. These copied headers are part of the 21/W attribution and must be checked in the release audit.

A detached clean worktree at commit `1dcdf06` also built `:app:assembleDebug` successfully using the pinned local SDK. This verifies that the build does not depend on untracked source files in the working tree.

## Deliberate first-stage limits

- The compiled source list excludes `fmgen`, GPL MAME, the omitted DOSBox FPU code, and the older `sound/mamebsd/` copy. ymfm integration is [issue #7](https://github.com/MrJackSpade/Kairo98/issues/7).
- This profile starts the portable 286 CPU and PC-98 machine state. PC-9821/IA-32 support is [issue #15](https://github.com/MrJackSpade/Kairo98/issues/15).
- `android_host/platform.c` still uses disconnected serial/printer devices. Stage 2 sends its 640x400 frame surface to `ANativeWindow` and mixes core PCM to AAudio. The document picker imports an HDI into private app storage; normal disk management and frontend intents are later stages.
- Save states are unavailable: `statsave.c` is omitted and the two serialization entry points return failure. The optional NP2 guest-service commands are also stubbed. Both require deliberate follow-up before they can be advertised.
- The first-boot profile has no floppy seek sound, SCSI, or external ROM/firmware files. It does not bundle any game, BIOS, or operating system.

## Imported source adjustments

The following changes to the pinned 21/W snapshot were required for this arm64 build. They are local Kairo98 changes; there is no upstream sync remote.

- `sound/fmboard.h`: guard the Sound Blaster type when that optional board is disabled.
- `io/printif.c`: pass an integer zero through its integer-typed message API.
- `sdl2/dosio.c`: use Android's `futimens` for file timestamps in place of `futimes`; skip `fflush`/`fsync` for read-only file descriptors so read-only HDIs close cleanly.

## Stage 1 device check

On a Retroid Pocket Classic running Android 14 (API 34, `arm64-v8a`, 4 KB pages), the APK installed and loaded its native library. The diagnostic button calls `pccore_init()`, `pccore_reset()`, reads the CPU reset vector, then calls `pccore_term()`. It returned `CS:IP=f000:fff0` and remained running after two more repeated probes. [Device screenshot](evidence/stage1-retroid-reset.png). A later diagnostic build mounted and read sector 0 from a user-supplied HDI through the 21/W SASI disk code; [details](compatibility.md). A later Stage 2 build booted the user-supplied HDI to a DOS prompt; [results](compatibility.md).

## Stage 2 native path and device check

`android_host/machine.c` owns core startup, the 2.5 MHz default, SASI HDD 0 mount, execution, key events, reset, clock configuration, and disk flush/teardown. The JNI bridge runs `pccore_exec(TRUE)` on one 60 Hz worker and queues input, pause, resume, reset, disk, clock, and stop commands there. It copies RGB565 frames to `ANativeWindow` and sends 44.1 kHz stereo PCM to AAudio. The Kotlin activity handles document import, physical keyboard events, lifecycle, and the basic test controls. The app does not ship an HDI or BIOS.

The final debug APK tested here has SHA-256 `6fe08d5b653e78a8ac181af758f24489b54f8a617e9511799a01f49da4cfcda9`. It was built from implementation revision `71f9686` (based on `0e2c4dd`), using `:app:assembleDebug --offline`. On the Retroid Pocket Classic (Android 14 / API 34), it reached the DOS prompt, accepted physical keys, emitted nonzero PCM buffers, survived repeated lifecycle commands, and closed a read-only HDI cleanly. [Compatibility record and screenshot](compatibility.md). The Windows 21/W reference comparison and full game execution remain pending.