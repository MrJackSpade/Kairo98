# Source import policy

This repository is independent. It will have one Git remote, `origin`, pointing to this project's repository. Do not add an `upstream` remote, fork relationship, submodule, subtree sync, or automated upstream merge.

## 21/W snapshot

The first source import should start from the maintainer's BSD-only 21/W source package corresponding to rev104, which is published on the [official download page](https://simk98.github.io/np21w/download.html). Before import, record the archive URL, download date, version, SHA-256, included license files, and local modifications in this document. Keep the imported source in `third_party/np21w/` and the Android-specific adapter separate where practical.

The BSD-only package is a starting candidate, not yet a verified build input. Audit the actual files and build definitions before use. No source has been imported as of this document's creation.

## ymfm snapshot

Import an audited, pinned [ymfm](https://github.com/aaronsgiles/ymfm) source snapshot into `third_party/ymfm/`. Record the exact commit and license text at import. Keep the adapter code separate so register, timer, mixing, and sample handling can be tested against known PC-98 games.

## Provenance record

| Component | Source version | SHA-256 or commit | License review | Imported |
| --- | --- | --- | --- | --- |
| 21/W BSD-only source | rev104 candidate | Pending | Pending | No |
| ymfm | Pending | Pending | Pending | No |

Future upstream changes are not merged automatically. Changes needed for this project are reviewed and implemented in this repository with their provenance recorded.
