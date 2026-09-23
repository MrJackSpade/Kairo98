# Compatibility evidence

No game compatibility result exists yet. The Android build initializes the machine and can read an HDI, but it does not execute a disk boot or display frames.

| Date | Device | Android | Build | Test | Result |
| --- | --- | --- | --- | --- | --- |
| 2026-09-22 | Retroid Pocket Classic | 14 / API 34, arm64-v8a | Stage 1 debug APK | Repeated core initialization, reset, and teardown | Pass: `CS:IP=f000:fff0`; process remained alive. |
| 2026-09-22 | Retroid Pocket Classic | 14 / API 34, arm64-v8a | HDI read diagnostic APK | Select user-supplied *Night Slave* English translation HDI through Android document picker; mount as SASI HDD 0 using 21/W's `sxsihdd` code and read sector 0 | Pass: 310 cylinders, 8 heads, 33 sectors, 256 bytes/sector; first two disk bytes `EB 0A`. Core reset succeeded again afterward. |

The supplied ZIP contains one `.hdi` and a README. Its 4,096-byte header declares 20,951,040 data bytes, exactly matching `310 x 8 x 33 x 256`; the full image is 20,955,136 bytes. The README requires a 2.5 MHz GDC setting and warns against save states. These are test requirements for a future boot attempt, not confirmed game behavior. The ZIP, extracted HDI, and README are kept in ignored `roms/` and are absent from Git.

[Reset screenshot](evidence/stage1-retroid-reset.png) · [HDI read screenshot](evidence/hdi-read-retroid.png) · [Native build manifest](build-manifest.md). The first disk boot remains [issue #6](https://github.com/MrJackSpade/Kairo98/issues/6).
