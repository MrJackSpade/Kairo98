# Source import policy

This repository is independent. It will have one Git remote, `origin`, pointing to this project's repository. Do not add an `upstream` remote, fork relationship, submodule, subtree sync, or automated upstream merge.

## 21/W snapshot

The first source import uses the maintainer's BSD-only 21/W source package corresponding to rev104, published on the [official download page](https://simk98.github.io/np21w/download.html). The Android-specific adapter will remain separate from `third_party/np21w/` where practical.

The downloaded outer archive contains `np21w-src-rev104-bsd.zip`. The source ZIP was extracted using Japanese CP932 filenames. All source ZIP files except its root `.gitignore` and `np2tool/` were copied to `third_party/np21w/`. The excluded `np2tool/` subtree contains guest utilities and prebuilt disk, executable, and driver files that are not needed for the Android emulator. The vendored copy contains 1,613 files. Its original `LICENSES/` directory is preserved.

Preliminary checks found no `sound/fmgen/`, `sound/mame/`, `fpemul_dosbox.c`, or `fpemul_dosbox2.c` in the copied source. `sound/mamebsd/` and the BSD SoftFloat 3 source are present. The source and build configuration still need a full audit before distribution.

## ymfm snapshot

A pinned [ymfm](https://github.com/aaronsgiles/ymfm) source snapshot is in `third_party/ymfm/`. Its root `.gitignore` was omitted; the `LICENSE` file, source, and examples are preserved. The adapter code will remain separate so register, timer, mixing, and sample handling can be tested against known PC-98 games.

## Spleen bitmap font

The ASCII ANK glyphs use [Spleen 8x16](https://github.com/fcambus/spleen), pinned to commit `57f9219328c9f5873085320fe8bc8f7dd34b8791`. The source BDF and BSD-2-Clause license are preserved in `third_party/spleen/`. `generate_ascii.py` produces the 95 printable ASCII glyphs in `spleen_ascii_8x16.h`; the Android build compiles that table. Japanese glyphs are generated at runtime from the device's installed fonts and are not included in the repository or APK.

## Provenance record

| Component | Source version | SHA-256 or commit | License review | Imported |
| --- | --- | --- | --- | --- |
| 21/W BSD-only source | rev104 | Outer ZIP SHA-256 `0630a6f7bc794e9e8a96a090b1a80434f912b6f0c14f744e07e7932e26d0d3fb`; nested source ZIP SHA-256 `5ef56e04c8304b5af527072b3ca356ecd865e11fb293da5eea6a9962b2d491ab` | Preliminary source check; binary audit pending | Yes, 2026-09-22 |
| ymfm | `81aec25ccbb98f4873a255f7551ac4dadac59b4a` | ZIP SHA-256 `5be43559f608e53008b6ab742bcb11acfa1f97fc0e24d42e6b36745e49f94f7a` | BSD-3-Clause license included; binary audit pending | Yes, 2026-09-22 |
| Spleen 8x16 | `57f9219328c9f5873085320fe8bc8f7dd34b8791` | BDF SHA-256 `b38b32a66920068965a3101f98071d310c5c74659fe86e55d346140770f8f6e8` | BSD-2-Clause license included; binary notice packaging pending | Yes, 2026-09-23 |

21/W archive: <https://drive.google.com/file/d/14_byhfNHKf06-nmC60svoGRehaMniL4D/view?usp=drive_link>. ymfm archive: <https://github.com/aaronsgiles/ymfm/archive/81aec25ccbb98f4873a255f7551ac4dadac59b4a.zip>.

Future upstream changes are not merged automatically. Changes needed for this project are reviewed and implemented in this repository with their provenance recorded.

## Local input observation patch

The vendored rev104 snapshot has two Kairo98-only, additive observation hooks. `bios/bios18.c` records BIOS keyboard waits, completed reads, and polls; `io/mouseif.c` records reads of the PC-98 bus mouse data port. The hooks call project-owned `android_host/input_telemetry.c` and do not branch on telemetry values or change guest-visible results. The Android build replaces the imported SDL2 mouse stub with project-owned `android_host/mousemng.c` to accept guest mouse movement and buttons. These changes are local to this repository; the original archive and its provenance above remain the reference snapshot.

## Android/Linux include portability

The imported `diskimage/fddfile.h` and `fdd/sxsicd.c` used `DiskImage/FD` and `DiskImage/CD` in quoted include paths while the actual directories are lowercase. Kairo98 changes those 11 include paths to `diskimage/fd` and `diskimage/cd` so the pinned snapshot builds on case-sensitive Linux CI runners. No code behavior changes.

## Portable IA-32 core

The Android build now compiles the pinned snapshot's portable `i386c/ia32` CPU core instead of its 286 core. The explicit source list includes the snapshot's disabled FPU, MMX, and SSE stubs, but excludes the DOSBox FPU implementations. Three local source corrections make that configuration compile: `i386c/cpumem.c` uses the non-PC-9821 memory handlers in its 32-bit table, `i386c/ia32/paging.c` removes trailing comment backslashes that accidentally continued preprocessor lines, and `i386c/ia32/instructions/sse2/sse2.c` supplies three undefined-instruction stubs in its disabled-SSE2 branch. The source archive above remains the provenance reference for all three files. The IA-32 source list needs to be included in the final binary license audit.

## PC-9821 machine and graphics configuration

The Android core now enables the portable 21/W PC-9821, PEGC, large-memory,
15/31 kHz display, PC-9801-119, PC-9861K, IA-32 paging/reset, and BIOS I/O
features used by the desktop build. `io/pegc.c` is included in the explicit
source list. The pinned `mem/memvga.c` needs one local include of `pegc.h` to
compile its PEGC path on Android. These are project-local build and include
changes to the existing rev104 source; no new source archive was imported.
The Windows-only integrations and excluded fmgen code are not enabled.
