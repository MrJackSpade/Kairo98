# Source import policy

This repository is independent. It will have one Git remote, `origin`, pointing to this project's repository. Do not add an `upstream` remote, fork relationship, submodule, subtree sync, or automated upstream merge.

## 21/W snapshot

The first source import uses the maintainer's BSD-only 21/W source package corresponding to rev104, published on the [official download page](https://simk98.github.io/np21w/download.html). The Android-specific adapter will remain separate from `third_party/np21w/` where practical.

The downloaded outer archive contains `np21w-src-rev104-bsd.zip`. The source ZIP was extracted using Japanese CP932 filenames. All source ZIP files except its root `.gitignore` and `np2tool/` were copied to `third_party/np21w/`. The excluded `np2tool/` subtree contains guest utilities and prebuilt disk, executable, and driver files that are not needed for the Android emulator. The vendored copy contains 1,613 files. Its original `LICENSES/` directory is preserved.

Preliminary checks found no `sound/fmgen/`, `sound/mame/`, `fpemul_dosbox.c`, or `fpemul_dosbox2.c` in the copied source. `sound/mamebsd/` and the BSD SoftFloat 3 source are present. The source and build configuration still need a full audit before distribution.

## ymfm snapshot

A pinned [ymfm](https://github.com/aaronsgiles/ymfm) source snapshot is in `third_party/ymfm/`. Its root `.gitignore` was omitted; the `LICENSE` file, source, and examples are preserved. The adapter code will remain separate so register, timer, mixing, and sample handling can be tested against known PC-98 games.

## Provenance record

| Component | Source version | SHA-256 or commit | License review | Imported |
| --- | --- | --- | --- | --- |
| 21/W BSD-only source | rev104 | Outer ZIP SHA-256 `0630a6f7bc794e9e8a96a090b1a80434f912b6f0c14f744e07e7932e26d0d3fb`; nested source ZIP SHA-256 `5ef56e04c8304b5af527072b3ca356ecd865e11fb293da5eea6a9962b2d491ab` | Preliminary source check; binary audit pending | Yes, 2026-09-22 |
| ymfm | `81aec25ccbb98f4873a255f7551ac4dadac59b4a` | ZIP SHA-256 `5be43559f608e53008b6ab742bcb11acfa1f97fc0e24d42e6b36745e49f94f7a` | BSD-3-Clause license included; binary audit pending | Yes, 2026-09-22 |

21/W archive: <https://drive.google.com/file/d/14_byhfNHKf06-nmC60svoGRehaMniL4D/view?usp=drive_link>. ymfm archive: <https://github.com/aaronsgiles/ymfm/archive/81aec25ccbb98f4873a255f7551ac4dadac59b4a.zip>.

Future upstream changes are not merged automatically. Changes needed for this project are reviewed and implemented in this repository with their provenance recorded.
