package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.SecondaryDisplayCoordinator
import com.mrjackspade.kairo.frontend.EdgeSwipeNavigation
import com.mrjackspade.kairo.frontend.ImmersiveWindow
import com.mrjackspade.kairo.frontend.ViewScale
import com.mrjackspade.kairo.frontend.RgDsDisplayRouter
import com.mrjackspade.kairo.frontend.GuestKeyboardPanel
import com.mrjackspade.kairo.frontend.StateSlotCoordinator
import com.mrjackspade.kairo.frontend.StateSlotStore
import com.mrjackspade.kairo.frontend.AppSaveLocation
import com.mrjackspade.kairo.frontend.SaveFolderSettings
import com.mrjackspade.kairo.frontend.SaveLocation
import com.mrjackspade.kairo.frontend.GraphicsOptions
import com.mrjackspade.kairo.frontend.ShaderOptions
import com.mrjackspade.kairo.frontend.GameDeletionFlow

import com.mrjackspade.kairo.frontend.InputRouter
import com.mrjackspade.kairo.frontend.AboutDocuments
import com.mrjackspade.kairo.frontend.InputDispatchCoordinator
import com.mrjackspade.kairo.frontend.FrontendInputScreens
import com.mrjackspade.kairo.frontend.ControllerDeviceMonitor
import com.mrjackspade.kairo.frontend.GuestLifecycleCoordinator
import com.mrjackspade.kairo.frontend.InputModeDecider
import com.mrjackspade.kairo.frontend.JoystickInputRouter
import com.mrjackspade.kairo.frontend.GamepadMapper
import com.mrjackspade.kairo.frontend.OnScreenControls
import com.mrjackspade.kairo.frontend.ControllerPages
import com.mrjackspade.kairo.frontend.ControllerGuestSpec
import com.mrjackspade.kairo.frontend.CatalogArtworkDownloadController
import com.mrjackspade.kairo.frontend.CatalogUpdateController
import com.mrjackspade.kairo.frontend.ControllerProfileStore
import com.mrjackspade.kairo.frontend.ControllerProfileCoordinator
import com.mrjackspade.kairo.frontend.ControllerBinding
import com.mrjackspade.kairo.frontend.TouchInputPolicy
import com.mrjackspade.kairo.frontend.TouchInputSelection
import com.mrjackspade.kairo.frontend.TouchSettingsCoordinator
import com.mrjackspade.kairo.frontend.TouchSettingsStore
import com.mrjackspade.kairo.frontend.TouchUiCoordinator
import com.mrjackspade.kairo.frontend.DocumentPicker
import com.mrjackspade.kairo.frontend.ArtworkCoordinator
import com.mrjackspade.kairo.frontend.ArtworkOverridePath

import com.mrjackspade.kairo.frontend.MouseInputRouter
import com.mrjackspade.kairo.frontend.Ui
import com.mrjackspade.kairo.frontend.LibraryScreen
import com.mrjackspade.kairo.frontend.LibraryFlow
import com.mrjackspade.kairo.frontend.LibraryMenu
import com.mrjackspade.kairo.frontend.ExternalGameIntent
import com.mrjackspade.kairo.frontend.ExternalGameDispatcher
import com.mrjackspade.kairo.frontend.SessionFlow
import com.mrjackspade.kairo.frontend.SessionNavigationCoordinator
import com.mrjackspade.kairo.frontend.GameOrientation
import com.mrjackspade.kairo.frontend.SessionNavigationState
import com.mrjackspade.kairo.frontend.FrontendBackCoordinator
import com.mrjackspade.kairo.frontend.LibraryStrings
import com.mrjackspade.kairo.frontend.PageStack
import com.mrjackspade.kairo.frontend.GroupsPage
import com.mrjackspade.kairo.frontend.ChoicePage
import com.mrjackspade.kairo.frontend.SetupPage
import com.mrjackspade.kairo.frontend.FooterButton
import com.mrjackspade.kairo.frontend.FlyoutModel
import com.mrjackspade.kairo.frontend.SettingsRow
import com.mrjackspade.kairo.frontend.RowGroup
import com.mrjackspade.kairo.frontend.ChoiceSpec
import com.mrjackspade.kairo.frontend.ChoiceGroup
import com.mrjackspade.kairo.frontend.ChoiceOption
import com.mrjackspade.kairo.frontend.Confirm
import com.mrjackspade.kairo.frontend.TraySpec
import com.mrjackspade.kairo.frontend.TrayButton
import com.mrjackspade.kairo.frontend.ButtonTone
import com.mrjackspade.kairo.frontend.ValueTone
import com.mrjackspade.kairo.frontend.GameOverlay
import com.mrjackspade.kairo.frontend.GameNotice
import com.mrjackspade.kairo.frontend.GameValue
import com.mrjackspade.kairo.frontend.GameSource
import com.mrjackspade.kairo.frontend.GameSettingsRows
import com.mrjackspade.kairo.frontend.gameSettingsPage
import com.mrjackspade.kairo.frontend.fileLabel
import com.mrjackspade.kairo.frontend.variantLabel

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity(), SurfaceHolder.Callback {
    private external fun nativeStart(path: String?, fontPath: String, biosDir: String,
                                     mhzTimesTen: Int, gdcMhzTimesTen: Int,
                                     cpuMultiple: Int, floppy: Boolean, bootFloppyPath: String?,
                                     secondFloppyPath: String?): Boolean
    private external fun nativeFloppy(drive: Int, path: String?): Boolean
    private external fun nativeStop()
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeClock(mhzTimesTen: Int)
    private external fun nativeKey(scanCode: Int, down: Boolean)
    private external fun nativeMouseMove(dx: Int, dy: Int)
    private external fun nativeMouseButton(button: Int, down: Boolean)
    private external fun nativeMouseWarp(x: Int, y: Int)
    private external fun nativeMouseWarpDone(): Boolean
    private external fun nativeSaveState(dir: String): Int
    private external fun nativeLoadState(dir: String): Int
    private external fun nativeJoystick(control: Int, down: Boolean)
    private external fun nativeInputTelemetry(): LongArray
    private external fun nativeStatus(): String
    private external fun nativeDosPromptReady(): Boolean
    private external fun nativeSetScreenHashSampling(enabled: Boolean)
    private external fun nativeScreenHashSnapshot(): LongArray
    private external fun nativeSetSurface(surface: Surface?, width: Int, height: Int)
    private external fun nativeSetShaderPreset(path: String?)
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeSetFastForward(enabled: Boolean)

    private val inputRouter = InputRouter(::nativeKey)
    private val joystickRouter = JoystickInputRouter(::nativeJoystick)
    private val mouseRouter = MouseInputRouter(::nativeMouseMove, ::nativeMouseButton)
    private val gamepadMapper = GamepadMapper(inputRouter, joystickRouter, mouseRouter,
        ::controllerAction, ::controllerActionReleased, ControllerBindings.defaults())
    private val inputDispatch: InputDispatchCoordinator by lazy {
        InputDispatchCoordinator(FrontendInputScreens(
            { if (::pages.isInitialized) pages else null },
            { if (::libraryFlow.isInitialized) libraryScreen else null },
            { if (::overlay.isInitialized) overlay else null },
            { !libraryVisible }, { preparingFont }, ::handleBack),
            gamepadMapper, inputRouter, ::pc98ScanCode, ::handleBack,
            { if (menuOpen) closeMenu() else openMenu() },
            {
                if (::root.isInitialized && root.isInTouchMode)
                    (currentFocus ?: screen).requestFocusFromTouch()
            }, ::releaseTouchInputs,
            {
                debugAutoAdvance?.cancel()
                debugAutoAdvance = null
            }, setOf("guest-command", "disk-swap", "debug-auto-space"))
    }
    private val controllerDevices by lazy { ControllerDeviceMonitor(this, inputDispatch::releaseDevice) {
        controllerProfiles.configuration.devicesChanged()
        if (currentDisk == null) gamepadMapper.bindings = controllerFlow.global()
    } }
    private var controllerSetupPending = false
    private var pendingControllerExternalRequest = false
    private val guestLifecycle: GuestLifecycleCoordinator by lazy {
        GuestLifecycleCoordinator({ releaseInputs() }, ::applyPauseState, {}, {},
            initiallyVisible = false,
            companionActive = { ::secondaryKeyboard.isInitialized && secondaryKeyboard.isCompanionActive },
            beforeSuspend = { commandCancelled.set(true) },
            resetGestures = edgeSwipes::reset,
            releaseOnFocusLoss = { releaseInputs(preserveAutomation = true) })
    }

    private val preferences by lazy { getSharedPreferences("kairo98", MODE_PRIVATE) }
    private val saveFolder by lazy {
        SaveFolderSettings(this, pages, preferences, AppSaveLocation(File(filesDir, "states")), FolderStateSlots.UNITS,
            ::saveTitle, { "Slot ${it.removePrefix("slot")}" },
            { if (currentDisk != null) "Return to the library to change the save folder." else null })
    }
    private val graphics by lazy {
        GraphicsOptions(this, preferences) { updateViewport(); refreshSettingValues() }
    }
    private val shaderOptions by lazy {
        ShaderOptions(this, preferences, { if (::pages.isInitialized) pages else null },
            { path -> nativeSetShaderPreset(path); refreshSettingValues() }, ::toast)
    }
    private val controllerProfiles by lazy {
        ControllerProfileStore(preferences, ControllerBindings::parse,
            { ControllerBindings.toJson(it).toString() }, { ControllerBindings.defaults(it) })
    }
    private val controllerFlow by lazy {
        ControllerProfileCoordinator(controllerProfiles, gamepadMapper,
            LibraryEntry::contentId,
            { entry: LibraryEntry -> effectiveControllerBindings(
                romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)) },
            { id, bindings -> romLibrary.catalog.setOverrideSubfield(id, "controller", "bindings",
                ControllerBindings.toJson(bindings)) },
            { id -> romLibrary.catalog.updateOverrideSubfields(id, "controller",
                mapOf("bindings" to null, "profile" to null)) },
            { currentEntry },
            { currentEntry?.let { currentGame = romLibrary.catalog.resolve(
                it.contentId!!, it.displayName) } })
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var screen: SurfaceView
    private lateinit var keyboardPanel: GuestKeyboardPanel
    private lateinit var swappedKeyboardPanel: GuestKeyboardPanel
    private lateinit var secondaryKeyboard: SecondaryDisplayCoordinator
    private lateinit var onScreenControls: OnScreenControls
    private lateinit var libraryFlow: LibraryFlow<LibraryEntry>
    private val libraryScreen: LibraryScreen<LibraryEntry> get() = libraryFlow.screen
    private val touchUi by lazy {
        TouchUiCoordinator(this, libraryScreen, { !libraryVisible },
            { ::secondaryKeyboard.isInitialized && secondaryKeyboard.isKeyboardVisible },
            ::updateViewport, ::applyPauseState,
            { if (::screen.isInitialized) screen.requestFocus() })
    }
    private val touchSettingsCoordinator by lazy {
        TouchSettingsCoordinator(object : TouchSettingsStore<LibraryEntry> {
            override fun global() = TouchInputSelection(globalInputMode, globalTouchDirect)
            override fun saveGlobal(value: TouchInputSelection) {
                globalInputMode = value.mode
                globalTouchDirect = value.directTouch
                preferences.edit()
                    .putString("input_mode", InputModeDecider.storageValue(value.mode))
                    .putString("touch_mouse", touchStorage(value.directTouch)).apply()
            }
            override fun mode(game: LibraryEntry) = inputValue(game, "mode", { it.inputMode },
                InputModeDecider::parse, InputModeDecider::storageValue) { globalInputMode }
            override fun directTouch(game: LibraryEntry) = inputValue(game, "touch", { it.inputTouch },
                { it == "direct" }, ::touchStorage) { globalTouchDirect }
            override fun secondaryTouchpad(game: LibraryEntry) = inputValue(game, "secondary",
                { it.inputSecondary }, { it == "touchpad" }, { if (it) "touchpad" else "keyboard" },
                builtIn = true) { false }
        }, listOf(
            InputModeDecider.Mode.AUTO to ChoiceOption("auto", "Auto", "Follows what the game reads"),
            InputModeDecider.Mode.KEYBOARD to ChoiceOption("keyboard", "Keyboard", "Tap opens the PC-98 keyboard"),
            InputModeDecider.Mode.MOUSE to ChoiceOption("mouse", "Mouse")),
            "Direct tap lands on the touched point in games that move the " +
                "cursor one pixel per mouse count and stop it at the screen\u2019s top-left edge.",
            changed = { entry ->
                if (entry != null && currentEntry?.contentId == entry.contentId) {
                    currentGame = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
                    setSecondaryInitialMode(currentGame?.inputSecondary == "touchpad")
                }
            })
    }

    /**
     * A per-game input choice: the user's override over the catalog, which falls back to the
     * all-games setting, or to the app for the second screen.
     */
    private fun <V> inputValue(game: LibraryEntry, subfield: String, read: (GameCatalog.Game) -> String?,
                               parse: (String) -> V, store: (V) -> String, builtIn: Boolean = false,
                               global: () -> V): GameValue<V> {
        val id = game.contentId!!
        val catalog = romLibrary.catalog
        fun custom() = catalog.sourceOf(id, "input", subfield) == "User override"
        return GameValue({ if (custom()) read(catalog.resolve(id, game.displayName))?.let(parse) else null },
            { read(catalog.resolveDefault(id, game.displayName))?.let(parse) ?: global() },
            { catalog.defaultSourceOf(id, "input", subfield).let { source ->
                if (source == "App default") if (builtIn) GameSource.BUILT_IN else GameSource.ALL_GAMES
                else GameSource.label(source)
            } },
            { value ->
                if (value == null) catalog.resetOverrideSubfield(id, "input", subfield)
                else catalog.setOverrideSubfield(id, "input", subfield, store(value))
            })
    }
    private lateinit var firstRunSetup: FirstRunSetup
    private lateinit var romLibrary: RomLibrary
    private val artwork by lazy {
        ArtworkCoordinator(this, pages, romLibrary.catalog.artworkStore,
            { entry: LibraryEntry -> entry.contentId },
            { entry, kind ->
                val catalog = romLibrary.catalog
                val game = catalog.resolve(entry.contentId ?: "", entry.displayName)
                val urlKind = if (kind == "preview") "previewUrl" else "boxArtUrl"
                val url = (if (kind == "preview") game.previewUrl else game.boxArtUrl)
                    ?.takeIf { entry.contentId?.let { id ->
                        catalog.sourceOf(id, "artwork", kind) ==
                            catalog.sourceOf(id, "artwork", urlKind)
                    } == true }
                ArtworkCoordinator.Record(if (kind == "preview") game.preview else game.boxArt, url,
                    entry.contentId?.let { catalog.sourceOf(it, "artwork", kind) })
            }, { ArtworkOverridePath.valid(it, "art/") },
            { id, kind, path ->
                if (path == null) romLibrary.catalog.resetArtworkOverride(id, kind)
                else romLibrary.catalog.setArtworkOverride(id, kind, path)
            }, { libraryScreen.showEntries(libraryEntries) },
            { entry -> romLibrary.catalog.resolve(entry.contentId ?: "", entry.displayName).title },
            ::toast, GameCatalog::validImageUrl)
    }
    private lateinit var controllerPages: ControllerPages<LibraryEntry>
    private val sessionState = SessionNavigationState()
    private val libraryVisible: Boolean get() = sessionState.libraryVisible
    private val gameOrientation by lazy { GameOrientation(this, preferences) }
    private val sessionNavigation: SessionNavigationCoordinator by lazy {
        SessionNavigationCoordinator(sessionState, libraryScreen,
            { if (::sessionFlow.isInitialized) sessionFlow else null },
            { currentDisk != null }, { sessionFromFrontend }, ::exitApp, edgeSwipes::reset,
            {
                commandCancelled.set(true)
                releaseInputs()
                hideKeyboard()
            }, {}, {
                libraryScreen.showEntries(libraryEntries)
                libraryScreen.showFolder(romTree?.let(::folderName))
                libraryScreen.showStatus("${libraryEntries.count { it.playable }} games ready")
            }, ::applyPauseState, { screen.requestFocus() }, ::endSessionForLibrary, gameOrientation)
    }
    private val backCoordinator: FrontendBackCoordinator by lazy {
        FrontendBackCoordinator(this, { if (::pages.isInitialized) pages else null }, libraryScreen,
            { if (!sessionNavigation.resumeGame()) finish() },
            { if (::keyboardPanel.isInitialized) keyboardPanel else null }, { openMenu() },
            { preparingFont }, ::hideKeyboard)
    }
    private var romTree: Uri?
        get() = libraryFlow.tree
        set(value) { libraryFlow.tree = value }
    private val artworkDownloads by lazy {
        CatalogArtworkDownloadController(this, libraryScreen, { libraryEntries },
            romLibrary.catalog::missingArtworkFor, romLibrary.catalog::downloadArtwork) { pages.refresh() }
    }
    private val catalogUpdates by lazy {
        CatalogUpdateController(this, { task -> romLibrary.catalog.downloadUpdate(task) },
            { message -> if (!message.endsWith("\u2026")) toast(message) }, { libraryScreen.refreshCatalog() },
            { if (libraryVisible) toast("Catalogs updated") }, { libraryMenu.showCatalogProgress(it) },
            romLibrary.catalog.installedCatalogs::update)
    }
    private var currentEntry: LibraryEntry? = null
    private var sessionFromFrontend = false
    private var externalEntries: List<LibraryEntry> = emptyList()
    private val externalDispatcher by lazy {
        ExternalGameDispatcher(this, contentResolver, { libraryEntries },
            { entry: LibraryEntry -> Uri.parse(entry.uri) },
            { file, cancelled -> romLibrary.inspectExternal(file, cancelled) },
            { entries, inspected ->
                if (inspected) externalEntries = entries
                chooseExternalGame(entries)
            }, libraryScreen::showStatus, "Kairo98-external-game")
    }
    private var currentDisk: File? = null
    private var currentIsFloppy = false
    private val mountedFloppies = arrayOfNulls<String>(2)
    private val manualFloppies = arrayOfNulls<File>(2)
    private data class PreparedLaunch(val disk: File, val bootFloppy: LibraryEntry?,
                                      val floppyB: LibraryEntry?,
                                      val swapSources: Map<String, LibraryEntry>,
                                      val gameFont: Boolean)
    /** The running game uses a FONT.BMP from its own folder instead of the global one. */
    private var gameFontActive = false
    private val gameFonts by lazy { GameFontLocator(contentResolver, File(filesDir, "game-font.bmp")) }
    @Volatile private var floppyBusy = false
    private val samplingLock = Any()
    private var samplingUsers = 0
    private val guestSequenceLock = Any()
    @Volatile private var biosBusy = false
    @Volatile private var fontBusy = false
    @Volatile private var rhythmBusy = false
    private var currentTitle: String? = null
    private var currentGame: GameCatalog.Game? = null
    private var commandCancelled = AtomicBoolean(false)
    private var selectedStartup = emptyList<Pair<GameCatalog.StartupChoice, GameCatalog.StartupOption>>()
    private var traceScreenHashes = false
    private var skipDebugChoices = false
    private var skipDebugCommands = false
    private var debugStartupOption: String? = null
    private var debugAdvanceSeconds = 0
    private var debugAdvanceIntervalMs = 100
    private var debugAutoAdvance: DebugAutoAdvance? = null
    private var lastTraceSerial = 0L
    private var libraryEntries: List<LibraryEntry>
        get() = libraryFlow.entries
        set(value) { libraryFlow.entries = value }
    private var pendingDebugGame: String? = null
    private var pendingDebugLaunch: String? = null
    private lateinit var pages: PageStack
    private lateinit var overlay: GameOverlay
    private lateinit var libraryMenu: LibraryMenu<LibraryEntry>
    private lateinit var sessionFlow: SessionFlow
    /** What the progress strip says while a machine stops or starts. */
    private var progressText = ""
    private val menuOpen: Boolean get() = sessionFlow.isOpen
    private var userPaused: Boolean
        get() = sessionState.userPaused
        set(value) { sessionState.userPaused = value }
    private val activityVisible: Boolean get() = guestLifecycle.isVisible
    private var muted = false
    private var clock = 25
    private var exiting = false
    private var relocating = false
    private lateinit var viewScale: ViewScale
    private val edgeSwipes by lazy { EdgeSwipeNavigation(resources.displayMetrics.density) }
    private val inputModeDecider = InputModeDecider()
    private var globalInputMode = InputModeDecider.Mode.AUTO
    private var mouseTouchActive = false
    private var globalTouchDirect = false
    private var pendingMouseWarp: Runnable? = null
    private var mouseDragging = false
    private var mouseMoved = false
    private var mouseTouchStartX = 0f
    private var mouseTouchStartY = 0f
    private var mouseTouchLastX = 0f
    private var mouseTouchLastY = 0f
    private var mouseFractionX = 0f
    private var mouseFractionY = 0f
    private var pendingMouseHold: Runnable? = null
    private var pendingMouseRelease: Runnable? = null
    @Volatile private var preparingFont = false
    private val stateBusy get() = stateFlow.isBusy
    private val stateFlow by lazy {
        StateSlotCoordinator(this, pages, handler,
            { currentEntry?.contentId?.takeIf { !libraryVisible } }, { currentTitle },
            { id ->
                saveFolder.folder?.let { FolderStateSlots(it, id, "state.np2", File(cacheDir, "state-staging")) }
                    ?: StateSlotStore(saveFolder.app.root, id, "state.np2")
            },
            { (if (::secondaryKeyboard.isInitialized) secondaryKeyboard.activeGameSurface else null)
                ?: screen },
            { scratch -> nativeSaveState(scratch.absolutePath) },
            { directory -> nativeLoadState(directory.absolutePath) }, ::stateError, ::toast,
            {
                releaseInputs()
                inputModeDecider.reset()
                userPaused = false
                closeMenu()
            }, { code ->
                // Native media replacement may require restarting from the original disks.
                if (code > 3) currentEntry?.let { entry ->
                    userPaused = false
                    closeMenu()
                    launchEntry(entry, sessionFromFrontend)
                }
            }, "Save states are available for games started from the library.")
    }
    @Volatile private var startGeneration = 0

    private val updateStatus = object : Runnable {
        override fun run() {
            if (::pages.isInitialized && pages.isActive) pages.tick()
            if (!libraryVisible && !preparingFont) {
                InputModeDecider.GuestInput.fromNative(nativeInputTelemetry())?.let {
                    inputModeDecider.observe(it, android.os.SystemClock.elapsedRealtime())
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    private val traceHashes = object : Runnable {
        override fun run() {
            if (traceScreenHashes && !libraryVisible) {
                val sample = nativeScreenHashSnapshot()
                if (sample.size == 2 && sample[0] != 0L && sample[0] != lastTraceSerial) {
                    lastTraceSerial = sample[0]
                    File(filesDir, "startup-hash-trace.txt").appendText(
                        "${android.os.SystemClock.elapsedRealtime()} ${sample[0]} " +
                            "${java.lang.Long.toUnsignedString(sample[1], 16).padStart(16, '0')} " +
                            "${nativeDosPromptReady()} ${nativeStatus()}\n")
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RgDsDisplayRouter.routeToUpper(this)) {
            relocating = true
            return
        }
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            traceScreenHashes = intent.getBooleanExtra("kairo98.traceScreenHashes", false)
            skipDebugChoices = intent.getBooleanExtra("kairo98.skipStartupChoices", false)
            skipDebugCommands = intent.getBooleanExtra("kairo98.skipLaunchCommands", false)
            debugStartupOption = intent.getStringExtra("kairo98.startupOption")
            debugAdvanceSeconds = intent.getIntExtra("kairo98.autoAdvanceSeconds", 0)
                .coerceIn(0, 3600)
            debugAdvanceIntervalMs = intent.getIntExtra("kairo98.autoAdvanceIntervalMs", 100)
                .coerceIn(75, 10000)
            pendingDebugLaunch = intent.getStringExtra("kairo98.launchGame64")
                ?.let(::decodeDebugGameQuery) ?: intent.getStringExtra("kairo98.launchGame")
            pendingDebugGame = intent.getStringExtra("kairo98.selectGame64")
                ?.let(::decodeDebugGameQuery) ?: intent.getStringExtra("kairo98.selectGame")
        }
        graphics.load()
        nativeSetShaderPreset(shaderOptions.selectedPath())
        muted = preferences.getBoolean("muted", false)
        clock = preferences.getInt("base_clock", 25).let { if (it == 20) 20 else 25 }
        globalInputMode = InputModeDecider.parse(preferences.getString("input_mode", "auto"))
        globalTouchDirect = preferences.getString("touch_mouse", "touchpad") == "direct"
        nativeSetMuted(muted)
        if (traceScreenHashes) {
            File(filesDir, "startup-hash-trace.txt").writeText("")
            nativeSetScreenHashSampling(true)
            handler.post(traceHashes)
        }
        controllerFlow.initialize()
        controllerDevices.register(handler)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ImmersiveWindow.apply(this)
        viewScale = ViewScale(this)
        buildUi()
        secondaryKeyboard = SecondaryDisplayCoordinator(this, inputRouter, mouseRouter,
            Pc98KeyboardLayout.value, "Kairo98",
            { available ->
                if (available && secondaryKeyboard.isKeyboardVisible &&
                    keyboardPanel.visibility == View.VISIBLE) {
                    keyboardPanel.close()
                    updateViewport()
                    applyPauseState()
                }
            },
            { surface, width, height -> nativeSetSurface(surface, width, height) },
            { event, width, height -> handleScreenTouch(event, width, height) },
            ::onSecondarySwapChanged)
        romLibrary = RomLibrary(this)
        artwork.restoreInstanceState(savedInstanceState)
        controllerPages = ControllerPages(this, pages,
            controllerFlow::load, controllerFlow::save, controllerFlow::reset,
            { controllerProfiles.globalCustom },
            controllerFlow::physical, controllerFlow::savePhysical, controllerFlow::resetPhysical,
            { controllerProfiles.physicalCustom },
            { controllerFlow.deadZone }, { controllerFlow.deadZone = it },
            ControllerGuestSpec("PC-98", (0..127).toList(),
                Pc98KeyNames::label, setOf(0x70, 0x71, 0x72, 0x73, 0x74, 0x7d),
                ControllerBindings.JOYSTICK.zip(listOf("Up", "Down", "Left", "Right",
                    "Button 1", "Button 2")),
                "Uses the joystick input on the emulated sound board. Games must support joystick 1.",
                listOf("menu" to "Open menu", "pause" to "Pause or resume",
                    "fastForward" to "Fast forward while held", "restart" to "Restart",
                    "exit" to "Exit"), Pc98KeyboardLayout.value),
            { ControllerBindings.toJson(it) }, controllerProfiles.configuration,
            { releaseInputs(); controllerFlow.refresh(null) }, { entry -> controllerMappingStatus(entry) },
            { entry -> romLibrary.catalog.resolve(entry.contentId!!, entry.displayName).title },
            onScreenControls, touchSettingsCoordinator) { releaseInputs() }
        val strings = LibraryStrings("KAIRO98", "Kairo98")
        val libraryPage = LibraryScreen(this, romLibrary.catalog, strings, pages,
            ::chooseRomFolder, { preferences.getString("last_played_entry", null) },
            { launchEntry(it) }, ::showDetailPreview, ::showGameDetails, ::showLibrarySelection)
        libraryFlow = LibraryFlow(this, preferences, libraryPage, ROM_FOLDER_REQUEST,
            romLibrary::cached, romLibrary::scan, ::folderName,
            "Choose a ROM folder to find disk images and ZIP games",
            "Folder access expired. Select the ROM folder again.",
            { entries ->
                val errors = entries.count { it.error != null }
                "${entries.count { it.playable }} games" +
                    (if (errors == 0) "" else " \u00b7 $errors unreadable") +
                    " \u00b7 ${romLibrary.hashCount} hashes this scan"
            },
            { _ ->
                selectPendingDebugGame()
                pendingDebugLaunch?.let { query ->
                    android.util.Log.w("Kairo98", "ADB game not found or ambiguous: $query")
                    pendingDebugLaunch = null
                }
            },
            { _ ->
                if (firstRunSetup.isChoosingRomFolder) advanceFirstRunFirmware(folderChosen = true)
                else refreshLibrary(false)
            },
            { message -> if (firstRunSetup.isOpen) toast(message) })
        libraryMenu = LibraryMenu(this, pages, libraryFlow, romLibrary.catalog, strings,
            { updateGameCatalog(false) },
            { if (resources.getBoolean(R.bool.catalog_art_download_enabled)) artworkDownloads else null },
            ::settingsRows, ::exitApp)
        libraryPage.openMenuAction = libraryMenu::open
        // Pages and the game overlay sit above the library; pages come forward when they open.
        root.addView(libraryScreen, root.indexOfChild(overlay), FrameLayout.LayoutParams(-1, -1))
        touchUi.bind(keyboardPanel)
        firstRunSetup = FirstRunSetup(pages, ::chooseRomFolder, ::advanceFirstRunFirmware,
            ::chooseBiosFile, ::chooseFontFile, ::chooseRhythmFile, ::finishFirstRun,
            { biosFile().isFile }, { fontBitmapFile().isFile }, { rhythmRomFile().isFile },
            { biosBusy }, { fontBusy }, { rhythmBusy })
        backCoordinator.register()
        handler.post(updateStatus)
        controllerSetupPending = controllerProfiles.configuration.needsSetup
        root.post {
            if (isFinishing || isDestroyed) return@post
            if (controllerSetupPending) {
                controllerSetupPending = true
                pages.push(controllerProfiles.configuration.setupPage("KAIRO98", ::finish) {
                    controllerSetupPending = false
                    controllerFlow.initialize()
                    pages.closeAll()
                    continueStartup(savedInstanceState)
                })
            } else continueStartup(savedInstanceState)
        }
    }

    private fun continueStartup(savedInstanceState: Bundle?) {
        libraryFlow.restore()
        val setupStep = preferences.getInt("onboarding_step_v1", if (romTree == null) 0 else 2)
        val externallyRequested = (savedInstanceState == null || pendingControllerExternalRequest ||
            savedInstanceState.getBoolean("controller_setup_external_v1")) &&
            ExternalGameIntent.hasRequest(intent)
        if (externallyRequested) dispatchExternalGame(intent)
        else if (romTree != null && hasRomGrant(romTree!!) && setupStep >= 2 &&
            !selectPendingDebugGame()) refreshLibrary(false)
        if (setupStep < 2 && !externallyRequested) firstRunSetup.show(if (setupStep == 0)
            FirstRunSetup.Step.ROM_FOLDER else FirstRunSetup.Step.FIRMWARE, romTree != null)
        applyPauseState()
        updateGameCatalog(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("controller_setup_external_v1", controllerSetupPending && ExternalGameIntent.hasRequest(intent))
        artwork.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (controllerSetupPending) {
            pendingControllerExternalRequest = true
            return
        }
        dispatchExternalGame(intent)
    }

    private fun dispatchExternalGame(intent: Intent) = externalDispatcher.dispatch(intent)

    private fun chooseExternalGame(entries: List<LibraryEntry>) {
        val playable = entries.filter { it.playable }
        if (playable.isEmpty()) {
            libraryScreen.showStatus("No playable PC-98 disk found")
            return
        }
        val preferred = playable.singleOrNull()
            ?: playable.filter { !it.isFloppy }.singleOrNull()
            ?: playable.filter { it.displayName.contains("boot", true) }.singleOrNull()
        if (preferred != null) { launchEntry(preferred, true); return }
        // Back cancels and leaves Kairo98 open.
        pages.push(ChoicePage(ChoiceSpec("Choose boot disk", { null }, {
            listOf(ChoiceGroup(null, playable.map { ChoiceOption(it.id, it.displayName) }))
        }, { null }, { id ->
            playable.firstOrNull { it.id == id }?.let { launchEntry(it, true) }
        }, stay = true, cycles = false)))
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = SurfaceView(this).apply {
            holder.addCallback(this@MainActivity)
            isFocusableInTouchMode = true
            contentDescription = "PC-98 display"
            setOnTouchListener { view, event -> handleScreenTouch(event, view.width, view.height) }
        }
        root.setOnTouchListener { _, event ->
            !libraryVisible && !menuOpen &&
                touchUi.handleKeyboardTouch(event,
                    inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.KEYBOARD)
        }
        root.addView(screen, FrameLayout.LayoutParams(640, 400, Gravity.CENTER))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
        onScreenControls = OnScreenControls(this, root, gamepadMapper, preferences, ::applyPauseState,
            { controllerProfiles.configuration.layout })

        keyboardPanel = GuestKeyboardPanel(this, inputRouter, Pc98KeyboardLayout.value, ::hideKeyboard)
        root.addView(keyboardPanel, FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM))
        onScreenControls.bindGuestKeyboard(keyboardPanel)
        swappedKeyboardPanel = GuestKeyboardPanel(this, inputRouter, Pc98KeyboardLayout.value,
            {}, showClose = false,
            onSwap = { secondaryKeyboard.toggleSwap() }, mouse = mouseRouter)
        root.addView(swappedKeyboardPanel, FrameLayout.LayoutParams(-1, -1))

        overlay = GameOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        pages = PageStack(this, root)
        pages.menuKey = { if (libraryVisible) libraryScreen.menuKey() else closeMenu() }
        pages.base = { if (libraryVisible && ::libraryFlow.isInitialized) libraryScreen else null }
        onScreenControls.pages = pages
        sessionFlow = SessionFlow(pages, ::gameFlyout, { !libraryVisible },
            {
                commandCancelled.set(true)
                releaseInputs()
                hideKeyboard()
            },
            { open ->
                if (open) onScreenControls.refreshVisibility(false)
                applyPauseState()
            },
            { screen.requestFocus() },
            {
                handler.postDelayed({
                    if (menuOpen && Build.VERSION.SDK_INT >= 30) {
                        window.insetsController?.hide(WindowInsets.Type.statusBars() or
                            WindowInsets.Type.navigationBars())
                    }
                }, 300)
            })

        setContentView(root)
        screen.requestFocus()
    }

    /** One settings list, shown the same way in the game flyout and the library flyout. */
    private fun settingsRows() = listOf(
        SettingsRow.nav("f-graphics", "Graphics") { pages.push(graphics.page(shaderOptions)) },
        SettingsRow.nav("f-controls", "Controls") {
            pages.push(controllerPages.controlsPage(currentEntry?.takeIf { !libraryVisible && it.contentId != null }))
        },
        SettingsRow.nav("f-machine", "Machine") { pages.push(machinePage()) },
        SettingsRow.nav("f-configurations", "Configurations") { pages.push(configurationsPage()) },
        SettingsRow.switch("f-sound", "Sound", { !muted }) { on -> if (on == muted) toggleMute() },
        SettingsRow.nav("f-about", "About") { pages.push(aboutPage()) })

    /** Configurations: settings for all games, such as screen orientation and where saves go. */
    private fun configurationsPage() = GroupsPage("Configurations", { GameSource.ALL_GAMES }, {
        listOf(RowGroup("DISPLAY", listOf(gameOrientation.row())),
            RowGroup("SAVE STATES", listOf(saveFolder.row())))
    })

    /** A save folder's game name, as a library title when the game is still in the library. */
    private fun saveTitle(game: String): String = libraryEntries.firstOrNull {
        it.contentId?.let(SaveLocation::gameName) == game
    }?.let { romLibrary.catalog.resolve(it.contentId!!, it.displayName).title } ?: game

    /** The game flyout: the game's own pages, settings, then leaving. */
    private fun gameFlyout() = FlyoutModel("KAIRO98", { currentTitle ?: "No disk selected" },
        { if (preparingFont) "Preparing PC-98 font" else menuStatusText() }) {
        listOf(
            RowGroup("GAME", stateFlow.rows() + listOf(
                SettingsRow.nav("f-disks", "Disks") { pages.push(disksPage()) },
                SettingsRow.nav("f-game", "Game settings",
                    enabled = { currentEntry != null },
                    disabledReason = { "Game settings are available for games started from the library." }) {
                    currentEntry?.let(::showGameDetails)
                })),
            RowGroup("SETTINGS", settingsRows()),
            RowGroup(null, listOf(
                SettingsRow.action("f-restart", "Restart game",
                    confirm = Confirm("Progress since your last save is lost.", "Restart", run = ::restartMachine)),
                sessionNavigation.returnRow("Kairo98"),
                SettingsRow.action("f-exit", "Exit Kairo98",
                    confirm = Confirm("The machine stops. Progress since your last save is lost.", "Exit",
                        title = "Exit Kairo98?", run = ::exitApp)))))
    }

    /**
     * Machine state, media, and sound for the flyout header. The menu itself pauses the
     * machine, so it reads Paused only when the player paused the game.
     */
    private fun menuStatusText(): String {
        val raw = nativeStatus()
        val state = raw.substringBefore('|').trim().ifEmpty { "Stopped" }
            .let { if (it == "Paused" && !userPaused) "Running" else it }
        val media = when {
            currentDisk == null -> null
            currentIsFloppy -> "Floppy disk"
            else -> "Hard disk"
        }
        return listOfNotNull(state, media, if (muted) "Muted" else null).joinToString(" \u00b7 ")
    }

    private fun refreshSettingValues() {
        if (::pages.isInitialized) pages.refresh()
    }

    private fun toggleMute() {
        muted = !muted
        preferences.edit().putBoolean("muted", muted).apply()
        nativeSetMuted(muted)
        refreshSettingValues()
    }

    private fun updateViewport() {
        if (!::root.isInitialized || root.width <= 0 || root.height <= 0) return
        val keyboardHeight = if (::keyboardPanel.isInitialized &&
            keyboardPanel.visibility == View.VISIBLE) keyboardPanel.layoutParams.height else 0
        val viewport = graphics.viewport(root.width, root.height, keyboardHeight,
            640, 400, 640.0 / 400.0) ?: return
        val params = screen.layoutParams as FrameLayout.LayoutParams
        if (params.width != viewport.width || params.height != viewport.height ||
            params.topMargin != viewport.topMargin ||
            params.gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL)) {
            screen.layoutParams = FrameLayout.LayoutParams(viewport.width, viewport.height,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = viewport.topMargin
            }
        }
    }

    private fun openMenu(start: String? = null, confirm: Boolean = false) = sessionFlow.open(start, confirm)

    private fun closeMenu() = sessionFlow.close()


    /** Shows the selected library game on the second screen, if there is one. */
    private fun showLibrarySelection(entry: LibraryEntry?) {
        if (!::secondaryKeyboard.isInitialized) return
        if (entry == null) {
            secondaryKeyboard.setLibraryInfo(null)
            return
        }
        val game = libraryScreen.metadata(entry)
        val media = entry.zipEntry ?: entry.path
        val tags = listOf(if (DiskFormat.isFloppy(media)) "Floppy disk" else "Hard disk") +
            ((variantLabel(entry.path) ?: entry.zipEntry?.let(::variantLabel))?.split("  ·  ") ?: emptyList())
        val info = SecondaryDisplayCoordinator.LibraryInfo(game.title,
            listOf(fileLabel(entry)) + tags + game.tags,
            game.description ?: "No description available yet.", null, hasArtwork = game.preview != null)
        secondaryKeyboard.setLibraryInfo(info, game.preview, romLibrary.catalog::openArtwork)
    }

    private fun applyPauseState() {
        if (!libraryVisible && ::secondaryKeyboard.isInitialized) {
            secondaryKeyboard.setLibraryInfo(null)
        }
        val presentation = sessionState.presentation(activityVisible, menuOpen, false, preparingFont)
        val showingGuest = presentation.showGuest
        if (::swappedKeyboardPanel.isInitialized) {
            if (::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped && showingGuest &&
                swappedKeyboardPanel.visibility != View.VISIBLE)
                swappedKeyboardPanel.open()
            else if (swappedKeyboardPanel.visibility == View.VISIBLE)
                swappedKeyboardPanel.close()
        }
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setAppearance(
            showingGuest,
            if (::firstRunSetup.isInitialized && firstRunSetup.isOpen) Ui.BG
                else Color.BLACK)
        nativePause(presentation.pauseGuest)
        if (::overlay.isInitialized) {
            if (preparingFont) overlay.showProgress(progressText) else overlay.hideProgress()
            overlay.setPaused(userPaused && !libraryVisible && !menuOpen && !preparingFont)
            if (libraryVisible) overlay.hideNotice()
        }
        if (::onScreenControls.isInitialized) {
            if (::libraryFlow.isInitialized) touchUi.refreshControls(onScreenControls,
                showingGuest, ::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped)
            else onScreenControls.refreshVisibility(false)
        }
    }

    private fun hasRomGrant(uri: Uri) = libraryFlow.hasGrant(uri)

    private fun folderName(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').substringAfterLast(':')
            .ifEmpty { "ROM folder" }
    } catch (_: Exception) { "ROM folder" }

    private fun chooseRomFolder() = libraryFlow.chooseFolder()

    private fun advanceFirstRunFirmware(folderChosen: Boolean) {
        preferences.edit().putInt("onboarding_step_v1", 1).apply()
        firstRunSetup.show(FirstRunSetup.Step.FIRMWARE, folderChosen)
    }

    private fun finishFirstRun() {
        preferences.edit().putInt("onboarding_step_v1", 2).apply()
        firstRunSetup.close()
        applyPauseState()
        val tree = romTree
        if (tree != null && hasRomGrant(tree)) refreshLibrary(false)
        else libraryScreen.showStatus("Choose a ROM folder from the library menu when you\u2019re ready")
    }

    private fun refreshLibrary(forceHash: Boolean) = libraryFlow.refresh(forceHash)

    private fun cancelArtworkDownload() = artworkDownloads.cancel()

    private fun updateGameCatalog(silent: Boolean) = catalogUpdates.check(silent)

    private fun downloadMissingImages() = artworkDownloads.start()

    private fun decodeDebugGameQuery(encoded: String): String? = runCatching {
        String(android.util.Base64.decode(encoded,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
    }.getOrNull()

    private fun selectPendingDebugGame(): Boolean {
        pendingDebugLaunch?.let { query ->
            val entry = libraryScreen.findGameForDebugLaunch(query)
            if (entry != null) {
                pendingDebugLaunch = null
                android.util.Log.i("Kairo98", "ADB launching ${entry.displayName} [$query]")
                launchEntry(entry)
                return true
            }
        }
        val query = pendingDebugGame ?: return false
        if (libraryScreen.selectGame(query)) {
            pendingDebugGame = null
            android.util.Log.i("Kairo98", "Selected library game: $query")
        }
        return false
    }

    private fun launchEntry(entry: LibraryEntry, fromFrontend: Boolean = false) {
        commandCancelled.set(true)
        releaseInputs()
        if (saveFolder.busy) {
            toast("Wait for the saves to finish moving.")
            return
        }
        if (!entry.playable) {
            toast(entry.error ?: "Refresh this entry before playing")
            return
        }
        if (entry !in externalEntries && (romTree == null || !hasRomGrant(romTree!!))) {
            libraryScreen.showStatus("Folder access expired. Select the ROM folder again.")
            return
        }
        val game = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
        debugStartupOption?.let { requested ->
            val selected = game.startupChoices.mapNotNull { choice ->
                choice.options.firstOrNull { it.id == requested }?.let { choice to it }
            }
            if (selected.size == game.startupChoices.size) {
                startEntry(entry, game, selected, fromFrontend)
                return
            }
            File(filesDir, "performance-auto.txt").writeText(
                "state\terror\tstartup option $requested unavailable\n")
            return
        }
        if (game.startupChoices.isNotEmpty() && !skipDebugChoices) {
            chooseStartupOptions(entry, game, 0, emptyList(), fromFrontend)
        } else startEntry(entry, game, emptyList(), fromFrontend)
    }

    /** One choice page per startup choice; Play manually starts without the rest. Back cancels. */
    private fun chooseStartupOptions(entry: LibraryEntry, game: GameCatalog.Game, index: Int,
                                     selected: List<Pair<GameCatalog.StartupChoice,
                                         GameCatalog.StartupOption>>,
                                     fromFrontend: Boolean, replace: Boolean = false) {
        if (index == game.startupChoices.size) {
            startEntry(entry, game, selected, fromFrontend)
            return
        }
        val choice = game.startupChoices[index]
        lateinit var page: ChoicePage
        page = ChoicePage(ChoiceSpec(choice.title, { game.title }, {
            listOf(ChoiceGroup(null, choice.options.map { ChoiceOption(it.id, it.label) },
                "Kairo98 enters your choice when the game reaches this screen. Play manually leaves it to you."))
        }, { null }, { id ->
            val option = choice.options.first { it.id == id }
            pages.postDelayed({
                // Back during the pause cancels, as it does on the page.
                if (pages.top === page) chooseStartupOptions(entry, game, index + 1,
                    selected + (choice to option), fromFrontend, replace = true)
            }, 150)
        }, actions = {
            listOf(SettingsRow.action("manual", "Play manually") { startEntry(entry, game, selected, fromFrontend) })
        }, stay = true, cycles = false))
        if (replace) pages.replace(page) else pages.push(page)
    }

    private fun startEntry(entry: LibraryEntry, game: GameCatalog.Game,
                           choices: List<Pair<GameCatalog.StartupChoice,
                               GameCatalog.StartupOption>>,
                           fromFrontend: Boolean = false) {
        val generation = ++startGeneration
        val cancelled = AtomicBoolean(false)
        if (::pages.isInitialized) pages.closeAll()
        preparingFont = true
        showProgress("Preparing ${entry.displayName}\u2026")
        gameOrientation.launching()
        val fontTree = if (entry in externalEntries) null else romTree
        Thread {
            val result = try {
                val sources = libraryEntries + externalEntries
                val media = bootMediaFor(entry, sources, game.requiredBootFloppyId)
                val swapSources = game.diskSwaps.map { rule ->
                    rule.contentId to requiredFloppyFor(rule.contentId, entry, sources)
                }.toMap()
                val floppyB = game.initialFloppyBId?.let { id ->
                    requiredFloppyFor(id, entry, sources)
                }
                val primary = media?.hardDisk ?: entry
                val disk = romLibrary.prepare(primary, cancelled) { message ->
                    runOnUiThread { if (generation == startGeneration) showProgress(message) }
                }
                val bootFloppy = media?.bootFloppy?.let { companion ->
                    romLibrary.prepare(companion, cancelled) { message ->
                        runOnUiThread { if (generation == startGeneration) showProgress(message) }
                    }
                }
                val secondFloppy = floppyB?.let { companion ->
                    romLibrary.prepare(companion, cancelled) { message ->
                        runOnUiThread { if (generation == startGeneration) showProgress(message) }
                    }
                }
                val gameFont = gameFonts.prepare(primary, fontTree,
                    primary.zipEntry?.let { romLibrary.archive(primary, cancelled) }, cancelled)
                val font = gameFont?.absolutePath ?: prepareFont()
                if (generation != startGeneration) null
                else {
                    nativeStop()
                    if (generation != startGeneration) null
                    else if (nativeStart(disk.absolutePath, font, firmwareDir().absolutePath,
                            game.baseClockTenthsMHz ?: clock,
                            game.gdcClockTenthsMHz ?: 50, game.cpuMultiple ?: DEFAULT_CPU_MULTIPLE,
                            primary.isFloppy,
                            bootFloppy?.absolutePath, secondFloppy?.absolutePath)) {
                        awaitMachineReady()?.let(::error)
                        PreparedLaunch(disk, media?.bootFloppy, floppyB, swapSources, gameFont != null)
                    }
                    else error("Unable to start machine")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (generation == startGeneration) libraryScreen.showStatus(
                        "Launch failed: ${error.message ?: "Unknown error"}")
                }
                null
            }
            runOnUiThread {
                if (generation == startGeneration) {
                    preparingFont = false
                    if (result != null) {
                        controllerProfiles.configuration.endSession()
                        controllerProfiles.configuration.beginSession()
                        currentEntry = entry
                        sessionFromFrontend = fromFrontend
                        preferences.edit().putString("last_played_entry", entry.id).apply()
                        currentDisk = result.disk
                        gameFontActive = result.gameFont
                        currentIsFloppy = result.bootFloppy == null && entry.isFloppy
                        mountedFloppies[0] = result.bootFloppy?.displayName
                            ?: if (entry.isFloppy) entry.displayName else null
                        mountedFloppies[1] = result.floppyB?.displayName
                        currentTitle = game.title
                        currentGame = game
                        setSecondaryInitialMode(game.inputSecondary == "touchpad")
                        selectedStartup = choices
                        inputModeDecider.reset()
                        gamepadMapper.bindings = effectiveControllerBindings(game)
                        userPaused = false
                        sessionFlow.reset()
                        sessionNavigation.enterGame(notify = false)
                        gameOrientation.launched()
                        scheduleStartupQueue(game, choices)
                        scheduleDiskSwaps(game.diskSwaps, result.swapSources)
                    } else gameOrientation.launchFailed()
                    applyPauseState()
                }
            }
        }.start()
    }

    private fun awaitMachineReady(): String? {
        repeat(200) {
            val state = nativeStatus()
            if (state.startsWith("Running") || state.startsWith("Paused")) return null
            if (state.startsWith("Error")) return state.substringBefore(" | ")
            Thread.sleep(50)
        }
        return "Machine startup timed out"
    }

    private fun endSessionForLibrary(done: () -> Unit) {
        commandCancelled.set(true)
        startGeneration++
        preparingFont = true
        releaseInputs()
        hideKeyboard()
        showProgress("Stopping machine\u2026")
        Thread {
            nativeStop()
            runOnUiThread {
                currentDisk = null
                gameFontActive = false
                currentEntry = null
                currentGame = null
                currentTitle = null
                currentIsFloppy = false
                mountedFloppies.fill(null)
                manualFloppies.fill(null)
                selectedStartup = emptyList()
                sessionFromFrontend = false
                userPaused = false
                controllerProfiles.configuration.endSession()
                gamepadMapper.bindings = controllerFlow.global()
                onScreenControls.refreshVisibility(false)
                sessionFlow.reset()
                preparingFont = false
                done()
                rebuildIfRescaled()
            }
        }.apply { name = "Kairo98-end-session"; start() }
    }

    private fun showLibrary() = sessionNavigation.showLibrary()

    private fun touchSelection(game: GameCatalog.Game? = currentGame) =
        TouchInputPolicy.resolve(TouchInputSelection(globalInputMode, globalTouchDirect),
            game?.inputMode?.let(InputModeDecider::parse),
            game?.inputTouch?.let { it == "direct" })

    private fun configuredInputMode(): InputModeDecider.Mode = touchSelection().selection.mode

    private fun showKeyboard() { touchUi.showKeyboard() }

    private fun hideKeyboard() { touchUi.hideKeyboard() }

    private fun moveGuestMouse(event: MotionEvent, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        mouseFractionX += (event.x - mouseTouchLastX) * 640f / width
        mouseFractionY += (event.y - mouseTouchLastY) * 400f / height
        mouseTouchLastX = event.x
        mouseTouchLastY = event.y
        val dx = mouseFractionX.toInt().coerceIn(-640, 640)
        val dy = mouseFractionY.toInt().coerceIn(-400, 400)
        mouseFractionX -= dx
        mouseFractionY -= dy
        if (dx != 0 || dy != 0) nativeMouseMove(dx, dy)
    }

    /** Runs a click once a direct-tap warp has reached the guest; touchpad clicks run now. */
    private fun afterMouseWarp(action: () -> Unit) {
        pendingMouseWarp?.let(handler::removeCallbacks)
        pendingMouseWarp = null
        if (nativeMouseWarpDone()) {
            action()
            return
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 2000
        val poll = object : Runnable {
            override fun run() {
                if (nativeMouseWarpDone()) {
                    pendingMouseWarp = null
                    action()
                } else if (android.os.SystemClock.uptimeMillis() < deadline) handler.postDelayed(this, 8)
                else pendingMouseWarp = null
            }
        }
        pendingMouseWarp = poll
        handler.postDelayed(poll, 8)
    }

    private fun handleScreenTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        if (libraryVisible || menuOpen) return false
        if (touchUi.handleKeyboardTouch(event,
                inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.KEYBOARD))
            return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // With the keyboard on the second screen, the main screen touch is always the mouse.
                mouseTouchActive = (::secondaryKeyboard.isInitialized && secondaryKeyboard.isKeyboardVisible) ||
                    inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.MOUSE
                if (mouseTouchActive) {
                    pendingMouseRelease?.let(handler::removeCallbacks)
                    pendingMouseRelease = null
                    pendingMouseWarp?.let(handler::removeCallbacks)
                    pendingMouseWarp = null
                    mouseRouter.release("touch")
                    mouseDragging = false
                    mouseMoved = false
                    mouseTouchStartX = event.x
                    mouseTouchStartY = event.y
                    mouseTouchLastX = event.x
                    mouseTouchLastY = event.y
                    mouseFractionX = 0f
                    mouseFractionY = 0f
                    // The view is exactly the scaled 640x400 frame, so view coordinates
                    // already account for scaling and for any crop by the parent.
                    if (directTapActive() && width > 0 && height > 0) nativeMouseWarp(
                        (event.x * 640f / width).toInt().coerceIn(0, 639),
                        (event.y * 400f / height).toInt().coerceIn(0, 399))
                    val hold = Runnable {
                        if (mouseTouchActive && !mouseMoved) {
                            mouseDragging = true
                            afterMouseWarp { mouseRouter.hold("touch", "leftButton") }
                        }
                        pendingMouseHold = null
                    }
                    pendingMouseHold = hold
                    handler.postDelayed(hold, 500)
                }
            }
            MotionEvent.ACTION_MOVE -> if (mouseTouchActive) {
                if (!mouseMoved && (abs(event.x - mouseTouchStartX) > dp(12) ||
                    abs(event.y - mouseTouchStartY) > dp(12))) {
                    mouseMoved = true
                    pendingMouseHold?.let(handler::removeCallbacks)
                    pendingMouseHold = null
                }
                moveGuestMouse(event, width, height)
            }
            MotionEvent.ACTION_UP -> {
                if (mouseTouchActive) {
                    pendingMouseHold?.let(handler::removeCallbacks)
                    pendingMouseHold = null
                    moveGuestMouse(event, width, height)
                    if (mouseDragging) mouseRouter.release("touch")
                    else if (!mouseMoved) afterMouseWarp {
                        mouseRouter.hold("touch", "leftButton")
                        val release = Runnable {
                            mouseRouter.release("touch")
                            pendingMouseRelease = null
                        }
                        pendingMouseRelease = release
                        handler.postDelayed(release, 700)
                    }
                    hideKeyboard()
                } else showKeyboard()
                mouseTouchActive = false
                mouseDragging = false
                mouseMoved = false
            }
            MotionEvent.ACTION_CANCEL -> {
                pendingMouseHold?.let(handler::removeCallbacks)
                pendingMouseHold = null
                pendingMouseWarp?.let(handler::removeCallbacks)
                pendingMouseWarp = null
                if (mouseTouchActive) mouseRouter.release("touch")
                mouseTouchActive = false
                mouseDragging = false
                mouseMoved = false
            }
        }
        return true
    }

    private fun scheduleStartupQueue(game: GameCatalog.Game?,
        choices: List<Pair<GameCatalog.StartupChoice, GameCatalog.StartupOption>>) {
        commandCancelled.set(true)
        if (game == null) return
        val steps = (if (skipDebugCommands) emptyList() else game.launchCommands).mapIndexed { index, command ->
            val hashes = game.launchScreenHashes.getOrNull(index) ?: emptySet()
            StartupHashMatcher.Step("Launch command ${index + 1}: $command", command, true,
                hashes, hashes.isEmpty(), game.launchTimeoutMs)
        } + choices.flatMap { (choice, option) ->
            option.inputs.mapIndexed { index, input ->
                StartupHashMatcher.Step("${choice.title}: ${option.label} (${index + 1}/${option.inputs.size})",
                    input.key.toString(), input.enter, input.screenHashes, false, 120000)
            }
        }
        scheduleStartupSteps(steps)
    }

    private fun acquireScreenHashes() = synchronized(samplingLock) {
        if (samplingUsers++ == 0 && !traceScreenHashes) nativeSetScreenHashSampling(true)
    }

    private fun releaseScreenHashes() = synchronized(samplingLock) {
        if (--samplingUsers == 0 && !traceScreenHashes) nativeSetScreenHashSampling(false)
    }

    private fun scheduleStartupSteps(steps: List<StartupHashMatcher.Step>) {
        if (steps.isEmpty()) {
            scheduleDebugAutoAdvance(startGeneration)
            return
        }
        val cancelled = AtomicBoolean(false)
        commandCancelled = cancelled
        val generation = startGeneration
        acquireScreenHashes()
        Thread {
            try {
                val ready = StartupHashMatcher(::nativeScreenHashSnapshot, ::nativeDosPromptReady,
                    { nativeStatus().startsWith("Running") },
                    { cancelled.get() || generation != startGeneration },
                    { step -> sendStartupKeys(step, cancelled, generation) },
                    { step -> runOnUiThread {
                        if (!cancelled.get() && generation == startGeneration)
                            showStartupTimeout(steps, step)
                    } }).run(steps)
                if (ready && !cancelled.get() && generation == startGeneration)
                    scheduleDebugAutoAdvance(generation)
            } catch (error: Exception) {
                runOnUiThread {
                    if (!cancelled.get() && generation == startGeneration)
                        toast(error.message ?: "Startup input failed")
                }
            } finally {
                inputRouter.release("guest-command")
                releaseScreenHashes()
            }
        }.start()
    }

    private fun scheduleDebugAutoAdvance(generation: Int) {
        if (debugAdvanceSeconds <= 0) return
        handler.postDelayed({
            if (generation != startGeneration || libraryVisible) return@postDelayed
            debugAutoAdvance?.cancel()
            debugAutoAdvance = DebugAutoAdvance(inputRouter, ::nativeStatus,
                File(filesDir, "performance-auto.txt"), debugAdvanceSeconds,
                debugAdvanceIntervalMs).also { it.start() }
        }, 2000)
    }

    private fun sendStartupKeys(step: StartupHashMatcher.Step, cancelled: AtomicBoolean,
                                generation: Int, source: String = "guest-command") =
        synchronized(guestSequenceLock) {
        for (character in step.text) {
            if (cancelled.get() || generation != startGeneration) return
            val scans = guestCommandScans(character)
                ?: error("Unsupported startup character: $character")
            inputRouter.hold(source, scans)
            try { Thread.sleep(70) } finally { inputRouter.release(source) }
            Thread.sleep(70)
        }
        if (step.enter && !cancelled.get() && generation == startGeneration) {
            inputRouter.hold(source, listOf(0x1c))
            try { Thread.sleep(70) } finally { inputRouter.release(source) }
        }
    }

    private fun scheduleDiskSwaps(rules: List<GameCatalog.DiskSwap>,
                                  sources: Map<String, LibraryEntry>) {
        if (rules.isEmpty()) return
        val generation = startGeneration
        acquireScreenHashes()
        Thread {
            var failedRule: GameCatalog.DiskSwap? = null
            try {
                DiskSwapMatcher(::nativeScreenHashSnapshot,
                    { nativeStatus().startsWith("Running") },
                    { generation != startGeneration },
                    { rule ->
                        failedRule = rule
                        performDiskSwap(rule, sources.getValue(rule.contentId), generation)
                    }).run(rules)
            } catch (error: Exception) {
                runOnUiThread {
                    if (generation == startGeneration)
                        showDiskSwapFailure(failedRule, error, rules, sources)
                }
            } finally {
                releaseScreenHashes()
            }
        }.start()
    }

    @Synchronized private fun beginFloppyChange(): Boolean {
        if (floppyBusy) return false
        floppyBusy = true
        return true
    }

    @Synchronized private fun endFloppyChange() { floppyBusy = false }

    private fun performDiskSwap(rule: GameCatalog.DiskSwap, entry: LibraryEntry, generation: Int) {
        while (!beginFloppyChange()) {
            if (generation != startGeneration) return
            Thread.sleep(100)
        }
        val notice = GameNotice("Swapping Floppy ${'A' + rule.drive}: ${entry.displayName}", "")
        try {
            runOnUiThread { if (generation == startGeneration) overlay.showNotice(notice) }
            val disk = romLibrary.prepare(entry, AtomicBoolean(false)) { message ->
                runOnUiThread {
                    if (generation == startGeneration && overlay.showing(notice))
                        overlay.showNotice(GameNotice(notice.title, message).also { swapping = it })
                }
            }
            if (generation != startGeneration) return
            if (!nativeFloppy(rule.drive, disk.absolutePath)) error("Floppy could not be mounted")
            runOnUiThread {
                if (generation == startGeneration) {
                    manualFloppies[rule.drive]?.delete()
                    manualFloppies[rule.drive] = null
                    mountedFloppies[rule.drive] = entry.displayName
                }
            }
            if (rule.key.isNotEmpty() || rule.enter) {
                Thread.sleep(150)
                sendStartupKeys(StartupHashMatcher.Step(rule.id, rule.key, rule.enter,
                    emptySet(), false, 0), AtomicBoolean(false), generation, "disk-swap")
            }
        } finally {
            endFloppyChange()
            runOnUiThread {
                if (overlay.showing(notice) || swapping?.let(overlay::showing) == true) overlay.hideNotice()
                swapping = null
            }
        }
    }

    /** The information notice for a swap in progress, replaced as its progress text changes. */
    private var swapping: GameNotice? = null

    /** The game keeps running, so the prompt docks at the top instead of blocking it. */
    private fun showDiskSwapFailure(rule: GameCatalog.DiskSwap?, error: Exception,
                                    rules: List<GameCatalog.DiskSwap>,
                                    sources: Map<String, LibraryEntry>) {
        overlay.showNotice(GameNotice("Automatic disk swap failed",
            "${rule?.let { "Floppy ${'A' + it.drive}: " } ?: ""}" +
                (error.message ?: "Unknown error") + ". The game is still running.",
            listOf(TrayButton("Continue manually"),
                TrayButton("Choose floppy") {
                    openMenu()
                    pages.push(disksPage())
                    pages.push(ChoicePage(floppyChoice(rule?.drive ?: 0), "floppy${rule?.drive ?: 0}"))
                },
                TrayButton("Retry", ButtonTone.PRIMARY) { scheduleDiskSwaps(rules, sources) })))
    }

    private fun showStartupTimeout(steps: List<StartupHashMatcher.Step>,
                                   step: StartupHashMatcher.Step) {
        overlay.showNotice(GameNotice("Startup screen not recognized",
            "Waiting for ${step.label}. The game is still running.",
            listOf(TrayButton("Restart") { openMenu("f-restart", confirm = true) },
                TrayButton("Open keyboard") { showKeyboard() },
                TrayButton("Retry", ButtonTone.PRIMARY) {
                    scheduleStartupSteps(steps.drop(steps.indexOf(step).coerceAtLeast(0)))
                })))
    }

    private fun guestCommandScans(character: Char): List<Int>? = when {
        character in 'a'..'z' -> pc98ScanCode(KeyEvent.KEYCODE_A + (character - 'a'))?.let(::listOf)
        character in 'A'..'Z' -> pc98ScanCode(KeyEvent.KEYCODE_A + (character - 'A'))?.let {
            listOf(0x70, it)
        }
        character in '0'..'9' -> pc98ScanCode(if (character == '0') KeyEvent.KEYCODE_0
            else KeyEvent.KEYCODE_1 + (character - '1'))?.let(::listOf)
        else -> (when (character) {
            ' ' -> 0x34
            '\\' -> 0x0d
            '/' -> 0x32
            '.' -> 0x31
            '-' -> 0x0b
            ':' -> 0x27
            '_' -> 0x33
            else -> null
        })?.let(::listOf)
    }
    /** Game settings over the game page, or over the game flyout while the game runs. */
    private fun showGameDetails(entry: LibraryEntry) {
        pages.push(gameSettingsPage({ titleOf(entry) }) { gameSettings(entry) })
    }

    private fun titleOf(entry: LibraryEntry) =
        romLibrary.catalog.resolve(entry.contentId ?: "", entry.displayName).title

    private fun gameSettings(entry: LibraryEntry): List<RowGroup> {
        val id = entry.contentId
        val catalog = romLibrary.catalog
        val hashed = { id != null }
        val noHash = { GameSettingsRows.NO_HASH }
        val scope = { titleOf(entry) }
        fun game() = catalog.resolve(id ?: "", entry.displayName)
        fun custom(field: String, subfield: String? = null) = id != null && catalog.sourceOf(id, field, subfield) == "User override"
        fun defaultSource(field: String, subfield: String, appDefault: String) =
            { id?.let { catalog.defaultSourceOf(it, field, subfield) }?.takeIf { it != "App default" }
                ?.let(GameSource::label) ?: appDefault }
        fun machine(subfield: String, read: (GameCatalog.Game) -> Int?, fallback: () -> Int, appDefault: String) =
            GameValue({ if (custom("machine", subfield)) read(game())?.toString() else null },
                { (id?.let { read(catalog.resolveDefault(it, entry.displayName)) } ?: fallback()).toString() },
                defaultSource("machine", subfield, appDefault),
                { value -> saveGameSetting(entry) {
                    if (value == null) catalog.resetOverrideSubfield(id!!, "machine", subfield)
                    else catalog.setOverrideSubfield(id!!, "machine", subfield, value.toInt())
                } })
        val controllerSource = {
            val game = game()
            if (game.controllerBindings == null ||
                (game.controllerBindings == "[]" && !game.overriddenFields.contains("controller"))) GameSource.ALL_GAMES
            else GameSource.label(id?.let { catalog.sourceOf(it, "controller", "bindings") } ?: GameSource.ALL_GAMES)
        }
        val base = { if ((game().baseClockTenthsMHz ?: clock) == 20) 1.9968 else 2.4576 }
        val multiples = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, DEFAULT_CPU_MULTIPLE)
        val cpu = machine("cpuMultiple", { it.cpuMultiple }, { DEFAULT_CPU_MULTIPLE }, GameSource.BUILT_IN)
        val library = libraryEntries.any { it.uri == entry.uri }
        return listOfNotNull(
            RowGroup("CONTROLS", listOf(controllerPages.gameRow(entry, hashed, controllerSource)) +
                (id?.let { touchSettingsCoordinator.gameRows(entry, scope, hashed) } ?: listOf(
                    SettingsRow.choice("game-tap", "Screen tap", ChoiceSpec("Screen tap", scope, { emptyList() },
                        { null }, select = {}), enabled = { false }, disabledReason = noHash)))),
            RowGroup("MACHINE", listOf(
                GameSettingsRows.choice("clock", "Machine clock", scope,
                    listOf(ChoiceOption("20", "2 MHz"), ChoiceOption("25", "2.5 MHz")),
                    machine("baseClockTenthsMHz", { it.baseClockTenthsMHz }, { clock }, GameSource.ALL_GAMES),
                    hashed, noHash),
                GameSettingsRows.choice("gdc", "GDC clock", scope,
                    listOf(ChoiceOption("25", "2.5 MHz"), ChoiceOption("50", "5 MHz")),
                    machine("gdcClockTenthsMHz", { it.gdcClockTenthsMHz }, { 50 }, GameSource.BUILT_IN),
                    hashed, noHash),
                GameSettingsRows.choice("cpu", "CPU speed", scope,
                    multiples.map { ChoiceOption("$it", "%.1f MHz".format(base() * it), "\u00d7$it") },
                    cpu, hashed, noHash,
                    defaultLabel = { cpu.fallback().toInt().let { "%.1f MHz (\u00d7%d)".format(base() * it, it) } }),
                GameSettingsRows.text("command", "Startup command", "DOS command, such as GAME",
                    { game().launchCommand }, { custom("launch", "text") },
                    { id?.let { catalog.sourceOf(it, "launch") }?.takeIf { it != "App default" }?.let(GameSource::label) },
                    { value -> saveGameSetting(entry) {
                        if (value.isEmpty()) catalog.updateOverrideSubfields(id!!, "launch",
                            mapOf("text" to null, "commands" to null, "screenHashes" to null))
                        else catalog.updateOverrideSubfields(id!!, "launch",
                            mapOf("text" to value, "commands" to null, "screenHashes" to null))
                    } }, { saveGameSetting(entry) { catalog.resetOverride(id!!, "launch") } },
                    enabled = hashed, disabledReason = noHash)),
                "Machine changes apply on the next launch or restart."),
            RowGroup("LIBRARY", listOf(
                GameSettingsRows.text("title", "Title", "Game title", { game().title }, { custom("title") },
                    { id?.let { GameSource.label(catalog.sourceOf(it, "title")) } ?: "Filename" },
                    { value -> saveGameSetting(entry, "Saved") { catalog.setOverride(id!!, "title", value) } },
                    { saveGameSetting(entry, "Saved") { catalog.resetOverride(id!!, "title") } },
                    enabled = hashed, disabledReason = noHash),
                artwork.row(entry, "boxArt", hashed),
                artwork.row(entry, "preview", hashed)),
                "Chosen images are copied into your library."),
            RowGroup("FILE", GameSettingsRows.file(entry.path, entry.zipEntry, id, entry.error) +
                listOfNotNull(if (!library) null else GameDeletionFlow.row(this, libraryScreen,
                    GameDeletionFlow.Prompt(entry.path, "file",
                        affectedEntries = libraryEntries.count { it.uri == entry.uri }),
                    "ROM folder", { romTree?.let(libraryFlow::hasWriteGrant) == true }, ::chooseRomFolder,
                    { !libraryVisible && currentEntry?.uri == entry.uri }, "Kairo98-delete-game",
                    { romLibrary.deleteSource(entry, libraryEntries) }, { refreshLibrary(false) }))),
            RowGroup(null, listOf(GameSettingsRows.reset(hashed) {
                saveGameSetting(entry, "Game settings reset. Machine changes apply on next launch or restart.",
                    "Could not reset game settings") {
                    catalog.resetOverride(id!!)
                    if (currentEntry?.contentId == id) {
                        currentGame = catalog.resolve(id, entry.displayName)
                        setSecondaryInitialMode(currentGame?.inputSecondary == "touchpad")
                        gamepadMapper.bindings = effectiveControllerBindings(currentGame)
                    }
                }
            })))
    }

    private fun showDetailPreview(entry: LibraryEntry) = artwork.view(entry)

    private fun saveGameSetting(entry: LibraryEntry,
                                message: String = "Saved. Machine changes apply on next launch or restart.",
                                failure: String? = null, action: () -> Unit) {
        try {
            action()
            if (currentEntry?.contentId == entry.contentId && entry.contentId != null)
                currentGame = romLibrary.catalog.resolve(entry.contentId, entry.displayName)
            libraryScreen.showEntries(libraryEntries)
            toast(message)
        } catch (error: Exception) {
            toast(failure ?: "Could not save: ${error.message ?: "Invalid value"}")
        }
        pages.refresh()
    }

    /** CPU MHz for a clock multiple on the game's base clock (2.4576 or 1.9968 MHz). */
    private fun cpuMhz(game: GameCatalog.Game, multiple: Int) =
        (if ((game.baseClockTenthsMHz ?: clock) == 20) 1.9968 else 2.4576) * multiple

    private fun restartMachine() {
        commandCancelled.set(true)
        releaseInputs()
        currentEntry?.let { entry ->
            userPaused = false
            closeMenu()
            launchEntry(entry, sessionFromFrontend)
            return
        }
        val disk = currentDisk
        if (disk == null || !disk.isFile) {
            closeMenu()
            chooseHdi()
            return
        }
        val state = nativeStatus()
        userPaused = false
        closeMenu()
        if (state.startsWith("Running") || state.startsWith("Paused") ||
            state.startsWith("Starting")) {
            nativeReset()
            inputModeDecider.reset()
            scheduleStartupQueue(currentGame, selectedStartup)
        } else {
            startWithFont(disk, "Starting machine", currentIsFloppy)
        }
    }

    private val machineRunning get() = currentDisk != null &&
        nativeStatus().let { it.startsWith("Running") || it.startsWith("Paused") }

    /** Disks: the hard disk, which asks first because it ends the game, and both floppy drives. */
    private fun disksPage() = GroupsPage("Disks", { currentTitle }, {
        listOf(RowGroup("DRIVES", listOf(
            SettingsRow.action("hdd", "Hard disk",
                value = { if (currentIsFloppy) "Empty" else currentEntry?.displayName ?: currentTitle ?: "Empty" },
                tone = { if (currentDisk == null || currentIsFloppy) ValueTone.EMPTY else ValueTone.CURRENT },
                tray = {
                    if (currentDisk == null) TraySpec(buttons = listOf(TrayButton("Choose file\u2026", ButtonTone.PRIMARY) { chooseHdi() }))
                    else TraySpec("Starting another disk image ends this game. Progress since your last save is lost.",
                        listOf(TrayButton("Cancel"), TrayButton("Choose file\u2026", ButtonTone.PRIMARY) { chooseHdi() }), focus = 0)
                }),
            floppyRow(0), floppyRow(1))))
    })

    private fun floppyRow(drive: Int) = SettingsRow.choice("floppy$drive", "Floppy ${'A' + drive}", floppyChoice(drive),
        value = { mountedFloppies[drive] ?: "Empty" },
        tone = { if (mountedFloppies[drive] == null) ValueTone.EMPTY else ValueTone.CURRENT },
        enabled = { machineRunning && !floppyBusy },
        disabledReason = { if (floppyBusy) "Wait for the floppy change to finish" else "Start a game before inserting a floppy" })

    /** Empty ejects; other disks from the same ZIP or folder; or a file from the device. */
    private fun floppyChoice(drive: Int): ChoiceSpec {
        fun candidates(): List<LibraryEntry> {
            val current = currentEntry ?: return emptyList()
            return libraryEntries.filter { entry ->
                entry.playable && entry.isFloppy &&
                    (if (current.zipEntry != null) entry.uri == current.uri
                    else entry.path.substringBeforeLast('/', "") == current.path.substringBeforeLast('/', ""))
            }
        }
        return ChoiceSpec("Floppy ${'A' + drive}", { currentTitle }, {
            val found = candidates()
            listOfNotNull(ChoiceGroup(null, listOf(ChoiceOption("empty", "Empty")),
                if (found.isEmpty()) "No other disk images in this game\u2019s folder." else null),
                found.takeIf { it.isNotEmpty() }?.let { items ->
                    ChoiceGroup("IN THIS GAME\u2019S FOLDER", items.map { ChoiceOption(it.id, it.displayName) })
                })
        }, { candidates().firstOrNull { it.displayName == mountedFloppies[drive] }?.id
            ?: if (mountedFloppies[drive] == null) "empty" else null }, { id ->
            if (id == "empty") changeFloppy(drive, null)
            else candidates().firstOrNull { it.id == id }?.let { changeFloppy(drive, it) }
        }, actions = {
            listOf(SettingsRow.action("choose", "Choose file\u2026") { chooseFloppyFile(drive) })
        }, cycles = false)
    }

    private fun changeFloppy(drive: Int, entry: LibraryEntry?) {
        if (!beginFloppyChange()) return
        toast(if (entry == null) "Ejecting floppy ${'A' + drive}\u2026" else "Preparing ${entry.displayName}\u2026")
        pages.refresh()
        Thread {
            val result = try {
                val disk = entry?.let { romLibrary.prepare(it, AtomicBoolean(false)) { } }
                if (!nativeFloppy(drive, disk?.absolutePath)) error("Floppy could not be mounted")
                null
            } catch (error: Exception) { error.message ?: "Floppy change failed" }
            runOnUiThread {
                endFloppyChange()
                if (result == null) {
                    manualFloppies[drive]?.delete()
                    manualFloppies[drive] = null
                    mountedFloppies[drive] = entry?.displayName
                }
                toast(result ?: "Floppy ${'A' + drive}: ${entry?.displayName ?: "Empty"}")
                pages.refresh()
            }
        }.start()
    }

    private fun chooseFloppyFile(drive: Int) {
        DocumentPicker.open(this, if (drive == 0) FLOPPY_A_REQUEST else FLOPPY_B_REQUEST, failed = ::toast)
    }

    private fun chooseHdi() {
        closeMenu()
        DocumentPicker.open(this, HDI_REQUEST, failed = ::toast)
    }

    // Firmware selection requires no machine session. A session paused behind
    // the library still owns its firmware. Native loading is unchanged.
    private val firmwareConfigurable get() = currentDisk == null && !preparingFont

    /** Machine: the base clock, then firmware, which only changes with no game running. */
    private fun machinePage() = GroupsPage("Machine", { GameSource.ALL_GAMES }, {
        val inGame = !firmwareConfigurable
        val reason = { "Return to the library to change firmware" }
        listOf(
            RowGroup("CLOCK", listOf(SettingsRow.choice("base-clock", "Base clock", ChoiceSpec("Base clock",
                { GameSource.ALL_GAMES }, { listOf(ChoiceGroup(null, listOf(
                    ChoiceOption("25", "2.5 MHz"), ChoiceOption("20", "2 MHz")))) },
                { clock.toString() }, select = { value -> setBaseClock(value.toInt()) }))),
                "Changing the base clock restarts a running game. Games with their own clock keep it."),
            RowGroup("FIRMWARE", listOf(
                firmwareRow("bios", "BIOS ROM", { biosFile().isFile }, { biosBusy }, { "Installed" }, "Not installed",
                    "Remove", ::chooseBiosFile) { removeFirmware(biosFile(), "BIOS ROM") },
                firmwareRow("font", "Font BMP", { gameFontActive || fontBitmapFile().isFile }, { fontBusy },
                    { if (gameFontActive) "Game folder" else "Imported" }, "Built-in",
                    "Use built-in", ::chooseFontFile) { removeFirmware(fontBitmapFile(), "Font BMP") },
                firmwareRow("rhythm", "YM2608 rhythm ROM", { rhythmRomFile().isFile }, { rhythmBusy }, { "Installed" },
                    "Not installed", "Remove", ::chooseRhythmFile) { removeFirmware(rhythmRomFile(), "YM2608 rhythm ROM") })
                .map { row -> if (inGame) SettingsRow.action(row.id, row.label, row.value, { ValueTone.SUMMARY },
                    enabled = { false }, disabledReason = reason) else row },
                if (inGame) "Return to the library to change firmware."
                else "The BIOS ROM must be exactly 96 KiB and the rhythm ROM exactly 8 KiB."))
    })

    /** A firmware file with its state, and a tray to choose, replace, or remove it. */
    private fun firmwareRow(id: String, label: String, installed: () -> Boolean, busy: () -> Boolean,
                            on: () -> String, off: String, removeLabel: String, choose: () -> Unit,
                            remove: () -> Unit) = SettingsRow.action(id, label,
        value = { if (busy()) "Importing\u2026" else if (installed()) on() else off },
        tone = { if (installed()) ValueTone.CURRENT else ValueTone.SUMMARY },
        busy = busy, enabled = { !busy() }, disabledReason = { "Wait for the current import to finish" },
        tray = {
            TraySpec(buttons = if (installed()) listOf(TrayButton(removeLabel) { remove() },
                TrayButton("Replace\u2026", ButtonTone.PRIMARY) { choose() })
                else listOf(TrayButton("Choose file\u2026", ButtonTone.PRIMARY) { choose() }))
        })

    private fun removeFirmware(file: File, label: String) {
        if (!firmwareConfigurable) return
        toast(if (file.delete()) if (file == fontBitmapFile()) "Using the built-in font" else "$label removed."
            else "Could not remove $label")
        firstRunSetup.refreshFirmware()
        pages.refresh()
    }

    private fun setBaseClock(selected: Int) {
        if (clock == selected) return
        clock = selected
        preferences.edit().putInt("base_clock", clock).apply()
        if (currentDisk == null) return
        if (currentGame?.baseClockTenthsMHz == null) {
            nativeClock(clock)
            toast("Clock change resets the machine")
        } else toast("Global clock saved; this game uses its own clock")
    }

    private fun chooseBiosFile() {
        if (!firmwareConfigurable) return
        DocumentPicker.open(this, BIOS_REQUEST, failed = ::toast)
    }

    private fun chooseFontFile() {
        if (!firmwareConfigurable) return
        DocumentPicker.open(this, FONT_REQUEST, failed = ::toast)
    }

    private fun chooseRhythmFile() {
        if (!firmwareConfigurable) return
        DocumentPicker.open(this, RHYTHM_REQUEST, failed = ::toast)
    }

    private fun touchStorage(direct: Boolean) = if (direct) "direct" else "touchpad"

    private fun setSecondaryInitialMode(touchpad: Boolean) {
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setInitialMode(touchpad)
        if (::swappedKeyboardPanel.isInitialized) swappedKeyboardPanel.setInitialMode(touchpad)
    }

    private fun directTapActive(): Boolean = touchSelection().selection.directTouch

    private fun aboutPage() = AboutDocuments.page(this, pages, listOf(
        "Open the menu" to "Mode or Home button, Back, Menu, or a swipe from the left edge",
        "Open the keyboard" to "Swipe in from the right edge",
        "Physical keyboard" to "Types into the PC-98 while the menu is closed"),
        "Android reserves the system Home key.",
        "Kairo98 uses Neko Project 21/W and ymfm. Source and provenance: github.com/MrJackSpade/Kairo98.",
        "PRIVACY_POLICY.txt", "THIRD_PARTY_NOTICES.txt", ::diagnosticsPage)

    /** Live machine values, updated four times a second. */
    private fun diagnosticsPage() = object : GroupsPage("Diagnostics", { currentTitle }, {
        val parts = nativeStatus().split(" | ")
        fun part(index: Int, prefix: String = "") = parts.getOrNull(index)?.removePrefix(prefix)?.trim() ?: ""
        val running = currentDisk != null
        listOf(RowGroup("MACHINE STATUS", if (!running) listOf(SettingsRow.info("state", "State") { "Stopped" })
            else listOf(
                SettingsRow.info("state", "State") { part(0) },
                SettingsRow.info("frames", "Frames") { part(1, "frames") },
                SettingsRow.info("csip", "CS:IP") { part(2, "CS:IP") },
                SettingsRow.info("audio", "Audio") { part(3, "audio") },
                SettingsRow.info("xruns", "Xruns") { part(4, "xruns") },
                SettingsRow.info("audio-config", "Audio config") { part(5) },
                SettingsRow.info("audio-profile", "Audio profile") { part(6) }),
            if (running) "Live values from the emulator, updated four times a second." else "Start a game to see live values."))
    }) {
        override fun tick() = refresh()
    }

    private fun effectiveControllerBindings(game: GameCatalog.Game?): List<ControllerBinding> {
        val configured = game?.controllerBindings
        if (game != null && !game.overriddenFields.contains("controller") &&
            game.controllerDefaultBindings != null) {
            val defaults = org.json.JSONObject(game.controllerDefaultBindings)
            val bindings = defaults.optJSONArray(controllerProfiles.configuration.layout.key)
                ?: defaults.optJSONArray("withoutSticks")
            if (bindings != null) return ControllerBindings.parse(bindings.toString())
        }
        return if (game?.overriddenFields?.contains("controller") == true ||
            (configured != null && (configured != "[]" || game.controllerProfile == "custom-v1")))
            ControllerBindings.parse(configured)
        else controllerFlow.global()
    }

    private fun controllerMappingStatus(entry: LibraryEntry?): String? {
        if (controllerProfiles.configuration.layout != com.mrjackspade.kairo.frontend.ControllerLayout.WITH_STICKS)
            return null
        val game = entry?.contentId?.let { romLibrary.catalog.resolve(it, entry.displayName) }
        if (game == null) return null
        if (game.overriddenFields.contains("controller") || game.controllerProfile == "custom-v1") return null
        return if (game.controllerDefaultBindings != null &&
            org.json.JSONObject(game.controllerDefaultBindings).optJSONArray("withSticks") == null)
            "Using Without Sticks fallback" else null
    }

    private fun releaseInputs(preserveAutomation: Boolean = false) =
        inputDispatch.releaseInputs(preserveAutomation)

    private fun releaseTouchInputs() {
        pendingMouseHold?.let(handler::removeCallbacks)
        pendingMouseHold = null
        pendingMouseRelease?.let(handler::removeCallbacks)
        pendingMouseRelease = null
        pendingMouseWarp?.let(handler::removeCallbacks)
        pendingMouseWarp = null
        mouseRouter.release("touch")
        mouseTouchActive = false
        mouseDragging = false
        mouseMoved = false
    }

    private fun controllerAction(action: String) {
        when (action) {
            "menu" -> if (menuOpen) closeMenu() else openMenu()
            "pause" -> {
                userPaused = !userPaused
                applyPauseState()
                toast(if (userPaused) "Paused" else "Resumed")
            }
            "restart" -> if (!menuOpen) openMenu("f-restart", confirm = true)
            "exit" -> if (!menuOpen) openMenu("f-exit", confirm = true)
            "fastForward" -> nativeSetFastForward(true)
        }
    }

    private fun controllerActionReleased(action: String) {
        if (action == "fastForward") nativeSetFastForward(false)
    }

    private fun stateError(code: Int) = when (code) {
        1 -> "machine not running"
        2 -> "state is missing or from another version"
        3 -> "storage unavailable"
        4 -> "disk image copy failed"
        5 -> "state file unreadable"
        6 -> "machine did not respond"
        else -> "code $code"
    }

    private fun exitApp() {
        commandCancelled.set(true)
        releaseInputs()
        if (exiting) return
        exiting = true
        controllerProfiles.configuration.endSession()
        startGeneration++
        if (currentDisk != null) {
            sessionFlow.close()
            preparingFont = true
            showProgress("Stopping machine\u2026")
        }
        Thread {
            nativeStop()
            runOnUiThread { finish() }
        }.start()
    }

    private fun toast(message: String) =
        Ui.message(this, message)

    private fun showProgress(text: String) {
        progressText = text
        applyPauseState()
    }

    private fun onSecondarySwapChanged(swapped: Boolean) {
        keyboardPanel.close()
        updateViewport()
        if (!swapped) {
            val holder = screen.holder
            if (holder.surface.isValid)
                nativeSetSurface(holder.surface, screen.width, screen.height)
        }
        applyPauseState()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped)
            nativeSetSurface(holder.surface, holder.surfaceFrame.width(), holder.surfaceFrame.height())
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped)
            nativeSetSurface(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.swapped)
            nativeSetSurface(null, 0, 0)
    }

    override fun onPause() {
        if (!relocating && guestLifecycle.onPause()) secondaryKeyboard.stop()
        super.onPause()
    }

    override fun onStop() {
        if (!relocating && guestLifecycle.onStop()) secondaryKeyboard.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (relocating) return
        secondaryKeyboard.start(handler)
        guestLifecycle.onResume()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (relocating || rebuildIfRescaled()) return
        ImmersiveWindow.apply(this)
        graphics.load()
        root.post { updateViewport() }
        gameOrientation.configurationChanged()
    }

    /** Rebuilds views sized for earlier density or font settings once no machine is running or starting. */
    private fun rebuildIfRescaled(): Boolean {
        if (exiting || isFinishing || currentDisk != null || preparingFont || !viewScale.changed) return false
        recreate()
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (relocating) return
        guestLifecycle.onWindowFocusChanged(hasFocus)
    }

    override fun onDestroy() {
        if (relocating) { super.onDestroy(); return }
        catalogUpdates.cancel()
        romLibrary.catalog.installedCatalogs.close()
        backCoordinator.unregister()
        startGeneration++
        libraryFlow.cancel()
        externalDispatcher.cancel()
        if (::libraryFlow.isInitialized) artworkDownloads.cancel()
        releaseInputs()
        secondaryKeyboard.stop()
        controllerDevices.unregister()
        handler.removeCallbacks(updateStatus)
        handler.removeCallbacks(traceHashes)
        nativeSetSurface(null, 0, 0)
        if (!exiting) Thread { nativeStop() }.start()
        super.onDestroy()
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (shaderOptions.handleActivityResult(requestCode, resultCode, data)) return
        if (libraryMenu.handleActivityResult(requestCode, resultCode, data)) return
        if (artwork.handleActivityResult(requestCode, resultCode, data)) return
        if (libraryFlow.handleActivityResult(requestCode, resultCode, data)) return
        if (saveFolder.handleActivityResult(requestCode, resultCode, data)) return
        if (requestCode == BIOS_REQUEST) {
            if (resultCode == RESULT_OK && data?.data != null) importBiosRom(data.data!!)
            return
        }
        if (requestCode == FONT_REQUEST) {
            if (resultCode == RESULT_OK && data?.data != null) importFontBitmap(data.data!!)
            return
        }
        if (requestCode == RHYTHM_REQUEST) {
            if (resultCode == RESULT_OK && data?.data != null) importRhythmRom(data.data!!)
            return
        }
        if (requestCode !in listOf(HDI_REQUEST, FLOPPY_A_REQUEST, FLOPPY_B_REQUEST) ||
            resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data ?: return
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (error: Exception) {
            toast(error.message ?: "Could not read selected file")
            return
        }
        val isSwap = requestCode != HDI_REQUEST
        if (name == null || !DiskFormat.supported(name) || (isSwap && !DiskFormat.isFloppy(name))) {
            toast(if (isSwap) "Select a supported floppy image" else "Select a supported disk image")
            return
        }
        if (isSwap) {
            importFloppyFile(if (requestCode == FLOPPY_A_REQUEST) 0 else 1, uri, name)
            return
        }
        val generation = ++startGeneration
        sessionFlow.close()
        preparingFont = true
        showProgress("Preparing $name\u2026")
        gameOrientation.launching()
        Thread {
            val disk = File(filesDir, "boot${DiskFormat.suffix(name)}")
            val partial = File(filesDir, "${disk.name}.part")
            val result = try {
                nativeStop()
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected image" }
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= DiskFormat.maxBytes(name)) { "Disk image exceeds size limit" }
                            output.write(buffer, 0, count)
                        }
                        require(size > 0) { "Empty disk image" }
                    }
                }
                Files.move(partial.toPath(), disk.toPath(), StandardCopyOption.REPLACE_EXISTING)
                val font = prepareFont()
                if (generation != startGeneration) "Start cancelled"
                else if (nativeStart(disk.absolutePath, font, firmwareDir().absolutePath, clock,
                        50, DEFAULT_CPU_MULTIPLE, DiskFormat.isFloppy(name), null, null))
                    awaitMachineReady()?.let { "Disk start failed: $it" } ?: "Starting $name"
                else "Unable to start machine"
            } catch (error: Exception) {
                "Disk import failed: ${error.message}"
            } finally {
                partial.delete()
            }
            runOnUiThread {
                if (generation != startGeneration) return@runOnUiThread
                preparingFont = false
                if (result.startsWith("Starting ")) {
                    controllerProfiles.configuration.endSession()
                    controllerProfiles.configuration.beginSession()
                    currentEntry = null
                    currentDisk = disk
                    gameFontActive = false
                    currentIsFloppy = DiskFormat.isFloppy(name)
                    mountedFloppies[0] = if (currentIsFloppy) name else null
                    mountedFloppies[1] = null
                    currentTitle = name
                    currentGame = null
                    setSecondaryInitialMode(false)
                    inputModeDecider.reset()
                    gamepadMapper.bindings = controllerFlow.global()
                    sessionNavigation.enterGame(notify = false)
                    gameOrientation.launched()
                } else gameOrientation.launchFailed()
                applyPauseState()
                toast(result)
                rebuildIfRescaled()
            }
        }.start()
    }

    private fun importFloppyFile(drive: Int, uri: Uri, name: String) {
        if (!beginFloppyChange()) return
        toast("Importing $name\u2026")
        pages.refresh()
        Thread {
            val disk = File(cacheDir, "manual-floppy-$drive-${System.nanoTime()}${DiskFormat.suffix(name)}")
            val result = try {
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected floppy" }
                    disk.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= 64L * 1024 * 1024) { "Floppy exceeds size limit" }
                            output.write(buffer, 0, count)
                        }
                        require(size > 0) { "Empty floppy image" }
                    }
                }
                if (!nativeFloppy(drive, disk.absolutePath)) error("Floppy could not be mounted")
                null
            } catch (error: Exception) { error.message ?: "Floppy import failed" }
            runOnUiThread {
                endFloppyChange()
                if (result == null) {
                    manualFloppies[drive]?.delete()
                    manualFloppies[drive] = disk
                    mountedFloppies[drive] = name
                } else disk.delete()
                toast(result ?: "Floppy ${'A' + drive}: $name")
                pages.refresh()
            }
        }.start()
    }

    private fun importBiosRom(uri: Uri) {
        if (!firmwareConfigurable || biosBusy) return
        biosBusy = true
        val restartNeeded = currentDisk != null
        firstRunSetup.refreshFirmware()
        pages.refresh()
        Thread {
            val directory = firmwareDir()
            val partial = File(directory, "bios.rom.part")
            val result = try {
                require(directory.isDirectory || directory.mkdirs()) { "Cannot create firmware directory" }
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected file" }
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var size = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= BIOS_ROM_BYTES) { "BIOS ROM must be exactly 96 KiB" }
                            output.write(buffer, 0, count)
                        }
                        require(size == BIOS_ROM_BYTES) { "BIOS ROM must be exactly 96 KiB" }
                    }
                }
                Files.move(partial.toPath(), biosFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
                if (restartNeeded) "BIOS ROM imported. Restart the game to apply."
                else "BIOS ROM imported."
            } catch (error: Exception) {
                "BIOS import failed: ${error.message ?: "Unknown error"}"
            } finally { partial.delete() }
            runOnUiThread {
                biosBusy = false
                firstRunSetup.refreshFirmware()
                pages.refresh()
                toast(result)
            }
        }.start()
    }

    private fun importFontBitmap(uri: Uri) {
        if (!firmwareConfigurable || fontBusy) return
        fontBusy = true
        val restartNeeded = currentDisk != null
        firstRunSetup.refreshFirmware()
        pages.refresh()
        Thread {
            val directory = firmwareDir()
            val partial = File(directory, "font.bmp.part")
            val result = try {
                require(directory.isDirectory || directory.mkdirs()) { "Cannot create firmware directory" }
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected file" }
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var size = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= Pc98FontBitmap.MAX_BYTES) { "Font BMP is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                Pc98FontBitmap.validate(partial)
                Files.move(partial.toPath(), fontBitmapFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
                if (restartNeeded) "Font BMP imported. Restart the game to apply."
                else "Font BMP imported."
            } catch (error: Exception) {
                "Font BMP import failed: ${error.message ?: "Unknown error"}"
            } finally { partial.delete() }
            runOnUiThread {
                fontBusy = false
                firstRunSetup.refreshFirmware()
                pages.refresh()
                toast(result)
            }
        }.start()
    }

    private fun importRhythmRom(uri: Uri) {
        if (!firmwareConfigurable || rhythmBusy) return
        rhythmBusy = true
        val restartNeeded = currentDisk != null
        firstRunSetup.refreshFirmware()
        pages.refresh()
        Thread {
            val directory = firmwareDir()
            val partial = File(directory, "ym2608_adpcm_rom.bin.part")
            val result = try {
                require(directory.isDirectory || directory.mkdirs()) { "Cannot create firmware directory" }
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected file" }
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var size = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            require(size <= RHYTHM_ROM_BYTES) { "YM2608 rhythm ROM must be exactly 8 KiB" }
                            output.write(buffer, 0, count)
                        }
                        require(size == RHYTHM_ROM_BYTES) { "YM2608 rhythm ROM must be exactly 8 KiB" }
                    }
                }
                Files.move(partial.toPath(), rhythmRomFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
                if (restartNeeded) "YM2608 rhythm ROM imported. Restart the game to apply."
                else "YM2608 rhythm ROM imported."
            } catch (error: Exception) {
                "YM2608 rhythm ROM import failed: ${error.message ?: "Unknown error"}"
            } finally { partial.delete() }
            runOnUiThread {
                rhythmBusy = false
                firstRunSetup.refreshFirmware()
                pages.refresh()
                toast(result)
            }
        }.start()
    }

    private fun startWithFont(disk: File, message: String, floppy: Boolean) {
        if (preparingFont) return
        val generation = ++startGeneration
        preparingFont = true
        showProgress("Preparing PC-98 font")
        Thread {
            val result = try {
                val font = prepareFont()
                nativeStop()
                if (generation != startGeneration) "Start cancelled"
                else if (nativeStart(disk.absolutePath, font, firmwareDir().absolutePath,
                        clock, 50, DEFAULT_CPU_MULTIPLE, floppy, null, null))
                    awaitMachineReady()?.let { "Disk start failed: $it" } ?: message
                else "Unable to start machine"
            } catch (error: Exception) {
                "PC-98 font preparation failed: ${error.message}"
            }
            runOnUiThread {
                preparingFont = false
                applyPauseState()
                toast(result)
            }
        }.start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        inputDispatch.dispatchKey(event) { super.dispatchKeyEvent(it) }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        inputDispatch.dispatchMotion(event) { super.dispatchGenericMotionEvent(it) }

    private fun dispatchGuestTouch(event: MotionEvent) = super.dispatchTouchEvent(event)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Edge gestures work over the game and its flyout, not over pages above the flyout.
        if (!::firstRunSetup.isInitialized || !::sessionFlow.isInitialized || !::keyboardPanel.isInitialized ||
            libraryVisible || (pages.isActive && pages.top?.isFlyout != true))
            return super.dispatchTouchEvent(event)
        return when (edgeSwipes.handle(event, root.width, menuOpen,
            canOpenMenu = true,
            canOpenKeyboard = keyboardPanel.visibility != View.VISIBLE &&
                (!::secondaryKeyboard.isInitialized || !secondaryKeyboard.isKeyboardVisible),
            controlsHit = onScreenControls.hitTest(event.x, event.y))) {
            EdgeSwipeNavigation.Result.PASS -> super.dispatchTouchEvent(event)
            EdgeSwipeNavigation.Result.CONSUME -> true
            EdgeSwipeNavigation.Result.REPLAY_GUEST ->
                edgeSwipes.replay(event, ::dispatchGuestTouch)
            EdgeSwipeNavigation.Result.OPEN_MENU -> { openMenu(); true }
            EdgeSwipeNavigation.Result.OPEN_KEYBOARD -> { showKeyboard(); true }
            EdgeSwipeNavigation.Result.CLOSE_MENU -> {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                closeMenu()
                true
            }
        }
    }

    @Deprecated("Legacy Back path; API 33+ also uses OnBackInvokedDispatcher")
    override fun onBackPressed() = handleBack()

    private fun handleBack() = backCoordinator.handle()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        inputDispatch.physicalKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        inputDispatch.physicalKey(event) || super.onKeyUp(keyCode, event)

    private fun pc98ScanCode(key: Int): Int? {
        if (key in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) return key - KeyEvent.KEYCODE_1 + 1
        if (key == KeyEvent.KEYCODE_0) return 0x0a
        if (key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val letters = intArrayOf(0x1d, 0x2d, 0x2b, 0x1f, 0x12, 0x20, 0x21, 0x22,
                0x17, 0x23, 0x24, 0x25, 0x2f, 0x2e, 0x18, 0x19, 0x10, 0x13,
                0x1e, 0x14, 0x16, 0x2c, 0x11, 0x2a, 0x15, 0x29)
            return letters[key - KeyEvent.KEYCODE_A]
        }
        if (key in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F10) return 0x62 + key - KeyEvent.KEYCODE_F1
        return when (key) {
            KeyEvent.KEYCODE_ESCAPE -> 0x00
            KeyEvent.KEYCODE_DEL -> 0x0e
            KeyEvent.KEYCODE_TAB -> 0x0f
            KeyEvent.KEYCODE_ENTER -> 0x1c
            KeyEvent.KEYCODE_SPACE -> 0x34
            KeyEvent.KEYCODE_DPAD_UP -> 0x3a
            KeyEvent.KEYCODE_DPAD_LEFT -> 0x3b
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0x3c
            KeyEvent.KEYCODE_DPAD_DOWN -> 0x3d
            KeyEvent.KEYCODE_INSERT -> 0x38
            KeyEvent.KEYCODE_FORWARD_DEL -> 0x39
            KeyEvent.KEYCODE_PAGE_UP -> 0x36
            KeyEvent.KEYCODE_PAGE_DOWN -> 0x37
            KeyEvent.KEYCODE_MOVE_HOME -> 0x3e
            KeyEvent.KEYCODE_MOVE_END -> 0x3f
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 0x73
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0x70
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0x74
            else -> null
        }
    }

    /** The imported FONT.BMP, or the bundled Kairo98 font when none is imported. */
    private fun prepareFont(): String {
        val bitmap = fontBitmapFile()
        if (bitmap.isFile) return bitmap.absolutePath
        val bundled = File(filesDir, BUNDLED_FONT)
        val data = assets.open("font/$BUNDLED_FONT").use { it.readBytes() }
        if (!bundled.isFile || bundled.length() != data.size.toLong() || !bundled.readBytes().contentEquals(data)) {
            val partial = File(filesDir, "$BUNDLED_FONT.part")
            try {
                partial.writeBytes(data)
                Files.move(partial.toPath(), bundled.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                partial.delete()
            }
        }
        File(filesDir, "android-font.bin").delete()
        return bundled.absolutePath
    }

    private fun firmwareDir() = File(filesDir, "firmware")
    private fun biosFile() = File(firmwareDir(), "bios.rom")
    private fun fontBitmapFile() = File(firmwareDir(), "font.bmp")
    private fun rhythmRomFile() = File(firmwareDir(), "ym2608_adpcm_rom.bin")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        /** The core's standard CPU clock multiple, about 49 MHz on the 2.5 MHz base clock. */
        const val DEFAULT_CPU_MULTIPLE = 20
        /** Bundled FONT.BMP built by tools/generate_font_bmp.py from redistributable fonts. */
        private const val BUNDLED_FONT = "kairo98-font.bmp"
        private const val HDI_REQUEST = 98
        private const val ROM_FOLDER_REQUEST = 99
        private const val FLOPPY_A_REQUEST = 100
        private const val FLOPPY_B_REQUEST = 101
        private const val BIOS_REQUEST = 102
        private const val FONT_REQUEST = 103
        private const val RHYTHM_REQUEST = 104
        private const val BIOS_ROM_BYTES = 0x18000L
        private const val RHYTHM_ROM_BYTES = 0x2000L
        init { System.loadLibrary("kairo98") }
    }
}
