# Distribution parity

The free GitHub and paid Google Play editions of Kairo98 have the same features and behavior. The Play purchase pays for distribution and supports development; it does not unlock app features. Both editions are built from the same revision and pinned source snapshots.

The installable package ID is `com.loxifi.kairo98`. APKs released under the older `com.mrjackspade.kairo98` package install separately. Switching distribution channels can require reinstalling if signing certificates differ.

## Version tags and release notes

Pushing a version tag runs the existing [tag workflow](../.github/workflows/tag-builds.yml), which builds and publishes the GitHub Release artifacts. Before tagging, add `docs/releases/<tag>.md` with changes since the previous tag and any install impact. The workflow publishes that text. Device and license audit records are separate from release notes.

The distributed `withoutImages` APK and Play bundle omit catalog artwork while offering the same **Download missing images** feature. The repository does not contain private signing keys.

## Play signing and native packaging

The release certificate remains SHA-256
`C321EC6B35E2509741362FCC3FEDE816C81510AA8133D791EA55CE0E1594A51C`.
When enrolling in Play App Signing, import this existing app signing key to
preserve compatibility with GitHub APK updates. An AAB upload signature does
not determine the final Play-delivered APK signing certificate; confirm it in
Play Console. Do not select a different Google-generated app signing key if
cross-channel updates are required.

The tag workflow checks matching APK/AAB assets and native libraries, 16 KB ELF
alignment and APK zip alignment, in addition to existing signing and content
checks. Packaging checks do not establish runtime compatibility on a 16 KB
Android system or complete the license audit and Play listing requirements.
See [Android app signing](https://developer.android.com/studio/publish/app-signing)
and [16 KB support](https://developer.android.com/guide/practices/page-sizes).
