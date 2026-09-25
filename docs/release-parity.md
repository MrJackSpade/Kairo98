# Release parity

The GitHub and Google Play releases are the same product. The Play purchase pays for distribution and supports development; it does not unlock features.

- Build both releases from the same Git commit and native source snapshots.
- Keep the emulator, controller mapping, flyout keyboard, storage support, save behavior, and launch intents equivalent.
- Do not add ads, DRM, trial limits, or paid-only features to either channel.
- Document unavoidable packaging differences such as APK versus Android App Bundle and signing, without changing user-visible capabilities.
- Publish a version and source revision for every release so users can compare channels.

The current package ID is `com.mrjackspade.kairo98`. Beta test APKs now have a persistent signing identity; stable GitHub and Google Play signing still needs a separate decision. Moving between distribution channels without reinstalling requires compatible signing identities as well as matching package IDs.

## Tagged build artifacts

Pushing a version tag such as `v0.1.0-beta.1` runs `.github/workflows/tag-builds.yml`. It builds two installable debug APKs from that tag: `withImages` includes the 360-pixel catalog artwork, and `withoutImages` omits it. Both package the same JSON catalog, game titles, descriptions, artwork source URLs, emulator code, and controls. The workflow verifies that all shared APK assets match and that only the artwork build contains `art/` assets. It uploads each APK as a separate GitHub Actions artifact for 30 days. For prerelease tags containing a hyphen, it also attaches both APKs as direct downloads on a GitHub prerelease. The tag supplies `versionName`; the workflow run number supplies `versionCode` to both APKs.

The public `v0.1.0-beta.1` prerelease was populated manually from its successful tagged workflow run; later beta tags use the automated release step. These are debug-signed test builds, not stable or Play releases. Release signing and the remaining license audit are separate gates. A future stable GitHub release and paid Play build must use the same source revision and feature set; either distribution can use the artwork flavor once artwork redistribution rights are cleared.

Beta 1 through beta 3 were signed by separate temporary GitHub runners, so Android cannot update one of those APKs with another. New beta builds use one persistent test signing key from repository Actions secrets and CI verifies its SHA-256 certificate fingerprint: `c321ec6b35e2509741362fcc3fede816c81510aa8133d791ea55ce0e1594a51c`. A one-time uninstall is required when moving from beta 1–3 to the first persistently signed beta; later betas can update in place as long as their `versionCode` increases. The recoverable key and password are held in ignored local `.downloads/ci-signing/` files and must be backed up before that workspace is removed. The repository contains no private signing material.
