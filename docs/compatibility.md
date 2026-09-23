# Compatibility evidence

## Clean-font Android and visible 21/W checkpoint, 23 September 2026

Implementation revision `f295a02` built with `:app:assembleDebug --offline` produced debug APK SHA-256 `857fd9ef83b88d90a9e5233dd14a2b707fe7c1276474ba23b6f3c91cbdd6d111`. On the Retroid Pocket Classic (Android 14 / API 34, arm64-v8a), I removed both temporary font caches, installed the APK, started the private user-supplied HDI, and reached `A:\NS>`. The app generated `android-font.bin` (253,686 bytes) from Android's installed fonts and a separate 21/W fallback `font.tmp` (524,350 bytes). No font cache copied from Windows was used in this final run. ASCII uses the pinned BSD-licensed Spleen 8x16 bitmap, and Japanese/halfwidth kana are rendered from device fonts. [Final device screenshot](evidence/stage1-retroid-final-boot.png).

Physical Android key events entered `DIR` and Enter and produced a directory listing at the prompt. AAudio reported `audio on 5/1358` on the boot screenshot and `5/7938` after `DIR`, including nonzero PCM buffers. The final app-process log had no `AndroidRuntime` or native fatal signal; the only entry was an OpenGL swap-behavior warning, which did not interrupt rendering. The private HDI SHA-256 was still `455d6639f0d3e9e5e72bfe7e89bd82026bdb64ae314a4d077a2cd1e3ed7ff766` after this run. [Final device log](evidence/stage1-retroid-final-log.txt).

The official visible 21/W rev104 `np21x64w.exe` (SHA-256 `9fcb5b08525c7778d2c61d616dccabdabad5e36ff845b908d9c075b003f0c49e`) booted a copy of the same HDI to `A:\NS>` on Windows. It displayed the BIOS, DOS, `VEM4.86`, and Japanese boot text. Its `NS.BAT` command accepted input but returned to DOS after reporting the system GDC was at 5 MHz; the image requires 2.5 MHz. [Visible reference screenshot](evidence/stage1-reference-21w-boot.png). The reference was started with `clk_base=2457600`; the official x64 build used its default CPU profile. Android's current VX/286 profile uses a 2.5 MHz base/GDC setting, so its boot shows 386/HMA and insufficient-memory messages instead of the reference's GDC warning. This is a configuration and CPU-core difference, not a video transcription error.

Both runs show the 640×400 PC-98 text layout, including the same DOS prompt and Japanese/ASCII character groups. The Android font shapes differ from 21/W's Windows-generated font, but characters are legible without a Windows font cache. Android input and AAudio initialization work; neither boot reached a sound-producing game scene, so FM fidelity and audible reference matching remain untested. The current 286 core does not meet this game's 386 requirement; [IA-32 support](https://github.com/MrJackSpade/Kairo98/issues/15) and [ymfm audio](https://github.com/MrJackSpade/Kairo98/issues/7) are separate migration work. This checkpoint proves a usable disk prompt and basic rendering/input/audio-path operation, not game compatibility or release readiness.

## Android first boot, 22 September 2026

The Stage 2 debug APK booted a user-supplied *Night Slave* English translation HDI to the PC-98 MS-DOS 6.20 `A:\NS>` prompt on a Retroid Pocket Classic (Android 14 / API 34, arm64-v8a). The image is a 310-cylinder, 8-head, 33-sector, 256-byte/sector SASI HDI. Its 4,096-byte header and 20,951,040 data bytes make a 20,955,136-byte file. The device used the VX/286 profile with a 2.5 MHz base clock and the default 2.5 MHz GDC setting. No external ROM was supplied. The ZIP, HDI, and README remain under ignored `roms/`; the installed app uses a private copy.

| Check | Observation |
| --- | --- |
| Boot and video | BIOS/MS-DOS text and color appear in the 640x400 `SurfaceView`; the `A:\NS>` prompt is usable in roughly 8 to 10 seconds. [Device screenshot](evidence/stage2-retroid-dos-prompt.png). |
| Input | Android physical key events typed `DIR` and Enter at the prompt and produced a directory listing. The corrected `N` mapping typed `NS`; `NS.BAT` entered the game directory and invoked `NSS`, then returned to DOS. Gameplay has not been observed. |
| Audio | AAudio opened at 44.1 kHz stereo. The native status counted nonzero PCM buffers during boot (for example, `5/846`). Audibility and FM accuracy have not been measured. |
| Worker lifecycle | Repeated Start/Stop, Pause/Resume, Reset, and queued 2/2.5 MHz configuration changes completed on the device. The frame counter held steady while paused and advanced after resume. Returning to 2.5 MHz rebooted to the DOS prompt. |
| Disk | The app imported the HDI through the Android document picker, mounted and read it through the native SASI code, and closed it. With the private image set read-only (`chmod 444`), boot and Stop succeeded after the read-only flush fix. The private image's SHA-256 after testing remained `455d6639f0d3e9e5e72bfe7e89bd82026bdb64ae314a4d077a2cd1e3ed7ff766`, matching the original. Writable disk persistence has not been exercised. |

The debug APK was built with `:app:assembleDebug --offline` from implementation revision `71f9686`, as documented in [the build manifest](build-manifest.md). [Device log](evidence/stage2-retroid-log.txt). No Android runtime or native crash appeared during these checks. This is an initial boot result, not a game compatibility or release claim. The game's README calls for the 2.5 MHz GDC setting and warns against save states; Kairo98 does not expose save states.

## Earlier Stage 1 checks

The Stage 1 debug APK repeatedly initialized/reset the core on the same device, returning `CS:IP=f000:fff0`. The HDI read diagnostic mounted the user image and read sector 0 (`EB 0A`). [Reset screenshot](evidence/stage1-retroid-reset.png) · [HDI read screenshot](evidence/hdi-read-retroid.png).
