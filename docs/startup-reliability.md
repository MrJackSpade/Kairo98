# Startup reliability investigation: RG DS

Investigated on 2026-09-26, starting from `9a8efd8`. Temporary, opt-in debug
instrumentation recorded cancellation reasons, each step's expected hashes,
sample serials and hashes, machine state, and the renderer's CPU/GPU state.
It also captured the raw 640x400 RGB565 buffer when matching stalled. The
diagnostics did not enable the existing always-on hash trace, which would
force CPU rendering early and could conceal the sampling problem.

Logs, sampled pixels, screenshots, diagnostic APKs, and the temporary
instrumentation patch remain local under `.downloads/startup-review/` and
`.downloads/perf-review/`. No game assets are added to the repository.

## 1. Focus loss silently cancels startup

The fourth instrumented cold launch of Night Slave reproduced a silent stall.
`onWindowFocusChanged(false)` set the active queue's cancellation flag roughly
0.9 seconds after matching began, before the menu appeared. The matcher then
returned `ready=false, cancelled=true` and released hash sampling. Samples had
been advancing normally. Evidence: `initial-4/startup.log`.

Startup keys use `InputRouter(::nativeKey)` and do not need Android keyboard
focus. Focus changes can come from another app window or a dialog; they are
not inherently a signal to abandon a game-start operation. The RG DS's companion
keyboard makes this timing easy to encounter, but the cancellation code also
runs on single-screen devices.

The correction releases manual input on focus loss while preserving startup,
disk-swap, and debug automation owners. Stop, reset, new game, and actual
activity lifecycle cancellation retain their full cleanup. Preserving the
owners also prevents a focus transition from shortening an automatic key that
is already held. `tools/StartupInputTest.kt` checks shared manual/automatic
keys, manual-key release, and full cleanup; it passed against the actual router.

With this correction and diagnostics, these ordinary launches all matched:

| Game | Launches | Conditions |
| --- | ---: | --- |
| Night Slave | 20 | 10 cold, 10 with repeated starts in one process |
| Farland Story | 8 | Companion keyboard disabled; first cold, then warm |
| Starfire | 4 | Companion keyboard disabled; first cold, then warm |
| Cyber Arms Val-Kaizer | 8 | Both consecutive prompts; companion disabled |
| GaoGao! 2nd | 2 | Both DOS commands; companion disabled |

Disabling the companion is a single-window test on the RG DS, not testing a
second physical device. These 42 passes do not establish that every possible
startup timeout has been eliminated.

## 2. A sampling/rendering race hashes an old screen

Code inspection found two threads writing `gpudraw.c`'s plain `gpu_enabled`
flag. At machine startup the emulation thread read `screen_hash_sampling`, then
set the GPU switch from that value. The UI could enable sampling and disable
GPU rendering between those operations; the emulation thread could then apply
its stale value and re-enable GPU-only rendering. Hash samples continued to
advance while reading a CPU framebuffer that no longer represented the screen.

A controlled scheduling test paused initialization after it read
`sampling=false`, allowed the UI to enable sampling, and resumed the existing
GPU-switch operation. This forces a permitted thread ordering; it does not
change catalog hashes or guest input. It reproduced the explicit timeout on
Val-Kaizer with the companion keyboard disabled:

- Visible menu, converted back to RGB565: **`8dc81ee93b103d36`**, exactly the
  catalog's expected hash.
- Framebuffer sampled by the matcher: **`61017af65e011935`**, the preceding DOS
  loading screen; 11,634 pixels differed from the visible menu.
- Sample serials continued advancing and machine state remained `Running`.
- After 120 seconds the matcher reported a timeout with **`cancelled=false`**.

Evidence: `forced-race-valkaizer-1/startup.log`, `timeout.log`, `screen.png`,
`sample.rgb565`, `sample.png`, and `timeout.png`. This reproduces the reported
symptom and establishes a concrete cause that is independent of input focus.
The scheduling race was forced; its natural frequency, and whether it explains
every historical timeout, have not been established. Night Slave's text menu
still used CPU fallback and matched even under the same forced ordering.

The correction applies sampling requests only on the emulation thread, before
each frame, and uses that same decision for hashing after the frame. Starting
sampling also requests a full redraw so a static GPU image gets a current CPU
copy. UI requests no longer write renderer state. The same forced ordering
then completed both Val-Kaizer prompts on all three repeat runs
(`forced-race-fixed-1` through `-3`).

## Performance acceptance

Production builds remove the temporary diagnostics and scheduling hooks. Each
correction is measured separately using the Night Slave procedure in
[performance-fix-validation.md](performance-fix-validation.md).

| Build | Core + mixing (ms) | Heavy core (ms) | FPS | Combat underruns |
| --- | ---: | ---: | ---: | ---: |
| Preceding accepted build, `9a8efd8` | 8.667 | 19.244 | 60.004 | 0 |
| Focus correction, diagnostics removed | 8.711 | 19.211 | 59.992 | 0 |

The focus correction is accepted: its 0.044 ms core-plus-mixing increase is
within the 0.089 ms baseline run span measured earlier. Heavy core time decreased
slightly, the combat interval had no underruns, and the screenshot confirms the
same battle workload.
