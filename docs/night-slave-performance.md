# Night Slave performance investigation

Measured on the Retroid Android device on 2026-09-24 with the same Night Slave HDI and native `-O2` build. Each interval covers 600 emulated frames. The comparison build retained the same temporary profiler, with only the interpreter signal-mask change between runs. The profiler has since been removed from source.

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

The patched build booted Night Slave and reached visible combat on the device. In that run it held 60.0 frames/s in the sampled combat intervals, with 1–5 AAudio underruns per 600 frames. The user reported that gameplay seemed smooth. Longer play and other games still need regression testing.
