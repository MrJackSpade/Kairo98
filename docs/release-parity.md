# Distribution parity

The free GitHub and paid Google Play editions of Kairo98 have the same features and behavior. The Play purchase pays for distribution and supports development; it does not unlock app features. Both editions are built from the same revision and pinned source snapshots.

The installable package ID is `com.loxifi.kairo98`. APKs released under the older `com.mrjackspade.kairo98` package install separately. Switching distribution channels can require reinstalling if signing certificates differ.

## Version tags and release notes

Pushing a version tag runs the existing [tag workflow](../.github/workflows/tag-builds.yml), which builds and publishes the GitHub Release artifacts. Before tagging, add `docs/releases/<tag>.md` with changes since the previous tag and any install impact. The workflow publishes that text. Device and license audit records are separate from release notes.

The distributed `withoutImages` APK and Play bundle omit catalog artwork while offering the same **Download missing images** feature. The repository does not contain private signing keys.
