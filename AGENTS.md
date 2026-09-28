# Project instructions

- This is an independent Android PC-98 emulator with its own product name. Credit Neko Project 21/W and ymfm accurately without using their names as this app's brand.
- Keep only this project's `origin` Git remote. The first-party Kairo frontend is pinned as the `shared/` submodule. Do not add an upstream remote, fork relationship, other submodule, subtree sync, or automated upstream merge.
- Use pinned, audited source snapshots in `third_party/`; record provenance and licenses in `docs/source-import.md` and `docs/licensing.md`.
- Exclude fmgen from distributable builds. Do not bundle proprietary BIOS, ROM, operating system, or game files.
- The free GitHub and paid Google Play versions must have the same features and behavior. Build them from the same revision.
- Do not claim a binary is release-ready until it boots a game on an Android device and the license audit is complete.
- Keep tag-triggered GitHub Release publishing enabled. Do not disable, bypass, or replace it with manual publication. When the user asks to push a release tag, treat that as confirmation that testing is sufficient and use the existing tag workflow. The release-ready claim rule above does not impose a new test gate on each tagged APK.
- Write GitHub Release notes from the changes between that tag and the previous tag. Include only release changes and install impacts; keep audit status, device test status, roadmaps, generic build commentary, and unverified claims out of release notes. Verify the published notes against the tagged commits.

# Local Windows signing and device updates

- This workstation has the Android SDK at `D:\android-sdk` and a populated Gradle cache at `C:\Users\Service Account\.gradle`. The checkout path contains a space, so temporarily map it to an unused drive letter when building native code, then remove the mapping.
- The existing Kairo98 update keystore is `C:\Users\Service Account\Kairo98\.downloads\ci-signing\kairo98-beta.p12`. Its password source is the adjacent `password.txt`. Both are local, ignored files. Load the file into `KAIRO98_BETA_PASSWORD` without printing the value, and set `KAIRO98_BETA_KEYSTORE` to the absolute `.p12` path. Do not commit either file or the password. The keystore alias is `kairo98-beta` and its SHA-256 certificate fingerprint is `C3:21:EC:6B:35:E2:50:97:41:36:2F:CC:3F:ED:E8:16:C8:15:10:AA:81:33:D7:91:EA:55:CE:0E:15:94:A5:1C`.
- `kairo98/build.gradle.kts` uses those environment variables for its `beta` signing config, including debug builds. Build `:kairo98:assembleWithImagesDebug` with the variables set, then install `kairo98/build/outputs/apk/withImages/debug/kairo98-withImages-debug.apk` with `adb install -r`. An ordinary debug APK is signed by a different key and cannot update the installed app.
- When shared controller code changes, pin `shared/` to the same first-party Kairo commit used by KairoDos, build both apps, and update both the Retroid Pocket Classic and RGDS.
