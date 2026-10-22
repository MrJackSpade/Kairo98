# Licensing and third-party credits

[← Kairo98](../README.md) · [All documentation](../README.md#documentation)

The shared frontend bundles two individually reviewed RetroArch presets: Sharp Bilinear and Sharp Bilinear + Scanlines (both public domain). Their pinned source and per-file notices are in [shared/third_party/slang-shaders](../shared/third_party/slang-shaders/README.md). About -> Licenses includes their attribution. Other upstream shaders are not covered by this review.

Kairo98 first-party code is [GPL-2.0-or-later](../LICENSE.md). The pinned [Kairo frontend](https://github.com/MrJackSpade/Kairo) is also GPL-2.0-or-later. The GPL covers first-party source; third-party components retain their own notices.

The PC-98 emulator is based on a pinned BSD-only Neko Project 21/W rev104 source package. The app uses a pinned [ymfm](https://github.com/aaronsgiles/ymfm) snapshot under BSD-3-Clause for sound emulation. The build excludes fmgen, GPL MAME sound files, and DOSBox FPU sources. The Spleen UI font is BSD-2-Clause; the Shinonome Japanese font is public domain. See [source provenance](source-import.md) for the imported source and build boundaries. Distributed binaries include third-party notices in **About → Licenses**.

Kairo98 does not include BIOS ROMs, game files, operating systems, or commercial font ROM data. Users supply any firmware and games they need.

The shared RetroArch Slang integration uses librashader 0.11.0 under its GPL-3.0-only option. This shader-enabled combined build therefore uses GPLv3 terms. The source snapshot, locked Rust crate archives, and license record are in [shared licensing](../shared/docs/licensing.md). The app appends the shared runtime notice to **About → Licenses**. The NDK `libc++_shared.so` is packaged because the runtime needs it; the existing NDK notices cover that library. Review the exact combined binary and transitive notices before claiming a release is license-audited.

## Catalog metadata and artwork

Some catalog metadata derives from the [VNDB database dump](https://vndb.org/d14). The derived review data is attributed to VNDB contributors under ODbL 1.0 and DbCL; its source checksum is recorded with the research data. The app does not bundle the VNDB dump.

The optional artwork catalog contains downscaled cover and screenshot images, used under fair use, with their sources and checksums in `kairo98/src/withImages/assets/art/catalog-provenance-v1.json`. The `withoutImages` variant omits bundled artwork and downloads images for the games in a user's library.

A distributor must review the exact binary and its corresponding source and notices before claiming that binary is ready for release. The audit script is `tools/audit_distribution.ps1`.
