# Night Slave performance investigation

Measured on the Retroid Android device on 2026-09-24 with the same Night Slave HDI and native `-O2` build. Each interval covers 600 emulated frames. The comparison build retained the same temporary profiler, with only the interpreter signal-mask change between runs. A compact per-frame profiler is now available through the Android debug status for follow-up measurements.

| Combat interval | Before | After |
| --- | ---: | ---: |
| Interpreter entries per emulated frame | 31,415 | 32,314 |
| Core CPU time per emulated frame | 14.90 ms | 8.61 ms |
| Emulated frames per real second | 50.1 | 60.0 |
| AAudio underruns per 600 frames | 328 | 1 |
| Render CPU time per emulated frame | 1.20 ms | 1.35 ms |

The comparable intervals had no framebuffer changes; nearby animated combat intervals showed the same direction of improvement. For example, at 23,593 interpreter entries per frame, the patched build ran at 60.0 frames/s with one audio underrun and 177 changed framebuffer samples in 600 frames. Baseline animated intervals at about 19,253 entries per frame ran at 52.7 frames/s with 191 underruns and 151 changed samples. The game appears to update its picture less often than the emulator refreshes, but this alone does not explain the audio crackle. There is no verified reference-emulator frame-rate comparison yet.

An eight-second Android `simpleperf` sample of the baseline combat scene placed 90% of worker-thread CPU cycles under `pccore_exec`. Saving and restoring the host signal mask around the interpreter consumed a large share: `__rt_sigprocmask` alone had 9.9% self time, with further time in `sigsetjmp`, `sigprocmask`, Android's signal chain, and kernel code. The interpreter entered about 12,000–32,000 times per emulated frame in heavy scenes. A standalone benchmark on the same device measured 148 ns per `sigsetjmp(..., 1)` and 12.4 ns per `sigsetjmp(..., 0)`, including occasional `siglongjmp` calls.

The jump buffer handles internal emulated exception and panic control flow. That path does not change the host thread's signal mask, so the interpreter omits the mask save on every platform. This changes host bookkeeping only; it does not change guest CPU clocks, event scheduling, audio samples, or the frame pacing policy. Performance and compatibility have been measured on Android; other platform builds still need validation.

The actual serial event callback fired about 61 times per emulated frame in both builds, consistent with its configured timer. It was the nearest pending event in most samples, but it was not firing tens of thousands of times per frame. Short interpreter slices remain a potential target for future profiling, as do the actual guest CPU instruction hot spots. The current fix removes the cost that made those short slices especially expensive on Android.

Rendering cost about 1–2 ms per combat frame and audio mixing about 0.02–0.03 ms. Moving those operations to separate threads would add synchronization and cannot account for the measured signal-mask cost. The profiler showed AAudio underruns while writes were complete, linking the audible crackle to missed production deadlines rather than partial writes.

The patched build booted Night Slave and reached visible combat on the device. In that run it held 60.0 frames/s in the sampled combat intervals, with 1–5 AAudio underruns per 600 frames. Gameplay was reported as smooth during the device run. Longer play and other games still need regression testing.

## RG DS combat diagnosis, 2026-09-25

`tools/adb_advance_game.ps1` now cold-starts Night Slave, chooses its regular music driver with the catalog's exact-screen matcher, and sends Space from a debug-only app thread every 100 ms. The complete 240-second run sent 2,400 presses in 240.4 seconds. No interval exceeded 125 ms; the longest was 105.2 ms. The saved final frame visibly shows battle. Local evidence is under `.downloads/performance/night-slave/20260925-203302-attempt1/` and is excluded from Git.

For presses 1,600–2,400, the native profiler averaged 29.48 ms in `pccore_exec` per emulated frame, 1.21 ms rendering, and 435 AAudio underruns per 600 emulated frames. All sampled 600-frame battle windows missed the 16.67 ms host frame deadline. Audio mixing rounded below 0.1 ms per frame. The emulator core alone exceeds the frame budget, so moving rendering or mixing off-thread cannot close this gap.

Night Slave uses the interpreted IA-32 CPU core. Its catalog entry changes the GDC clock, but leaves the default CPU multiplier intact: 2.4576 MHz base clock times 20, or about 49.2 MHz of emulated CPU clock. Fitting the measured core and rendering work into a 16.67 ms frame requires roughly 1.9 times the present core throughput. Lowering the emulated CPU clock would reduce the work by changing the guest machine's speed, so it is not an emulator optimization.

A battle `simpleperf` sample attributed the largest flat CPU shares to event progression (`nevent_progress`, 5.6%), instruction dispatch (`exec_allstep`, 4.9%), instruction fetch (`cpu_codefetch`, 4.8%; protected-mode linear byte fetch, 4.5%), and the interpreter's exception boundary (`sigsetjmp`, 3.4%). The load is spread through the interpreted x86 execution path. A frame-pointer call graph placed about 93% of sampled worker-thread cycles under `pccore_exec`; its nested stacks across `siglongjmp` are not reliable for attributing individual instruction handlers.

A diagnostic build changed only the PC-98 core's debug compiler setting from `-O2` to `-O3`. The same battle window measured 29.69 ms core time and 440 underruns per 600 frames. The compiler setting was restored to `-O2`, and the baseline build was reinstalled. This rules out that compiler setting as a useful fix for this workload.

The next execution change needs a named hot path, an explanation of which guest operation it accelerates without changing guest timing, and a repeat of this automated battle comparison. A focused next measurement is the number of guest instructions, interpreter entries, and code-fetch cache misses per frame. That distinguishes dispatch cost from address-translation misses and helps bound the speedup available from either path. The current profile does not justify changing the scheduler, audio buffering, or guest CPU clock.

## RG DS combat, second pass, 2026-09-25

Measured with the same `tools/adb_advance_game.ps1` run on the RG DS (four Cortex-A55 cores at 2 GHz, Android 14, 60 Hz panels) and the same Night Slave HDI. All numbers are averages over 600-frame windows during the sampled battle presses, except the heavy-frame column, which averages every frame whose core time exceeded 20 ms in the battle segment.

| Build | Core per frame | Heavy frame | Late frames / 600 | AAudio underruns / 600 |
| --- | ---: | ---: | ---: | ---: |
| Starting point (`0.3.0-direct-fetch`) | 19.1 to 20.6 ms | n/a | 600 | 300 to 340 |
| Inline code fetch, EIP mask, direct registers | 18.8 ms | n/a | 600 | 290 to 320 |
| Plus data-path TLB fast path | 18.3 to 19.0 ms | n/a | 600 | 290 to 300 |
| Plus idle-loop detection | 10.2 to 10.5 ms | 23.4 ms | 40 to 215 | 22 to 166 |
| Plus presenter thread and worker priority | 10.2 to 10.6 ms | 23.5 ms | 16 to 37 | 4 to 14 |
| Plus thin LTO | 10.2 to 10.5 ms | 23.4 ms | 18 to 42 | 2 to 18 |
| Plus profile-guided optimization | 10.0 to 10.1 ms | 22.8 ms | 8 to 16 | 1 to 3 |
| Plus direct VRAM word path, skip unchanged-frame copy | 9.6 to 10.2 ms | 22.6 ms | 7 to 21 | 1 to 10 |
| Plus EGC shifter fast path (no PGO) | 9.6 to 9.8 ms | 22.4 ms | 4 to 20 | 1 to 5 |
| Final: all of the above with a regenerated PGO profile | 9.4 to 9.6 ms | 21.9 ms | 9 to 13 | 1 to 5 |

### What the guest is doing

The debug status line now reports interpreter slices, guest instructions, STI executions, idle skips, and stage times per emulated frame. In battle the guest executes about 181,000 instructions per frame across 81 slices, and 32,800 of those instructions are STI. That ratio, one STI in every 5.5 instructions, is a wait loop of roughly five instructions (STI, CLI, CMP, Jcc) polling for the vertical-sync interrupt. Night Slave runs its game logic every third vertical sync: one frame of about 129,000 real instructions followed by two frames spent almost entirely in the wait loop. Before this pass the emulator spent most of every frame interpreting that loop.

Host-side profiling alone could not show this. `simpleperf` attributes cycles to interpreter functions, and the same functions are hot whether the guest is blitting sprites or spinning. The guest counters in `i386c/ia32/cpu.c`, `pccore.c`, and `instructions/flag_ctrl.c` were what made the picture unambiguous.

### Idle-loop detection

`i386c/ia32/kairo98_idle.c` is called from the short and near jump macros in `ia32.mcr` whenever a jump goes backward. It records the target EIP, the general, segment, and flag registers, the interpreter slice number, and a side-effect counter. When the same backward jump lands again in the same slice with identical registers and no side effects since, the loop is a fixed point: every remaining iteration in the slice would repeat the same reads and produce the same state. The remaining clocks of the slice are then allowed to elapse, exactly as HLT already does, and the loop resumes at its head after the slice boundary.

The side-effect counter advances on every guest memory write, every read of non-RAM memory (VRAM, I/O-mapped regions), every I/O port access, every interrupt or exception, every CR3 or mode change, RDTSC, RDMSR, RDPMC, and the BIOS call hook. DMA activity and the single-step trap disable the check. Guest timing is unchanged: the guest clock advances by the same slice length, events fire at the same clock, and a pending interrupt is delivered at the same slice boundary it would have been, because STI already ends the slice when an interrupt is pending. In battle the detector skips about 41 times per frame and reduces executed instructions from 181,000 to 51,000 per frame.

### Host-side changes

- `kairo98_fetch.h` inlines the code-page cache check for byte, word, and dword fetches so the dispatch loop and instruction handlers fetch without a call. `cpu_codefetch_slow` remains the miss path.
- `kairo98_tlb.h` exposes the data TLB fast lookups so the segment-level read and write functions in `cpu_mem.mcr` resolve ordinary RAM to a host pointer without descending through the paging layer. The same functions route word reads and writes to the graphics VRAM windows (A8000-BFFFF, E0000-E7FFF) straight to the bank handler that `memp_read16` and `memp_write16` would reach after their range tests.
- `USE_CPU_EIPMASK` and `USE_CPU_DIRECTREG`, which the desktop IA-32 build already uses, are enabled.
- Presentation moved to its own thread in `native_bridge.cpp`. `ANativeWindow_lock` waits for the compositor, and that wait used to be charged to the emulation thread. Frames are copied only when the core reports a redraw or the surface changed. The worker thread runs at the priority Android uses for urgent display work.
- Thin LTO and an optional profile-guided build (`-Pkairo98PgoMode=generate` to record, `-Pkairo98PgoMode=use -Pkairo98PgoProfile=...` to apply) are wired into `CMakeLists.txt`. The profile under `app/src/main/cpp/pgo/` came from the same battle run.

Rusty booted on the final build and played through its intro scenes with correct graphics. Rewriting the 16-bit flag lookup to avoid its 64 KB table measured no change and was reverted. Raising the core's compiler setting had already been ruled out.

### The EGC blitter

Counting EGC accesses puts the battle frame at about 7,480 EGC word writes and 4,907 EGC word reads on average, so roughly 22,000 writes and 15,000 reads in each heavy frame. Sampling the shifter registers at read time shows one configuration: `sft=0x00F0`, `leng=0x000F`, `ope=0x29F0`, mode 2. The game programs the EGC for a single 16-bit transfer with a 15-bit destination offset, reads two source words through the shifter, and writes one destination word with raster operation F0 (source copy). That is the classic shifted sprite copy.

`mem/memegc.c` now has `kairo98_egc_read_inc_fast`, a direct equivalent of `shiftinput_incw` plus the word shifter for two states: an unshifted whole-word transfer, and the first read of a transfer whose destination offset is 8 or more with no source offset. Each case performs exactly the stores and register updates the original would, and falls back to the original otherwise. A `-Pkairo98EgcVerify=true` build runs both paths on every read and counts any divergence on the status line; a full 240-second run reported zero across all 24 windows. Half the battle reads take the fast path (the second read of each word continues with pending shifter state), and the measured gain was about 1%: the EGC engine's cost per access is dominated by reading and writing the four planes, not by the shifter bookkeeping.

### Where the heavy frame goes

Stage timers around `CPU_EXEC`, `nevent_progress`, `scrndraw_draw`, and the FM synthesizer put the average battle frame at 8.7 ms of guest execution, 1.1 ms of event progression, 0.7 ms of screen conversion, and 1.5 ms of FM synthesis. The remaining 22.6 ms heavy frame is therefore almost entirely guest instruction execution, with the EGC blitter path (`egc_writeword`, `egc_readword`, and the memory access chain above them) the largest identifiable block in the flat profile. Bringing that frame under 16.7 ms needs roughly 25% off interpreted execution of a blit-heavy instruction mix; the generic interpreter changes above each measured in single digits.

### Options not taken

Running emulation one frame ahead of a fixed presentation schedule would hide the heavy frame completely, since the two idle frames around it average 4 ms, but it adds 16.7 ms of display and audio latency. It was implemented, measured, and removed; the presenter thread keeps the frame-drop logic so the option is a small change if it is ever wanted as a setting. Moving FM synthesis to an audio thread would remove a fixed 1.5 ms from every frame but requires a timestamped register-write queue; the guest never reads the synthesizer's own timers, so the change is feasible. Converting the screen on the presenter thread from a VRAM snapshot would remove up to another 1.5 ms from heavy frames but has to reproduce every `scrndraw` mode including per-raster palette events.

