# ymfm validation, 25 September 2026

The Android OPNA output path now uses the pinned ymfm YM2203/YM2608 sources. This record covers the `0.2.0-ymfm-dev` local debug build on a Retroid Pocket Classic (Android 14). The source and license inventory is in [build-manifest.md](build-manifest.md) and [licensing.md](licensing.md).

## Exercised cases

`tools/ymfm_smoke.cpp` was cross-compiled with NDK 28.2.13676358 and run as an arm64 executable on the Retroid. It generated nonzero PCM for YM2203 FM and three-channel SSG, YM2608 FM and SSG, ADPCM-B from synthetic RAM, and YM2608 ADPCM-A rhythm from a synthetic 8 KiB ROM. Peak output values were 32,704, 16,382, 16,336, 10,921, 16,319, and 15,352 respectively for those six cases. Timer A set its status bit on both chips; reset cleared it; muting output did not stop timer progress. The test removes its synthetic rhythm file after use. No proprietary sample is in the repository or APK.

The 21/W guest-visible OPNA timer, status, and IRQ logic remains in place; ymfm runs its own synthesis timer callbacks. Night Slave booted to its animated title on the Retroid after choosing its regular music driver. The device reported nonzero PCM buffers as the intro advanced. A previous 35-second sample progressed from frame 1,172 to 3,179 and nonzero audio buffers from 5 to 1,544. The final build also booted the game after the rhythm ROM import path was added. Device screenshots and other test files are under ignored `.downloads/` and are not distributable media.

An optional 8 KiB `ym2608_adpcm_rom.bin` can now be imported through Machine settings or first-run setup. The adapter reads it from app-private firmware storage. If it is absent, the original 21/W `2608_*.wav` rhythm path remains. The real ROM and WAV fallback have not been tested on device; the standalone adapter test uses synthetic bytes. Android save states are unavailable, so state load is not an exposed path. OPL3 remains compiled for other board types.

## Audio underrun finding

AAudio reports a 2,658-frame buffer and 886-frame burst on this device. The Night Slave intro accumulated 66 xruns over 886 emulated frames in one run; 600-frame intervals in repeat runs added 22–116 xruns. Instrumentation found average core time of 7.8–9.3 ms/frame, render time of 2.0–3.8 ms/frame, sound mixing below 0.1 ms/frame, and audio write time at or below 0.1 ms/frame. No partial 512-frame writes were recorded in a sampled 600-frame interval. Queued audio at the sampled boundary varied from about 700 to 1,363 frames. Prefilling 1,024 silent frames at startup did not reduce the xrun rate, so that experiment was removed.

A further Night Slave intro run measured 29 new xruns and 53 missed 16.7 ms frame deadlines in a 600-frame interval. Average core and render time were 7.7 and 3.0 ms/frame, but the worst core frame took 28.4 ms and the worst render took 9.8 ms; the largest missed deadline was 15.0 ms. This confirms intermittent slow frames despite the averages being under budget. It does not yet identify which core operation causes the spikes or whether those specific spikes account for every xrun.

The xrun counter is in the left pane diagnostic status; the compact `c`, `r`, `m`, `w`, `x`, `p`, `q`, `maxC`, `maxR`, and `late` values show average core, render, mix, and write milliseconds per frame; interval xruns; partial writes; queued frames; worst core and render milliseconds; and missed frame deadlines with the worst delay. This diagnostic does not alter guest CPU timing or the frame pacing policy.

The remaining work for [issue #8](https://github.com/MrJackSpade/Kairo98/issues/8) is to locate and remove the underrun cause, compare audible FM/SSG/ADPCM/rhythm behavior with desktop 21/W using real sample data, and exercise a game that depends on timer IRQ behavior. Do not call the new audio path release validated yet.
