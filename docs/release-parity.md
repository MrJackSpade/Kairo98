# Release parity

The GitHub and Google Play releases are the same product. The Play purchase pays for distribution and supports development; it does not unlock features.

- Build both releases from the same Git commit and native source snapshots.
- Keep the emulator, controller mapping, flyout keyboard, storage support, save behavior, and launch intents equivalent.
- Do not add ads, DRM, trial limits, or paid-only features to either channel.
- Document unavoidable packaging differences such as APK versus Android App Bundle and signing, without changing user-visible capabilities.
- Publish a version and source revision for every release so users can compare channels.

The package ID and signing strategy are still to be decided. Plan for users to move between channels without losing their settings or game access where Android signing rules allow it.

## Tagged build artifacts

Pushing a version tag such as `v0.2.0` runs `.github/workflows/tag-builds.yml`. It builds two installable debug APKs from that tag: `withImages` includes the 360-pixel catalog artwork, and `withoutImages` omits it. Both package the same JSON catalog, game titles, descriptions, artwork source URLs, emulator code, and controls. The workflow verifies that all shared APK assets match and that only the artwork build contains `art/` assets. It uploads each APK as a separate GitHub Actions artifact for 30 days. The tag supplies `versionName`; the workflow run number supplies `versionCode` to both APKs.

These are test artifacts, not a GitHub or Play release. Release signing and the remaining license audit are separate gates. A future free GitHub release and paid Play build must use the same source revision and feature set; either distribution can use the artwork flavor once artwork redistribution rights are cleared.
