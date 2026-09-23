# Compatibility evidence

## Android first boot, 22 September 2026

The Stage 2 debug APK booted a user-supplied *Night Slave* English translation HDI to the PC-98 MS-DOS 6.20 `A:\NS>` prompt on a Retroid Pocket Classic (Android 14 / API 34, arm64-v8a). The image is a 310-cylinder, 8-head, 33-sector, 256-byte/sector SASI HDI. Its 4,096-byte header and 20,951,040 data bytes make a 20,955,136-byte file. The device used the VX/286 profile with a 2.5 MHz base clock and the default 2.5 MHz GDC setting. No external ROM was supplied. The ZIP, HDI, and README remain under ignored `roms/`; the installed app uses a private copy.

| Check | Observation |
| --- | --- |
| Boot and video | BIOS/MS-DOS text and color appear in the 640x400 `SurfaceView`; the `A:\NS>` prompt is usable in roughly 8 to 10 seconds. [Device screenshot](evidence/stage2-retroid-dos-prompt.png). |
| Input | Android physical key events typed `DIR` and Enter at the prompt and produced a directory listing. The corrected `N` mapping typed `NS`; `NS.BAT` entered the game directory and invoked `NSS`, then returned to DOS. Gameplay has not been observed. |
| Audio | AAudio opened at 44.1 kHz stereo. The native status counted nonzero PCM buffers during boot (for example, `5/846`). Audibility and FM accuracy have not been measured. |
| Worker lifecycle | Repeated Start/Stop, Pause/Resume, Reset, and queued 2/2.5 MHz configuration changes completed on the device. The frame counter held steady while paused and advanced after resume. Returning to 2.5 MHz rebooted to the DOS prompt. |
| Disk | The app imported the HDI through the Android document picker, mounted and read it through the native SASI code, and closed it. With the private image set read-only (`chmod 444`), boot and Stop succeeded after the read-only flush fix. The private image's SHA-256 after testing remained `455d6639f0d3e9e5e72bfe7e89bd82026bdb64ae314a4d077a2cd1e3ed7ff766`, matching the original. Writable disk persistence has not been exercised. |

The debug APK was built with `:app:assembleDebug --offline` from the Stage 2 source changes documented in [the build manifest](build-manifest.md). No Android runtime or native crash appeared during these checks. This is an initial boot result, not a game compatibility or release claim. The game's README calls for the 2.5 MHz GDC setting and warns against save states; Kairo98 does not expose save states.

## 21/W reference comparison

The official rev104 `np21x64w.exe` was downloaded from the [21/W download page](https://simk98.github.io/np21w/download.html) into ignored `.downloads/` and started locally with a copy of the same HDI and `clk_base=2457600`. Its Windows process stayed alive, but a hidden-window capture returned a black frame. A pinned source build also failed in upstream Windows C code (`paging.c` / `ct1745io.c`), with the build log kept in ignored `.downloads/`. Boot-sequence, video, input, and audio comparisons against a visible 21/W run remain unverified. The official reference binary and copied HDI are not tracked. [Issue #6](https://github.com/MrJackSpade/Kairo98/issues/6) remains open for that comparison and a fuller application run.

## Earlier Stage 1 checks

The Stage 1 debug APK repeatedly initialized/reset the core on the same device, returning `CS:IP=f000:fff0`. The HDI read diagnostic mounted the user image and read sector 0 (`EB 0A`). [Reset screenshot](evidence/stage1-retroid-reset.png) · [HDI read screenshot](evidence/hdi-read-retroid.png).