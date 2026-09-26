# Performance correctness fixes: RG DS validation

The comparison starts from `e782960` (current main on 2026-09-26). Each fix is
built and tested separately, retaining previously accepted fixes. The device is
the RG DS connected over USB. Builds use the same `withoutImagesDebug` variant,
NDK 28.2.13676358, native `-O2` and thin LTO, with PGO and verification modes off.
The installed app's data is retained, and its original APK is saved locally.

Every timing run cold-launches the Night Slave image with content ID
`sha256-hdi-v1:455d6639f0d3e9e5e72bfe7e89bd82026bdb64ae314a4d077a2cd1e3ed7ff766`,
selects the regular music driver, and uses `tools/adb_advance_game.ps1` to send
2,400 Space presses at 100 ms intervals. The comparison uses the nine status
samples at presses 1,600 through 2,400. Core and mixing times are averaged over
those samples; heavy core time is weighted by the reported count of frames over
12 ms. FPS and additional AAudio underruns use the difference between the first
and last comparison samples. Simpleperf is disabled during these comparisons.

Core plus mixing time is included because moving synthesis work from the
worker to the final mixer must not conceal its cost. Three baseline runs define
the observed run variation. A suspected slowdown or increase in underruns is
rechecked before acceptance. Screenshots confirm the final combat scene, and
thermal-service reports are retained alongside the measurements. This is a
focused workload comparison, not a claim of compatibility with the full library.

Raw logs, APKs, isolated correctness harnesses, and screenshots are local under
`.downloads/perf-review/` and are not distributed.

| Build / run | Core + mixing (ms) | Heavy core (ms) | FPS | Combat underruns |
| --- | ---: | ---: | ---: | ---: |
| Baseline 1 | 8.578 | 19.122 | 60.003 | 1 |
| Baseline 2 | 8.667 | 19.300 | 60.004 | 0 |
| Baseline 3 | 8.611 | 19.189 | 59.994 | 0 |
| Audio wait 1 | 8.733 | 19.367 | 60.001 | 0 |
| Audio wait 2 | 8.678 | 19.367 | 59.993 | 1 |
| Shared audio buffer | 8.744 | 19.333 | 60.004 | 0 |

## Audio wait ordering

`sound_pcmlock()` can prepare missing samples and enqueue FM synthesis. The
host now drains synthesis after that preparation, before consuming or recycling
the stream buffer. An isolated harness using the actual stream-preparation,
lock/unlock, and host-fill functions with a delayed mock synthesizer reproduced
the original failure: sample zero was returned while synthesis remained queued.
With the wait moved, the expected sample 123 is returned with no pending work.
Both paths invoke the drain once.

Accepted after two candidate runs. Their mean core-plus-mixing time is 8.706 ms
versus 8.619 ms for the three baselines: a 0.087 ms increase, comparable to the
0.089 ms span between baseline runs. Heavy core time increased by 0.163 ms
against a baseline span of 0.178 ms. Frame rate and combat underruns remain in
the baseline range. These measurements do not establish zero overhead, but the
differences are within the practical variation observed in this comparison.

## Shared audio buffer ownership

The synthesizer retains completed segments in private, reusable storage.
`drain()` waits for generation to finish and adds those samples on the emulation
thread, after the synchronous PCM, rhythm, and beep callbacks. No worker writes
the shared stream buffer, including configurations with multiple synthesizers.
Generation remains asynchronous and storage capacity is retained across drains.

`tools/ymfm_threading_test.cpp` compares two chips generating batched segments
with synchronous generation, including register changes and another mixer
contribution. Twelve comparisons reported zero differing samples. The Android
`tools/ymfm_smoke.cpp` test passed FM, SSG, timers, ADPCM-B, and generated rhythm
data. That older smoke test needed an explicit drain before reading its output
and explicit volume setup after each reset, matching the app's reset sequence.
Without the volume setup it failed identically on the accepted baseline and
candidate, because reset discards a previously queued volume restore.

Accepted: core plus mixing increased by 0.039 ms relative to the accepted
audio-wait build's two-run mean, smaller than that build's 0.055 ms run span.
Heavy core time decreased slightly, frame rate remained 60 fps, and the combat
interval had no underruns.
