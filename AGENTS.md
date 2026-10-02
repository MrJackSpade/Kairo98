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

- Android toolchain versions, Gradle conventions, wrapper distribution and CI setup live in pinned `shared/` (see `shared/docs/android-build.md`). Initialize that submodule before building. Product wrappers delegate to it; do not restore duplicate plugin versions or CI setup in either app.

- Installed app ID is `com.loxifi.kairo98`; the launch activity retains the `com.mrjackspade.kairo98.MainActivity` namespace. Always update it with `adb install -r` and verify the installed package before testing. Do not install a second application ID. The obsolete `com.mrjackspade.kairo98` package was removed from the Retroid on September 29, 2026; both devices now have only `com.loxifi.kairo98`.

- This workstation has the Android SDK at `D:\android-sdk` and a populated Gradle cache at `C:\Users\Service Account\.gradle`. The checkout path contains a space, so temporarily map it to an unused drive letter when building native code, then remove the mapping.
- The existing Kairo98 update keystore is `C:\Users\Service Account\Kairo98\.downloads\ci-signing\kairo98-beta.p12`. Its password source is the adjacent `password.txt`. Both are local, ignored files. Load the file into `KAIRO98_BETA_PASSWORD` without printing the value, and set `KAIRO98_BETA_KEYSTORE` to the absolute `.p12` path. Do not commit either file or the password. The keystore alias is `kairo98-beta` and its SHA-256 certificate fingerprint is `C3:21:EC:6B:35:E2:50:97:41:36:2F:CC:3F:ED:E8:16:C8:15:10:AA:81:33:D7:91:EA:55:CE:0E:15:94:A5:1C`.
- `kairo98/build.gradle.kts` uses those environment variables for its `beta` signing config, including debug builds. Build `:kairo98:assembleWithImagesDebug` with the variables set, then install `kairo98/build/outputs/apk/withImages/debug/kairo98-withImages-debug.apk` with `adb install -r`. An ordinary debug APK is signed by a different key and cannot update the installed app.
- When shared controller code changes, pin `shared/` to the same first-party Kairo commit used by KairoDos, build both apps, and update both the Retroid Pocket Classic and RGDS.

# Generated catalog guard

- Edit catalog inputs under `catalog/research/` and `catalog/startup-profiles-v1.json`. Do not hand-edit `catalog/source-v1.json`, `catalog/online-v1.json`, or `kairo98/src/main/assets/catalog/`.
- Regenerate catalog outputs only for an intentional catalog input change, and review the resulting diff before committing.
- Keep this checkout's versioned pre-commit hook active with `git config core.hooksPath .githooks`. It regenerates the catalog from staged inputs in a temporary directory and rejects mismatched generated files before a direct commit to `main`.

- Every tagged release must publish both signed APK variants (with and without bundled images) plus the Play AAB. Do not drop the image-inclusive output when changing release signing or workflows.
