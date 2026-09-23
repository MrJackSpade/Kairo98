# Release parity

The GitHub and Google Play releases are the same product. The Play purchase pays for distribution and supports development; it does not unlock features.

- Build both releases from the same Git commit and native source snapshots.
- Keep the emulator, controller mapping, flyout keyboard, storage support, save behavior, and launch intents equivalent.
- Do not add ads, DRM, trial limits, or paid-only features to either channel.
- Document unavoidable packaging differences such as APK versus Android App Bundle and signing, without changing user-visible capabilities.
- Publish a version and source revision for every release so users can compare channels.

The package ID and signing strategy are still to be decided. Plan for users to move between channels without losing their settings or game access where Android signing rules allow it.
