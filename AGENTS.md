# Project instructions

- This is an independent Android PC-98 emulator with its own product name. Credit Neko Project 21/W and ymfm accurately without using their names as this app's brand.
- Keep only this project's `origin` Git remote. The first-party Kairo frontend is pinned as the `shared/` submodule. Do not add an upstream remote, fork relationship, other submodule, subtree sync, or automated upstream merge.
- Use pinned, audited source snapshots in `third_party/`; record provenance and licenses in `docs/source-import.md` and `docs/licensing.md`.
- Exclude fmgen from distributable builds. Do not bundle proprietary BIOS, ROM, operating system, or game files.
- The free GitHub and paid Google Play versions must have the same features and behavior. Build them from the same revision.
- Do not claim a binary is release-ready until it boots a game on an Android device and the license audit is complete.
- Keep tag-triggered GitHub Release publishing enabled. Do not disable, bypass, or replace it with manual publication. When the user asks to push a release tag, treat that as confirmation that testing is sufficient and use the existing tag workflow. The release-ready claim rule above does not impose a new test gate on each tagged APK.
- Write GitHub Release notes from the changes between that tag and the previous tag. Include only release changes and install impacts; keep audit status, device test status, roadmaps, generic build commentary, and unverified claims out of release notes. Verify the published notes against the tagged commits.
