# Architecture

Kairo98 and [KairoDos](https://github.com/MrJackSpade/KairoDos) are separate Android apps built around the pinned [Kairo shared frontend](https://github.com/MrJackSpade/Kairo). Shared code owns the library, game details, navigation, controller editor, on-screen input, keyboard presentation, and session flow. Each app supplies media scanning, catalog lookup, guest key definitions, emulator actions, display and audio bridges, product settings, and assets specific to its machine.

Kairo98 adapts the pinned Neko Project 21/W core to Android. The native layer manages PC-98 machine state, disk devices, video, and sound chip registers. Android manages document access, rendering, audio output, controls, settings, and external launch intents. [ymfm](https://github.com/aaronsgiles/ymfm) supplies the FM sound implementation.

The exported `com.loxifi.kairo98.Launch` activity accepts Android `VIEW` intents and LaunchBox's `ROM` extra. An external game is inspected with the same content identity logic used by the library, then staged for the emulator. Returning to **Library** closes an externally launched session so the caller regains focus. See the [frontend setup instructions](../README.md#use-with-launchbox-for-android).

Library matching uses disk contents rather than archive compression. Catalog metadata and startup profiles are separate from user-owned disks. Guest commands and menu choices are sent as normal emulated keys; disk images and imported firmware remain outside the distributed app. See [catalog behavior](game-catalog.md) and [startup choices](startup-choices.md).
