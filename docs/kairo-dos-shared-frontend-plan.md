# Two Android apps, one maintained frontend

27 September 2026. Implementation plan for Kairo98 and KairoDos using a future pinned DOSBox Pure snapshot. The repositories and a shared-library prototype exist; no DOS core has been built.

## Decision

Keep **two Android apps in separate product repositories** and one maintained frontend in a third `Kairo` repository. Each app pins the shared repository as a Git submodule and builds its frontend Android library from that checkout. Each product repository owns its backend, application module, assets, CI, releases, and GitHub Pages site. A shared UI fix has one implementation, followed by an intentional submodule-pointer update and build in each app. Android library modules can be used by multiple app modules, and each app retains its own ID, manifest, assets, and release artifact ([Android libraries](https://developer.android.com/studio/projects/android-library), [app IDs](https://developer.android.com/build/configure-app-module)). [Git submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules) record a specific shared commit in each product; [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages) supports a separate project site for each repository.

Do not copy the frontend into two source trees. A single app module with `pc98` and `dos` flavors is technically possible, but would put two native build configurations, unrelated assets, and separate license audits behind flavor switches in the Kairo98 release pipeline. Separate product repositories also keep their Pages, issues, tags, and release workflows focused on one app. The existing Kairo98 `com.loxifi.kairo98` application ID, signing identity, and app-private data location must remain unchanged.

```text
Kairo repository
  :frontend     Android library: common activity flow, screen views, input routing,
                controller editor, settings UI, viewport, secondary display,
                library presentation, state-slot UI, and backend contracts

Kairo98 repository (shared/ is the pinned Kairo submodule)
  :kairo98      existing app ID/signing; owns icon, catalog, font, notices
  :backend-pc98 21/W + ymfm JNI, PC-98 input, media, machine settings, states
  :frontend     Gradle project mapped to shared/frontend

KairoDos repository (shared/ is the pinned Kairo submodule)
  :kairodos     new app ID/signing; owns icon, DOS defaults and notices
  :backend-dos  pinned DOSBox Pure + libretro host, DOS input/media/states
  :frontend     Gradle project mapped to shared/frontend
```

The app modules supply a small product factory to a shared base activity. A concrete `Kairo98Activity` and `KairoDosActivity` can each provide their backend and product identity. The shared activity must never select a system by package-name comparisons or scattered `if (dos)` checks. The backend contract can live in `:frontend` initially; a separate API module is unnecessary unless it becomes useful for non-Android testing. The native builds stay in their backend modules. Gradle packages each module's native library into the consuming app ([Android native builds](https://developer.android.com/studio/projects/add-native-code)).

The Gradle dependency edges are `:kairo98 -> :frontend + :backend-pc98` in the Kairo98 checkout and `:kairodos -> :frontend + :backend-dos` in the KairoDos checkout. Each product's `settings.gradle.kts` maps `:frontend` to `shared/frontend`; neither product includes the other product's backend. Pin compatible Android Gradle Plugin, Kotlin, and Android SDK versions in both consumers, and test a shared change against both before updating their submodule pointers. Keep `:kairo98`'s namespace initially; give new libraries distinct namespaces and use fully qualified activity names in manifests. Each app manifest supplies its launcher, label, and icon. A shared library manifest can declare the secondary-display activity once ([library dependencies](https://developer.android.com/build/dependencies), [manifest merging](https://developer.android.com/build/manage-manifests)).

The Kairo98 repository keeps only its own `origin` remote. The `shared/` submodule is an explicit user-directed exception to the earlier no-submodule project instruction; it points to the first-party Kairo repository, not to Neko Project, ymfm, or DOSBox upstream. Keep emulator sources as audited snapshots in each product's `third_party/`. Each product CI must initialize the exact recorded shared commit and reject a missing or dirty submodule. Publish a shared commit before advancing either product pointer, then update and verify both consumers. Do not auto-track the shared repo's default branch. The consumer commits and tags are the reproducible release inputs.

## What the current code actually shares

The repository now has `:kairo98` plus a pinned `:frontend` library. `kairo98/build.gradle.kts` owns the PC-98 CMake build, `arm64-v8a` target, signing, version, and artwork variants. The shared extraction covers UI styling, the pixel-font view, D-pad drawing, physical controller defaults, key, joystick and mouse routing, on-screen controls, the controller editor, a configurable guest keyboard panel, settings rows and the touch input settings dialog, and the secondary touchpad. PC-98 key rows, scan codes, controller defaults, and binding validation remain in `:kairo98` and are supplied to the shared components. The manifest still declares `MainActivity` and `SecondaryKeyboardActivity`. `MainActivity.kt` remains a large class containing UI creation, input dispatch, SAF pickers, library work, PC-98 launch and disk swaps, machine settings, save states, and JNI declarations. The JNI functions in `native_bridge.cpp` are exported with `Java_com_mrjackspade_kairo98_MainActivity_*` names; moving native calls out of this class requires coordinated symbol changes. KairoDos currently builds the same pinned library but only presents a development shell with shared keyboard and touch settings previews; it does not emulate DOS or run games.

| Area | Shared code to maintain once | Backend/product-owned data or behavior |
| --- | --- | --- |
| App shell | Library/detail/menu navigation, dialogs, lifecycle, pause reasons, Android storage picker flow, full-screen and secondary-display coordination | Product branding, onboarding steps and words, permission requests actually needed by that app |
| Video and audio | Surface ownership, scaling UI, portrait/crop/integer policy, and mute control | Source frame geometry, pixel aspect ratio, render format, timing, and audio/video output adapter. The current viewport assumes 640×400 (`MainActivity.updateViewport`); DOS changes modes and aspect ratios during play. Share a native output helper only if both backends can use it cleanly. |
| Physical and on-screen input | Device discovery, owner/refcount key release, gamepad parsing, overlay layout, mouse gesture/rate handling, controller editor screens | Guest key codes, key labels and rows, modifier rules, joystick capabilities and defaults. Current `InputRouter` and binding validator enforce PC-98 code range 0–127; `Pc98KeyboardPanel` contains literal PC-98 codes. |
| Touch settings | The same Keyboard/Mouse/touchpad/direct-tap setting screens and per-game override flow | `Auto` resolution and direct-pointer support. Kairo98 uses bus telemetry and a PC-98-specific warp heuristic; Pure does not promise equivalent telemetry. DOS must supply an honest default or per-game policy rather than silently claiming the same detection. |
| Library and game pages | Rows, search, artwork display, details, generic per-game settings layout, SAF tree grant handling | Content scanner and stable identity, artwork/catalog source, launch recipe, media labels. `RomLibrary` currently recognizes PC-98 disk images inside ZIPs; Pure accepts a whole ZIP, executable, CD image, or playlist as content ([Pure content formats](https://docs.libretro.com/library/dosbox_pure/)). |
| Config and settings | One settings renderer and persistence helpers for graphics, sound, controls, input and per-game overrides | Machine setting definitions, validation, defaults, restart semantics, and config conversion. PC-98 base/GDC clocks and BIOS/font/rhythm imports are not DOSBox CPU cycles, sound devices, or drive mounts. |
| State slots | Slot list, thumbnails, confirmations, busy/error UI | State bytes, disk consistency and naming. Current `StateSlots` assumes `state.np2` and copies mounted images. Pure uses libretro serialization and separate writable ZIP save data; its states can fail across CPU/video changes ([Pure save-state notes](https://docs.libretro.com/library/dosbox_pure/)). |

Most screens can be shared, but their models need to stop being `LibraryEntry` and `GameCatalog.Game` specific. The common library view should accept a small `LibraryItem` projection (`id`, title, source label, artwork, tags, playable/error). Each backend owns scanning, content IDs, metadata, launch options, and catalog persistence. A common `GameSettingsSection`/`SettingRow` model lets the same renderer display shared controls and backend-defined machine settings without making one universal machine configuration schema.

## Contracts worth creating

These are boundaries, not a demand to abstract every emulator function now:

```kotlin
interface EmulatorBackend {
    val product: ProductIdentity
    val library: GameLibrary
    val keyboard: GuestKeyboard
    val settings: MachineSettings
    val states: SaveStateService
    fun createSession(): EmulatorSession
}

interface EmulatorSession {
    fun start(launch: LaunchRequest): StartResult
    fun stop()
    fun setPaused(paused: Boolean)
    fun reset()
    fun attachSurface(surface: Surface?, size: Size)
    fun sendKey(code: Int, down: Boolean)
    fun moveMouse(dx: Int, dy: Int)
    fun mouseButton(button: Int, down: Boolean)
    fun joystick(control: Int, down: Boolean)
    fun setMuted(muted: Boolean)
    fun setFastForward(enabled: Boolean)
    val status: SessionStatus
    val video: VideoGeometry
    val capabilities: SessionCapabilities
}
```

`GuestKeyboard` supplies valid codes, labels, page/row definitions, modifiers, default controller bindings, and Android `KeyEvent` translation. The shared `InputRouter`, controller editor, and on-screen keyboard consume it. Keep Kairo98's existing integer scan codes and persisted `controller_global_v1`/per-game bindings valid when moving them; the two Android packages have separate private data, so DOS can use its own key-code range and defaults. The shared code must not assume that one numeric code means the same key in both apps. When a backend lacks a capability, the shared menu omits or disables that action; it does not emulate it with a misleading no-op.

`VideoGeometry` supplies width, height, pixel aspect ratio and mode-change notifications. The shared viewport computes the same user-selected scaling policy for either machine. `SessionStatus` needs structured state and errors, instead of parsing strings beginning `Running`/`Paused` from PC-98 JNI. The contract should expose relative mouse and buttons first. Direct positioning can be an optional capability because the existing Kairo98 corner-warp algorithm is machine specific.

The shared activity owns Android lifecycle and input focus. Each backend owns thread-safe command serialization and its native execution loop. Pure's libretro host must handle the core's environment requests, input polling, video/audio callbacks, changing geometry/timing, content loading, save/system directories, disk control, serialization, and optional hardware rendering where supported. `retro_run` should execute on its own emulation thread with one defined shutdown path; Android UI callbacks must not directly race it. The [libretro core API](https://docs.libretro.com/development/cores/developing-cores/) defines the host/core calls. Pure's [OpenGL 3dfx path](https://github.com/schellingb/dosbox-pure/releases) introduces a separate hardware-rendering requirement; decide explicitly whether the first KairoDos build supports it or presents only software-compatible core options. Do not expose a setting that the host cannot implement.

## Packaging, data, and release boundaries

* Keep `:kairo98` as the Kairo98 application module and preserve its `applicationId`, version migration, signing and storage. Move Kairo98 native code and policy only after the shared contract works, and keep existing Kairo98 `files/`, preferences, saved disks, catalog overrides and `state.np2` slots readable. A namespace or JNI class rename is an internal refactor, not a package-ID change ([Android app ID rules](https://developer.android.com/build/configure-app-module)).
* Give `:kairodos` its own application ID, icon, label, settings/data directories, version code, and signing configuration in its own repository. Both apps may be installed together, but Android does not share their private files. Any future library/profile transfer needs an explicit user export/import path.
* Keep PC-98 catalog JSON, PC-98 art, generated font, and PC-98 notices out of KairoDos. Keep DOSBox source, native `.so`, and DOS notices out of Kairo98. The current `tools/audit_play_release.py` explicitly rejects paths named `dosbox` and requires exactly `libkairo98.so`; retain that Kairo98 gate and add a DOS-specific artifact audit rather than weakening it globally.
* Keep the existing Kairo98 `v*` tagged build until its replacement passes artifact checks. KairoDos has its own tags, workflow, and release notes in its own repository. Build each product's GitHub APK and Play AAB from the same product commit and pinned shared commit, with the same features. Verify native libraries, assets, notices, package ID, signer, and APK/AAB parity. No binary is release-ready before a game boots on an Android device and its license audit is complete.
* Import an audited, pinned Pure source snapshot into `third_party/`; record the exact revision and transitive licenses in `docs/source-import.md` and `docs/licensing.md`. Do not add a second Git remote, upstream sync, or bundled games/operating systems/firmware. Shared frontend source shipped in the GPL KairoDos program needs GPL-compatible distribution terms for that copy; review ownership and notices before distribution.

## Migration order and acceptance gates

1. **Freeze the Kairo98 baseline.** Record the current build tasks and package contents, and run the existing build/audit checks. Capture an on-device behavior list: library launch, keyboard, controller, mouse, secondary display, settings persistence, disk swap, and state restore. This is the comparison for the refactor. The `:app` to `:kairo98` Gradle and directory rename is a separate first cleanup step; it does not change the installed application ID.
2. **Clean the seams before extracting them.** Split `MainActivity` by responsibility while it still runs in Kairo98: UI navigation/lifecycle, input routing, display/audio, SAF/library actions, machine configuration, and JNI commands. Centralize duplicated setting and binding validation, identify PC-98 literals in otherwise shared files, remove dead helpers, and retain the stored preference and state formats. Make small, behavior-preserving changes with focused checks and the device baseline. Avoid a broad rewrite of the emulator core.
3. **Create Kairo and wire Kairo98 to it.** Move the cleaned, backend-neutral UI and contracts into `Kairo`'s `:frontend` Android library. Add the first-party `shared/` submodule to Kairo98 and map `:frontend` to it in Gradle. Keep the current Kairo98 application ID and PC-98 activity while building against the library. Publish the shared commit before pinning it in Kairo98.
4. **Create KairoDos as a separate product repository.** Add its own `:kairodos` application module, backend library, manifest, branding, Pages, and release workflow. Pin the same shared commit. Use a temporary non-emulating backend first so both product targets compile and render the shared UI without copying it. Initialize the submodule in CI and run both consumers for shared changes.
5. **Extract the input and display behavior.** Move owner-based key/mouse/controller routers, overlay, secondary-display shell, viewport, and activity input handling into the frontend. Supply a PC-98 `GuestKeyboard` and geometry so Kairo98 behavior remains the same. Then provide a DOS keyboard definition using libretro's key and mouse inputs. Verify held-key release on menu, pause, display loss, and activity stop in both apps.
6. **Extract library, settings, and state presentation.** Make `LibraryScreen`, `GameDetailPage`, controller editor, first-run screen, game settings, and state slot UI consume backend-supplied models/actions. Keep `RomLibrary`, `GameCatalog`, startup hashes, disk swaps, firmware import, and existing state payloads in the PC-98 backend. Preserve Kairo98 stored keys and user data. Add DOS content models and machine settings without embedding DOS branches in screen code.
7. **Move the PC-98 JNI behind its backend.** Relocate native declarations from `MainActivity` to a dedicated bridge class and update `native_bridge.cpp` symbol names together. Move its CMake target to `:backend-pc98`. Verify that a Kairo98 APK still packages only `libkairo98.so` and that an installed upgrade retains data and can boot a game and restore a state.
8. **Integrate pinned Pure.** Build arm64 Pure in `:backend-dos`, implement the smallest libretro host that correctly supports the chosen content types and software rendering, and verify CPU dynarec, audio, keyboard, mouse, controller, lifecycle, writable saves, and state load on an Android device. Add optional hardware rendering only with a proven host path.
9. **Automate both products.** Keep the Kairo98 release audit strict; add a DOS-specific audit and package-isolation check. For each shared change, advance both product submodule pointers after their builds pass. Build each product's free GitHub and paid Play artifacts from one product commit and its pinned shared commit. Verify install/upgrade behavior and release notes against each product's tagged commit range.

The highest-risk work is the `MainActivity` split, followed by Pure's video/audio host and DOS content semantics. The next useful milestone is two installable debug apps rendering the same library/menu/controller/keyboard UI from one pinned Kairo commit, with Kairo98 still running its existing core and KairoDos using a temporary backend. The current KairoDos shell verifies shared-library consumption and build isolation, but does not yet demonstrate the complete shared app flow.
