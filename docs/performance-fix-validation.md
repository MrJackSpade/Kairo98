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
| Shared audio buffer, later repeat | 8.656 | 19.278 | 60.018 | 0 |
| Preserve dropped GPU packets | 8.656 | 19.022 | 59.994 | 0 |
| Native window reference | 8.722 | 19.189 | 59.990 | 0 |
| Idle-loop residual cycles (all fixes) | 8.667 | 19.244 | 60.004 | 0 |

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

## GPU packets dropped before presentation

Both queue-drop paths now carry an older packet's updates into its successor
before recycling it. Newer row and palette data win. Packet dirty flags identify
the text and graphics planes independently so changes to a temporarily hidden
plane remain valid across mode changes. The newest complete palette map and
display mode are retained. Complete CPU frames and the full refresh when entering
GPU mode do not require previous deltas.

`tools/gpu_frame_merge_test.cpp` checks preservation of older-only rows, newer
row precedence, palette retention and replacement, independent plane updates
across mode changes, all 256 palette slots, and CPU/GPU transitions. It reports
zero failures. This exercises dropped updates directly; the ordinary Night
Slave timing run does not force a presenter backlog.

Two candidate launches stalled at the music-driver menu before the input runner
started; neither is included in timing results. The user reports intermittent
startup failures on existing builds across multiple games. Reinstalling the
previous accepted APK completed startup and the full comparison again, with
8.656 ms core plus mixing versus its earlier 8.744 ms. The cause of the startup
stalls has not been established; the GPU merge does not run for the CPU-rendered
frames used while matching startup screen hashes.

The unchanged candidate completed startup on its third launch. Accepted: core
plus mixing matched the immediately preceding comparison at 8.656 ms; heavy
core time decreased, frame rate remained approximately 60 fps, and there were
no combat underruns. Thermal status remained zero.

## Native window lifetime

The presenter acquires a temporary `ANativeWindow` reference while holding
`window_mutex`, before Android can replace and release the shared reference.
It releases that temporary reference after `GlPresenter::attach()` has taken
its own reference (or handled attachment failure). The null-window path is
unchanged. This adds one acquire/release pair per surface-generation change,
outside the per-frame rendering path.

Accepted: core plus mixing rose 0.066 ms from the preceding build, within the
original baseline's 0.089 ms span and the 0.088 ms span of the two shared-buffer
runs. Heavy core time was also within the observed baseline range. The run
maintained approximately 60 fps with no combat underruns. Its first launch
stalled before automation; the unchanged APK completed the next launch.

Six background/foreground cycles recreated SurfaceView buffers while retaining
the same app process. The battle image returned after the cycles, with no fatal
signal or `eglSwapBuffers` failure in the captured log. This is a lifecycle smoke
check; the reference ownership change closes the race independently of whether
that particular interleaving occurs during the check.

## Idle-loop event boundary

The detector records remaining clocks at each candidate loop head, calculates
the period between matching states, and skips only whole periods. Instructions
in the residual part of the slice execute normally. This preserves both the
state seen by an event and normal instruction-cycle overshoot, without disabling
the idle-loop optimization.

`tools/test_idle_loop.ps1` compiles the actual checker with mocked CPU state and an
`INC AX / DEC AX / JMP` loop using the core's 2/2/7 instruction costs. It compares
AX, EIP, flags, and remaining cycles against normal execution for budgets 1
through 1,024, with no barrier, side effects, single-step trap, and active DMA:
4,096 cases. The original checker fails 911 cases; the correction fails zero.
Trap, DMA, and side-effect cases do not skip, while 992 ordinary cases still
skip iterations. This checks event-boundary semantics without assuming the
gameplay benchmark exposes them.

Run the check from a Visual Studio developer PowerShell with
`./tools/test_idle_loop.ps1`, or supply `-Compiler` with a host C compiler that
accepts GCC-style arguments. Generated test files stay under `.downloads/`.

Accepted: core plus mixing decreased 0.055 ms from the preceding window-reference
build, with approximately 60 fps and no combat underruns. The cumulative build
is also within the original baseline range: 8.667 ms versus 8.578–8.667 ms for
core plus mixing, and 19.244 ms versus 19.122–19.300 ms for heavy core time.
Instructions increased slightly, from roughly 50,901 to 50,964 per frame,
consistent with executing the residual loop instructions; idle skips remain
about 42 per frame. Thermal status remained zero. No measured regression calls
for a runtime optimization toggle in this workload.

## Separate startup reliability observations

The user reports both intermittent silent stalls and explicit screen-match
timeouts while the expected prompt is visible. These are distinct paths:
`StartupHashMatcher` exits silently when its cancellation flag is set, whereas
its timeout callback requires that matching continued without cancellation.
`MainActivity.onWindowFocusChanged(false)` sets that flag unconditionally, even
though startup keys go through `InputRouter` directly to `nativeKey` and do not
need Android keyboard focus. The RG DS also launches a companion keyboard
activity. This is a plausible cancellation path, not a confirmed diagnosis of
the failed launches recorded above. It cannot explain a genuine hash timeout.

The timeout case needs the sampled hash and sampling progress captured during
failure and compared with the catalog's expected hashes; a visible prompt alone
does not establish what the matcher sampled. Neither startup issue is claimed
fixed by these core performance corrections.

The subsequent [startup reliability investigation](startup-reliability.md)
captured the cancellation path and reproduced a separate stale-frame sampling
race with controlled thread scheduling, including an exact comparison of the
visible menu and sampled pixels.
