# Kairo98 migration plan

Status: Stage 1 passed and the Stage 2 Android first boot reached a usable DOS prompt on 22 September 2026; [device evidence](compatibility.md). The 21/W reference comparison and full game run remain open. This is the execution order and the definition of done for each stage, not a release claim.

## Product target

- A native Android PC-98 emulator named Kairo98, based on the pinned Neko Project 21/W rev104 BSD-only source snapshot and pinned ymfm snapshot in `third_party/`.
- Launch from Kairo98 itself or directly from LaunchBox and similar frontends. Select, swap, and write eligible disk images without requiring a broad storage permission.
- Map physical gamepad buttons, hats, and axes to arbitrary PC-98 keys, key combinations, mouse controls, and app actions. Provide an optional touch flyout keyboard with PC-98-specific keys.
- Publish the same emulator features and behavior on GitHub for free and Google Play for a price, from the same commit. No bundled commercial firmware, operating system, or game data.

## Technical approach

Use one Gradle Android app with a Kotlin activity and overlay UI, a CMake-built arm64 native library, and a small JNI boundary. Keep emulation, disk devices, and sound timing in native code. Keep Android intents, document access, settings, controller discovery, and the flyout UI in the Android layer. The existing `sdl2/` and `x11/` hosts are useful guides for the native host functions, but their desktop UI and file assumptions are not the Android product architecture. Build a manifest of the exact compiled source files; do not glob the whole imported tree.

Start with a basic `SurfaceView`/`ANativeWindow` frame path and Android audio output. Measure before adding a graphics renderer or a different audio API. Run the emulator on one worker thread; queue disk, input, pause, and reset commands to it so the Android UI never mutates machine state concurrently. Use the 21/W key and disk APIs behind a small Kairo98 host interface. Keep Kairo98 changes in project-owned adapter code where possible and make deliberate, documented patches to imported files when needed.

The first build targets `arm64-v8a`. Decide the minimum Android version after a device/support check; set the Play target to the then-current requirement (Android 16 / API 36 as of this plan). Pin Gradle, Android Gradle Plugin, NDK, CMake, and any Android dependencies once the first project build works. Check 16 KB native page compatibility in the release build.

## Stages and exit gates

| Stage | Work | Exit gate |
| --- | --- | --- |
| 0. Audit and baseline | Inventory sources, compile flags, platform functions, imported licenses, disk formats, firmware expectations, and a legal test image. Establish a Windows 21/W behavior reference and representative test cases. | Written build manifest and baseline observations; no `fmgen`, GPL MAME, or excluded DOSBox FPU sources in the candidate build. |
| 1. Native build | Create the Android project and CMake source list. Bring up the interpreter CPU path and the smallest useful PC-98 configuration on arm64; fix endianness, size, alignment, and platform assumptions. Stub optional hardware only where the core permits it. | Reproducible debug APK loads the native library on a real arm64 device and initializes/resets the machine without a crash. |
| 2. First boot | Implement native timing, video frames, initial audio output, keyboard events, and seekable disk I/O using a controlled local test image. Wire `pccore_exec`, frame transfer, and clean shutdown. | A known-compatible, legally usable image boots and accepts input on a real device; capture log, screenshot, and build revision. |
| 3. ymfm sound | Add a C++ adapter for the required YM2203/YM2608 family and any other sound chips actually enabled in the product. Map register writes, status/timers, ADPCM and rhythm data, sample clock/rate conversion, volume, reset, and state restore. Remove the old OPNA generator from active output once parity is demonstrated. | FM/SSG/ADPCM/rhythm test cases produce audio without `fmgen`; timing and game behavior are compared with the baseline. Save/load does not corrupt sound state. |
| 4. Android storage and frontend launch | Implement Android document selection, persistable grants where available, seekable file-descriptor or controlled-copy access, writable/readonly decisions, disk swapping, and errors for unavailable media. Export one `ACTION_VIEW` activity and test `content://` and supported `file://`/path launch forms from LaunchBox. | A LaunchBox entry cold-launches a disk directly; multi-disk swap and restart work; failed URI grants produce a useful recovery flow. |
| 5. Controls and UX | Add physical keyboard handling, gamepad discovery, profiles with per-game overrides, configurable axis thresholds, arbitrary PC-98 key/chord targets, mouse targets, and app actions. Add the optional flyout keyboard and usable pause/disk/settings UI. | Each input source generates balanced press/release events; focus loss and disconnect clear held keys; a keyboard-heavy title is playable with controller plus flyout. |
| 6. Lifecycle and persistence | Handle pause/resume, audio focus, screen resize/rotation, incoming intent while already running, clean disk flush/eject, settings migration, and save states if the core state format is reliable. | Repeated background/foreground and relaunch cycles preserve media and settings without stuck keys or disk corruption. If save states are unsafe, omit them from the initial release and document that choice. |
| 7. Compatibility and performance | Exercise a documented matrix across floppy, HDD, PC-9801/9821 modes actually supported, graphics modes, common sound boards, disk writes, and multi-disk games. Profile frame pacing, audio underruns, battery/thermal behavior, and memory. | Explicit pass/fail matrix on at least two arm64 devices or Android versions; no unresolved release-blocking data loss, crash, or major audio/video regression. |
| 8. Release | Audit every compiled source and packaged asset, create required notices, inspect APK/AAB contents, verify 16 KB page support, pin version and commit, and make signed GitHub APK and Play AAB from one release revision. | Feature parity checklist passes; both channel packages install and boot the same test image; licensing review is recorded. |

Stages 1 and 2 are the feasibility checkpoint. If the interpreter core cannot run on arm64 without extensive CPU rewrites, stop and revise the scope before building UI polish. Stage 3 is a release dependency, even if the first boot uses 21/W's internal BSD sound generator as a temporary diagnostic baseline.

## Porting details to resolve early

### CPU and machine profile

The 21/W tree contains multiple CPU implementations and platform-specific build choices. Begin with the portable interpreter sources and a minimal PC-98 configuration. Determine which PC-9821 features need the IA-32 core and whether that path compiles and performs correctly on ARM64. Do not promise all 21/W machine modes until tested. Avoid shipping the omitted DOSBox FPU files or substituting a new restricted implementation without another license review.

### Disk and Android document access

The SDL2 host's `dosio.c` opens paths with `fopen`, while disk code expects seekable reads and often writes. Android `content://` URIs are not paths. Test whether a provider's file descriptor supports random access and writes; otherwise import a working copy into app storage and provide an explicit export/replace flow. Track image identity independently of a temporary path so per-game profiles and swap lists survive app restarts. Treat read-only providers as read-only disks and flush before closing. Imported firmware follows the same access rules.

### Sound replacement

In the imported tree, `sound/opna.c` sends OPNA FM through `opngen`, PSG through `psggen`, rhythm through `rhythm`, and ADPCM through `adpcm`. `SUPPORT_FMGEN` adds optional code but is not needed for first boot. The existing `sound/mamebsd/` ymfm integration is for OPL3/YMF262, not the YM2608 OPNA replacement. The pinned standalone ymfm source provides YM2203/YM2608. Plan for an adapter and tests, not a preprocessor switch. Preserve emulator IRQ/timer behavior and all sound sources when replacing output. Decide whether to retain the BSD OPL3 adapter or use the pinned standalone copy after the build-level license and duplication audit.

### Input and external launch

Define one virtual action model: PC-98 scan key down/up, chord, pointer movement/buttons, and emulator command. Controller, physical keyboard, touch keyboard, and UI invoke that model. Maintain held-key ownership by source so two inputs mapped to one key cannot release each other. Controller profiles need dead zones, axis direction, repeat policy, and a visible way to bind unusual keys such as STOP, COPY, GRPH, XFER, NFER, and KANA. LaunchBox's custom emulator form can pass an explicit activity and `{file.uri}` or `{file.path}`; validate the actual intent and permission delivered on devices before publishing a launch string.

### Distribution identity

Choose the Android application ID before the first public APK. Prefer one application ID and the same app-signing certificate for GitHub and Play so a user can update between channels while retaining app data, subject to Android/Play signing rules. Keep the Play upload key distinct from the app-signing key where applicable. If one shared signing identity is infeasible, document the data migration path before release. Channel packaging/signing may differ; code, features, and behavior must match.

## Evidence to keep in the repo

- `docs/build-manifest.md`: exact native inputs, flags, exclusions, and build tool versions.
- `docs/compatibility.md`: test media characteristics, machine profile, device/API, result, and known failures. Never commit copyrighted disk images or firmware.
- `docs/launch-intent.md`: final activity/package contract, LaunchBox configuration string, accepted URI forms, and tested permission behavior.
- `docs/licensing.md`: final compiled-source and packaged-artifact audit with notices.
- `docs/release-parity.md`: revision, version, signing/package policy, and parity check for each release.

## References

- [21/W source and license table](https://simk98.github.io/np21w/download.html)
- [ymfm chip support and integration notes](https://github.com/aaronsgiles/ymfm)
- [Android CMake/native build](https://developer.android.com/ndk/guides/cmake)
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [LaunchBox custom emulator intents](https://feedback.launchbox-app.com/en/help/articles/7096169-custom-emulator-with-code)
- [Google Play target API requirement](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Android 16 KB page guidance](https://developer.android.com/guide/practices/page-sizes)
- [Android App Bundle signing FAQ](https://developer.android.com/guide/app-bundle/faq)