# Compatibility evidence

No game compatibility result exists yet. Stage 1 only proves that the selected 21/W machine code compiles for arm64 and initializes/resets on Android.

| Date | Device | Android | Build | Test | Result |
| --- | --- | --- | --- | --- | --- |
| 2026-09-22 | Retroid Pocket Classic | 14 / API 34, arm64-v8a | Stage 1 debug APK | Repeat `pccore_init()` → `pccore_reset()` → `pccore_term()` | Pass: `CS:IP=f000:fff0`; process remained alive after repeated probes. |

See the [native build manifest](build-manifest.md) and [reset screenshot](evidence/stage1-retroid-reset.png). The first disk boot belongs to [issue #6](https://github.com/MrJackSpade/Kairo98/issues/6).
