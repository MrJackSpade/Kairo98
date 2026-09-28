# Licensing and third-party credits

Kairo98 first-party code is [GPL-2.0-or-later](../LICENSE.md). The pinned [Kairo frontend](https://github.com/MrJackSpade/Kairo) is also GPL-2.0-or-later. The GPL covers first-party source; third-party components retain their own notices.

The PC-98 emulator is based on a pinned BSD-only Neko Project 21/W rev104 source package. The app uses a pinned [ymfm](https://github.com/aaronsgiles/ymfm) snapshot under BSD-3-Clause for sound emulation. The build excludes fmgen, GPL MAME sound files, and DOSBox FPU sources. The Spleen UI font is BSD-2-Clause; the Shinonome Japanese font is public domain. See [source provenance](source-import.md) for the imported source and build boundaries. Distributed binaries include third-party notices in **About → Licenses**.

Kairo98 does not include BIOS ROMs, game files, operating systems, or commercial font ROM data. Users supply any firmware and games they need.

## Catalog metadata and artwork

Some adult-content flags derive from the [VNDB database dump](https://vndb.org/d14). The derived review data is attributed to VNDB contributors under ODbL 1.0 and DbCL; its source checksum is recorded with the data in `catalog/research/adult-content-v1.json`. The app does not bundle the VNDB dump.

The optional artwork catalog contains downscaled cover and screenshot images with source references in `kairo98/src/withImages/assets/art/catalog-provenance-v1.json`. A source URL does not establish permission to redistribute an image. The distributed `withoutImages` variant omits that artwork and lets users download missing images for their own library. See [artwork rights](art-rights.md) for the project's asset policy.

A distributor must review the exact binary, its corresponding source and notices, and the rights to any included artwork before claiming that binary is ready for release. The audit script is `tools/audit_distribution.ps1`.
