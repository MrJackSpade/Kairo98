# Release parity

The GitHub and Google Play releases are the same product. The Play purchase pays for distribution and supports development; it does not unlock features.

- Build both releases from the same Git commit and native source snapshots.
- Keep the emulator, controller mapping, flyout keyboard, storage support, save behavior, and launch intents equivalent.
- Do not add ads, DRM, trial limits, or paid-only features to either channel.
- Document unavoidable packaging differences such as APK versus Android App Bundle and signing, without changing user-visible capabilities.
- Publish a version and source revision for every release so users can compare channels.

The release package ID for both channels is `com.loxifi.kairo98`. Kotlin and JNI retain the internal `com.mrjackspade.kairo98` namespace; it is not a second installable package. GitHub `v0.3.0` and older APKs used `com.mrjackspade.kairo98` as their installable package, so users moving to the Loxifi package need a new install. Play App Signing may use a different signing certificate than the GitHub APK, so switching channels may also require reinstalling even when package IDs match.

## Tagged build artifacts

Pushing a version tag such as `v0.7.0` runs `.github/workflows/tag-builds.yml`. It builds the signed `withoutImagesRelease` APK and matching Play AAB from the same tagged revision and pinned Kairo frontend commit. The workflow checks the signer, package, notices, and APK/AAB asset parity, then retains both artifacts for manual review. Publish the GitHub Release only after the exact APK has booted a game on Android and the license audit is complete. The tag supplies `versionName` and a numeric `versionCode` derived from the version; manual builds use the workflow run number instead.
Before pushing a tag, add `docs/releases/<tag>.md` with the changes in that version. The tag workflow requires that file and uses it verbatim as the GitHub Release notes. Compare the text with the commits since the previous tag; include install impacts, and keep audit status and future work out of the notes.

The APK and Play AAB both show **Download missing images** in the library's right menu. It fetches artwork only for games in the selected ROM library, keeps 360-pixel thumbnails in private app storage, and resumes by skipping files already saved. No catalog artwork is embedded in either distributed artifact.
The public `v0.1.0-beta.1`, `v0.2.0`, and `v0.3.0` releases predate the Loxifi package. `v0.3.1` uses a release build and excludes bundled game artwork. Both channels use the same source revision and feature set.

The release APK and AAB use the persistent key stored in repository Actions secrets. CI verifies its SHA-256 certificate fingerprint: `c321ec6b35e2509741362fcc3fede816c81510aa8133d791ea55ce0e1594a51c`. The recoverable key and password are held in ignored local `.downloads/ci-signing/` files and must be backed up before that workspace is removed. The repository contains no private signing material.
