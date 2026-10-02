package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.SecondaryDisplayCoordinator
import com.mrjackspade.kairo.frontend.EdgeSwipeNavigation
import com.mrjackspade.kairo.frontend.RgDsDisplayRouter
import com.mrjackspade.kairo.frontend.GuestKeyboardPanel
import com.mrjackspade.kairo.frontend.StateSlotCoordinator
import com.mrjackspade.kairo.frontend.StateSlotStore
import com.mrjackspade.kairo.frontend.GraphicsOptions
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
import com.mrjackspade.kairo.frontend.ControllerEditor
import com.mrjackspade.kairo.frontend.ControllerGuestSpec
import com.mrjackspade.kairo.frontend.CatalogArtworkDownloadController
import com.mrjackspade.kairo.frontend.CatalogUpdateController
import com.mrjackspade.kairo.frontend.ControllerProfileStore
import com.mrjackspade.kairo.frontend.ControllerProfileCoordinator
import com.mrjackspade.kairo.frontend.ControllerEditorFlow
import com.mrjackspade.kairo.frontend.ControllerBinding
import com.mrjackspade.kairo.frontend.SettingsEntry
import com.mrjackspade.kairo.frontend.TouchInputPolicy
import com.mrjackspade.kairo.frontend.TouchInputSelection
import com.mrjackspade.kairo.frontend.TouchSettingsCoordinator
import com.mrjackspade.kairo.frontend.TouchSettingsStore
import com.mrjackspade.kairo.frontend.TouchUiCoordinator
import com.mrjackspade.kairo.frontend.DocumentPicker
import com.mrjackspade.kairo.frontend.ArtworkCoordinator
import com.mrjackspade.kairo.frontend.ArtworkOverridePath

import com.mrjackspade.kairo.frontend.MouseInputRouter
import com.mrjackspade.kairo.frontend.PixelTextView
import com.mrjackspade.kairo.frontend.Ui
import com.mrjackspade.kairo.frontend.LibraryScreen
import com.mrjackspade.kairo.frontend.LibraryFlow
import com.mrjackspade.kairo.frontend.ExternalGameIntent
import com.mrjackspade.kairo.frontend.ExternalGameDispatcher
import com.mrjackspade.kairo.frontend.FrontendNavigation
import com.mrjackspade.kairo.frontend.SessionFlow
import com.mrjackspade.kairo.frontend.SessionNavigationCoordinator
import com.mrjackspade.kairo.frontend.SessionNavigationState
import com.mrjackspade.kairo.frontend.FrontendBackCoordinator
import com.mrjackspade.kairo.frontend.LibraryStrings
import com.mrjackspade.kairo.frontend.SessionAction
import com.mrjackspade.kairo.frontend.SessionDrawer
import com.mrjackspade.kairo.frontend.GameSettingsRow
import com.mrjackspade.kairo.frontend.GameSettingsCoordinator
import com.mrjackspade.kairo.frontend.CommonGameSettings
import com.mrjackspade.kairo.frontend.CommonGameSettingsActions
import com.mrjackspade.kairo.frontend.GameSettingsValue
import com.mrjackspade.kairo.frontend.GameTitleEditor
import com.mrjackspade.kairo.frontend.fileLabel
import com.mrjackspade.kairo.frontend.variantLabel

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
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
import android.widget.EditText
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeSetFastForward(enabled: Boolean)

    private val inputRouter = InputRouter(::nativeKey)
    private val joystickRouter = JoystickInputRouter(::nativeJoystick)
    private val mouseRouter = MouseInputRouter(::nativeMouseMove, ::nativeMouseButton)
    private val gamepadMapper = GamepadMapper(inputRouter, joystickRouter, mouseRouter,
        ::controllerAction, ::controllerActionReleased, ControllerBindings.defaults())
    private val inputDispatch: InputDispatchCoordinator by lazy {
        InputDispatchCoordinator(FrontendInputScreens(
            { if (::firstRunSetup.isInitialized) firstRunSetup else null },
            { if (::onScreenControls.isInitialized) onScreenControls else null },
            { if (::controllerEditor.isInitialized) controllerEditor else null },
            { if (::libraryFlow.isInitialized) libraryScreen else null },
            { if (::sessionDrawer.isInitialized) sessionDrawer else null },
            { !libraryVisible }, ::handleBack, ::closeMenu),
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
    private val graphics by lazy {
        GraphicsOptions(this, preferences, { builder -> builder.showStyled() },
            { updateViewport(); refreshSettingValues() }, ::toast)
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
        TouchSettingsCoordinator(this, object : TouchSettingsStore<LibraryEntry> {
            override fun global() = TouchInputSelection(globalInputMode, globalTouchDirect)
            override fun game(game: LibraryEntry) = touchSelection(
                romLibrary.catalog.resolve(game.contentId!!, game.displayName)).selection
            override fun secondaryTouchpad(game: LibraryEntry): Boolean? =
                romLibrary.catalog.resolve(game.contentId!!, game.displayName)
                    .inputSecondary == "touchpad"
            override fun saveGlobal(value: TouchInputSelection) {
                globalInputMode = value.mode
                globalTouchDirect = value.directTouch
                preferences.edit()
                    .putString("input_mode", InputModeDecider.storageValue(value.mode))
                    .putString("touch_mouse", touchStorage(value.directTouch)).apply()
            }
            override fun saveGame(game: LibraryEntry, value: TouchInputSelection,
                                  secondaryTouchpad: Boolean?) {
                val resolved = romLibrary.catalog.resolve(game.contentId!!, game.displayName)
                val chosenMode = InputModeDecider.storageValue(value.mode)
                val chosenTouch = touchStorage(value.directTouch)
                val chosenSecondary = if (secondaryTouchpad == true) "touchpad" else "keyboard"
                val changes = linkedMapOf<String, Any?>()
                if (chosenMode != (resolved.inputMode ?:
                        InputModeDecider.storageValue(globalInputMode)))
                    changes["mode"] = chosenMode
                if (chosenTouch != (resolved.inputTouch ?: touchStorage(globalTouchDirect)))
                    changes["touch"] = chosenTouch
                if (chosenSecondary != (resolved.inputSecondary ?: "keyboard"))
                    changes["secondary"] = chosenSecondary
                if (changes.isNotEmpty()) romLibrary.catalog.updateOverrideSubfields(
                    game.contentId!!, "input", changes)
            }
            override fun resetGame(game: LibraryEntry) {
                romLibrary.catalog.resetOverride(game.contentId!!, "input")
            }
        }, LibraryEntry::contentId, InputModeDecider.Mode.entries,
            listOf("Auto (follows what the game reads)",
                "Keyboard (tap opens the PC-98 keyboard)", "Mouse"),
            "Touch input", { entry ->
                "Touch input for ${romLibrary.catalog.resolve(entry.contentId!!, entry.displayName).title}"
            }, "Direct tap lands on the touched point in games that move the " +
                "cursor one pixel per mouse count and stop it at the screen's top-left edge.",
            noHash = { toast("This file needs a successful hash before settings can be saved") },
            changed = { entry ->
                if (entry != null && currentEntry?.contentId == entry.contentId) {
                    currentGame = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
                    setSecondaryInitialMode(currentGame?.inputSecondary == "touchpad")
                }
            })
    }
    private lateinit var firstRunSetup: FirstRunSetup
    private lateinit var romLibrary: RomLibrary
    private val artwork by lazy {
        ArtworkCoordinator(this, romLibrary.catalog.artworkStore,
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
                ArtworkCoordinator.Record(if (kind == "preview") game.preview else game.boxArt, url)
            }, { ArtworkOverridePath.valid(it, "art/") },
            { id, kind, path ->
                if (path == null) romLibrary.catalog.resetArtworkOverride(id, kind)
                else romLibrary.catalog.setArtworkOverride(id, kind, path)
            }, { libraryScreen.showEntries(libraryEntries) },
            ::showGameDetails, ::toast, GameCatalog::validImageUrl)
    }
    private lateinit var controllerEditor: ControllerEditor<LibraryEntry>
    private val controllerEditorFlow by lazy {
        ControllerEditorFlow(controllerEditor, LibraryEntry::contentId, ::closeMenu,
            ::releaseInputs, ::hideKeyboard, { onReturn -> onScreenControls.show(onReturn) }, ::toast)
    }
    private val sessionState = SessionNavigationState()
    private val libraryVisible: Boolean get() = sessionState.libraryVisible
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
                libraryScreen.showFolder(romTree?.let(::folderLabel))
                libraryScreen.showStatus("${libraryEntries.count { it.playable }} games ready")
            }, ::applyPauseState, { screen.requestFocus() }, ::endSessionForLibrary)
    }
    private val backCoordinator: FrontendBackCoordinator by lazy {
        FrontendBackCoordinator(this, firstRunSetup,
            { if (::onScreenControls.isInitialized) onScreenControls else null },
            controllerEditor, libraryScreen,
            { if (!sessionNavigation.resumeGame()) finish() },
            { if (::sessionFlow.isInitialized) sessionFlow else null }, ::closeMenu,
            { if (::keyboardPanel.isInitialized) keyboardPanel else null }, ::openMenu, ::hideKeyboard)
    }
    private var romTree: Uri?
        get() = libraryFlow.tree
        set(value) { libraryFlow.tree = value }
    private val artworkDownloads by lazy {
        CatalogArtworkDownloadController(this, libraryScreen, { libraryEntries },
            romLibrary.catalog::missingArtworkFor, romLibrary.catalog::downloadArtwork)
    }
    private val catalogUpdates by lazy {
        CatalogUpdateController(this, { task -> romLibrary.catalog.downloadUpdate(task) },
            libraryScreen::showStatus, { libraryScreen.showEntries(libraryEntries) },
            { if (libraryVisible) toast("Game catalog updated") }, libraryScreen::showCatalogUpdate)
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
                                      val swapSources: Map<String, LibraryEntry>)
    @Volatile private var floppyBusy = false
    private val samplingLock = Any()
    private var samplingUsers = 0
    private val guestSequenceLock = Any()
    private lateinit var swapStatus: TextView
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
    private lateinit var backdrop: View
    private lateinit var drawer: ScrollView
    private lateinit var menuStatus: TextView
    private lateinit var mediaLabel: TextView
    private lateinit var sessionDrawer: SessionDrawer
    private lateinit var sessionFlow: SessionFlow
    private val menuOpen: Boolean get() = sessionFlow.isOpen
    private var userPaused: Boolean
        get() = sessionState.userPaused
        set(value) { sessionState.userPaused = value }
    private val activityVisible: Boolean get() = guestLifecycle.isVisible
    private var muted = false
    private var clock = 25
    private var exiting = false
    private var relocating = false
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
        StateSlotCoordinator(this, handler,
            { currentEntry?.contentId?.takeIf { !libraryVisible } }, { currentTitle },
            { StateSlotStore(File(filesDir, "states"), it, "state.np2") },
            { (if (::secondaryKeyboard.isInitialized) secondaryKeyboard.activeGameSurface else null)
                ?: screen },
            { _, scratch -> nativeSaveState(scratch.absolutePath) },
            { nativeLoadState(it.directory.absolutePath) }, ::stateError,
            { menuStatus.text = it }, ::toast,
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
            }, "Save states are available for games started from the library.", R.drawable.ic_save)
    }
    @Volatile private var startGeneration = 0

    private val updateStatus = object : Runnable {
        override fun run() {
            if (menuOpen && !preparingFont && !floppyBusy && !stateBusy) menuStatus.text = menuStatusText()
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
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.decorView.setBackgroundColor(Color.BLACK)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or
                WindowInsets.Type.navigationBars())
        }
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
        controllerEditor = ControllerEditor(this, root,
            controllerFlow::load, controllerFlow::save, controllerFlow::reset,
            controllerFlow::physical, controllerFlow::savePhysical,
            controllerFlow::resetPhysical,
            { controllerFlow.deadZone }, { controllerFlow.deadZone = it },
            ::applyPauseState, ::showOnScreenControls,
            { onScreenControls.eightWayDpad }, { onScreenControls.eightWayDpad = it },
            LibraryEntry::id, ControllerGuestSpec("PC-98", (0..127).toList(),
                Pc98KeyNames::label, setOf(0x70, 0x71, 0x72, 0x73, 0x74, 0x7d),
                ControllerBindings.JOYSTICK.zip(listOf("Up", "Down", "Left", "Right",
                    "Button 1", "Button 2")),
                "Uses the joystick input on the emulated sound board. Games must support joystick 1.",
                listOf("menu" to "Open menu", "pause" to "Pause or resume",
                    "fastForward" to "Fast forward while held", "restart" to "Restart",
                    "exit" to "Exit")),
            { ControllerBindings.toJson(it) }, controllerProfiles.configuration,
            { releaseInputs(); controllerFlow.refresh(null) }, { entry -> controllerMappingStatus(entry) })
        val libraryPage = LibraryScreen(this, romLibrary.catalog, LibraryStrings("KAIRO98"),
            ::chooseRomFolder, { refreshLibrary(false) }, { refreshLibrary(true) },
            { updateGameCatalog(false) },
            if (resources.getBoolean(R.bool.catalog_art_download_enabled))
                ::downloadMissingImages else null, ::cancelArtworkDownload,
            settingsEntries(), { preferences.getString("last_played_entry", null) },
            { launchEntry(it) }, ::showDetailPreview, ::showGameDetails, ::showLibrarySelection)
        libraryFlow = LibraryFlow(this, preferences, libraryPage, ROM_FOLDER_REQUEST,
            romLibrary::cached, romLibrary::scan, ::folderLabel,
            "Choose a ROM folder to find disk images and ZIP games",
            "Folder access expired. Select the ROM folder again.",
            { entries ->
                val errors = entries.count { it.error != null }
                "${entries.count { it.playable }} games" +
                    (if (errors == 0) "" else " · $errors unreadable") +
                    " · ${romLibrary.hashCount} hashes this scan"
            },
            { _ ->
                selectPendingDebugGame()
                pendingDebugLaunch?.let { query ->
                    android.util.Log.w("Kairo98", "ADB game not found or ambiguous: $query")
                    pendingDebugLaunch = null
                }
            },
            { _ ->
                if (firstRunSetup.isChoosingRomFolder) advanceFirstRunFirmware()
                else refreshLibrary(false)
            },
            { message -> if (firstRunSetup.isOpen) toast(message) })
        root.addView(libraryScreen, FrameLayout.LayoutParams(-1, -1))
        touchUi.bind(keyboardPanel)
        swapStatus = TextView(this).apply {
            visibility = View.GONE
            setTextColor(Ui.TEXT)
            setBackgroundColor(0xe0202a36.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            textSize = Ui.BODY
        }
        root.addView(swapStatus, FrameLayout.LayoutParams(-2, -2,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(20) })
        firstRunSetup = FirstRunSetup(this, ::chooseRomFolder, ::advanceFirstRunFirmware,
            ::chooseBiosFile, ::chooseFontFile, ::chooseRhythmFile, ::finishFirstRun,
            { biosFile().isFile }, { fontBitmapFile().isFile }, { rhythmRomFile().isFile })
        root.addView(firstRunSetup, FrameLayout.LayoutParams(-1, -1))
        backCoordinator.register()
        handler.post(updateStatus)
        controllerSetupPending = controllerProfiles.configuration.needsSetup
        root.post {
            if (isFinishing || isDestroyed) return@post
            if (controllerSetupPending) {
                controllerSetupPending = true
                controllerProfiles.configuration.show(this, required = true, product = "KAIRO98") {
                    controllerSetupPending = false
                    controllerFlow.initialize()
                    continueStartup(savedInstanceState)
                }
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
            FirstRunSetup.Step.ROM_FOLDER else FirstRunSetup.Step.FIRMWARE)
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
        AlertDialog.Builder(this).setTitle("Choose boot disk")
            .setItems(playable.map { it.displayName }.toTypedArray()) { _, which ->
                launchEntry(playable[which], true)
            }.setNegativeButton("Cancel", null).showStyled()
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
            !libraryVisible && !menuOpen && !onScreenControls.isOpen && !controllerEditor.isOpen &&
                touchUi.handleKeyboardTouch(event,
                    inputModeDecider.resolve(configuredInputMode()) == InputModeDecider.Mode.KEYBOARD)
        }
        root.addView(screen, FrameLayout.LayoutParams(640, 400, Gravity.CENTER))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
        onScreenControls = OnScreenControls(this, root, gamepadMapper, preferences, ::applyPauseState)

        keyboardPanel = GuestKeyboardPanel(this, inputRouter, Pc98KeyboardLayout.value, ::hideKeyboard)
        root.addView(keyboardPanel, FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM))
        onScreenControls.bindGuestKeyboard(keyboardPanel)
        swappedKeyboardPanel = GuestKeyboardPanel(this, inputRouter, Pc98KeyboardLayout.value,
            {}, showClose = false,
            onSwap = { secondaryKeyboard.toggleSwap() }, mouse = mouseRouter)
        root.addView(swappedKeyboardPanel, FrameLayout.LayoutParams(-1, -1))

        sessionDrawer = SessionDrawer(this, root, "KAIRO98", ::closeMenu, {
            showMachineDetails = !showMachineDetails
            menuStatus.text = menuStatusText()
        }, listOf(
            SessionAction("Resume", R.drawable.ic_play) {
                userPaused = false
                closeMenu()
            },
            SessionAction("Save", R.drawable.ic_save) { showStateSlots(saving = true) },
            SessionAction("Load", R.drawable.ic_load) { showStateSlots(saving = false) },
            SessionAction("Restart", R.drawable.ic_restart) { confirmRestart() },
            SessionAction("Library", R.drawable.ic_library) { sessionNavigation.requestLibrary() }
        ), listOf(
            SettingsEntry("Pause", { if (userPaused) "On · tap to let the game run again"
                else "Close the menu with the game stopped" }) {
                userPaused = !userPaused
                closeMenu()
            },
            SettingsEntry("Mount", { "Hard disk and floppy images" }) { showMountMenu() },
            SettingsEntry("Exit", { "Stop the machine and close Kairo98" }) { confirmExit() }
        ), settingsEntries())
        backdrop = sessionDrawer.backdrop
        drawer = sessionDrawer.drawer
        mediaLabel = sessionDrawer.mediaLabel
        menuStatus = sessionDrawer.status
        sessionFlow = SessionFlow(sessionDrawer,
            { currentTitle ?: "No disk selected" },
            { if (preparingFont) "Preparing PC-98 font" else menuStatusText() },
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

    private fun menuHighlight() = Ui.rowBackground(this)

    /** One settings list, shown the same way in the game menu and the library menu. */
    private fun settingsEntries() = listOf(
        SettingsEntry("Graphics", graphics::settingsLabel, graphics::show),
        SettingsEntry("Touch input", ::touchInputLabel) { showInputMode() },
        SettingsEntry("Machine", { "${if (clock == 25) "2.5" else "2"} MHz · BIOS ${if (biosFile().isFile) "imported" else "none"}" }) { showMachine() },
        SettingsEntry("Controller", { "Gamepad and on-screen controls" }) { showControllerScope() },
        SettingsEntry.sound({ muted }, ::toggleMute),
        SettingsEntry("About", { "Version, shortcuts, and licenses" }) { showAbout() })

    private var showMachineDetails = false

    /** Plain session status for the menu; tapping it shows the machine's raw telemetry. */
    private fun menuStatusText(): String {
        val raw = nativeStatus()
        if (showMachineDetails) return raw
        val state = raw.substringBefore('|').trim().ifEmpty { "Stopped" }
        val media = when {
            currentDisk == null -> null
            currentIsFloppy -> "Floppy disk"
            else -> "Hard disk"
        }
        return listOfNotNull(state, media, if (muted) "Muted" else null).joinToString("  ·  ")
    }

    private fun refreshSettingValues() {
        sessionDrawer.refreshValues()
        if (::libraryFlow.isInitialized) libraryScreen.refreshSettingValues()
    }

    private fun toggleMute() {
        muted = !muted
        preferences.edit().putBoolean("muted", muted).apply()
        nativeSetMuted(muted)
        refreshSettingValues()
    }

    private fun focusMenuItem(index: Int) = sessionDrawer.focus(index)

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

    private fun openMenu() = sessionFlow.open()

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
        val editingControls = ::onScreenControls.isInitialized && onScreenControls.isOpen
        val presentation = sessionState.presentation(activityVisible, menuOpen,
            editingControls || (::controllerEditor.isInitialized && controllerEditor.isOpen), preparingFont)
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
        if (::onScreenControls.isInitialized) {
            if (::libraryFlow.isInitialized) touchUi.refreshControls(onScreenControls,
                showingGuest, ::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped)
            else onScreenControls.refreshVisibility(false)
        }
    }

    private fun hasRomGrant(uri: Uri) = libraryFlow.hasGrant(uri)

    private fun folderLabel(uri: Uri): String = try {
        "ROM folder · " + DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/')
    } catch (_: Exception) { "ROM folder selected" }

    private fun chooseRomFolder() = libraryFlow.chooseFolder()

    private fun advanceFirstRunFirmware() {
        preferences.edit().putInt("onboarding_step_v1", 1).apply()
        firstRunSetup.show(FirstRunSetup.Step.FIRMWARE)
    }

    private fun finishFirstRun() {
        preferences.edit().putInt("onboarding_step_v1", 2).apply()
        firstRunSetup.close()
        applyPauseState()
        val tree = romTree
        if (tree != null && hasRomGrant(tree)) refreshLibrary(false)
        else libraryScreen.showStatus("Choose a ROM folder from the library menu when you're ready")
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

    private fun chooseStartupOptions(entry: LibraryEntry, game: GameCatalog.Game, index: Int,
                                     selected: List<Pair<GameCatalog.StartupChoice,
                                         GameCatalog.StartupOption>>,
                                     fromFrontend: Boolean) {
        if (index == game.startupChoices.size) {
            startEntry(entry, game, selected, fromFrontend)
            return
        }
        val choice = game.startupChoices[index]
        AlertDialog.Builder(this).setTitle(choice.title)
            .setItems(choice.options.map { it.label }.toTypedArray()) { _, which ->
                chooseStartupOptions(entry, game, index + 1,
                    selected + (choice to choice.options[which]), fromFrontend)
            }
            .setNeutralButton("Play manually") { _, _ ->
                startEntry(entry, game, selected, fromFrontend)
            }
            .setNegativeButton("Cancel", null).showStyled()
    }

    private fun startEntry(entry: LibraryEntry, game: GameCatalog.Game,
                           choices: List<Pair<GameCatalog.StartupChoice,
                               GameCatalog.StartupOption>>,
                           fromFrontend: Boolean = false) {
        val generation = ++startGeneration
        val cancelled = AtomicBoolean(false)
        preparingFont = true
        applyPauseState()
        libraryScreen.showStatus("Preparing ${entry.displayName}…")
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
                    runOnUiThread { if (generation == startGeneration) libraryScreen.showStatus(message) }
                }
                val bootFloppy = media?.bootFloppy?.let { companion ->
                    romLibrary.prepare(companion, cancelled) { message ->
                        runOnUiThread { if (generation == startGeneration) libraryScreen.showStatus(message) }
                    }
                }
                val secondFloppy = floppyB?.let { companion ->
                    romLibrary.prepare(companion, cancelled) { message ->
                        runOnUiThread { if (generation == startGeneration) libraryScreen.showStatus(message) }
                    }
                }
                val font = prepareFont()
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
                        PreparedLaunch(disk, media?.bootFloppy, floppyB, swapSources)
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
                        scheduleStartupQueue(game, choices)
                        scheduleDiskSwaps(game.diskSwaps, result.swapSources)
                    }
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
        applyPauseState()
        Thread {
            nativeStop()
            runOnUiThread {
                currentDisk = null
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
                onScreenControls.close()
                onScreenControls.refreshVisibility(false)
                sessionFlow.reset()
                preparingFont = false
                done()
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
        try {
            runOnUiThread {
                if (generation == startGeneration) {
                    swapStatus.text = "Swapping Floppy ${'A' + rule.drive}: ${entry.displayName}"
                    swapStatus.visibility = View.VISIBLE
                }
            }
            val disk = romLibrary.prepare(entry, AtomicBoolean(false)) { message ->
                runOnUiThread {
                    if (generation == startGeneration) swapStatus.text = message
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
            runOnUiThread { if (generation == startGeneration) swapStatus.visibility = View.GONE }
        }
    }

    private fun showDiskSwapFailure(rule: GameCatalog.DiskSwap?, error: Exception,
                                    rules: List<GameCatalog.DiskSwap>,
                                    sources: Map<String, LibraryEntry>) {
        AlertDialog.Builder(this).setTitle("Automatic disk swap failed")
            .setMessage("${rule?.let { "Floppy ${'A' + it.drive}: " } ?: ""}" +
                (error.message ?: "Unknown error") + ". The game is still running.")
            .setPositiveButton("Retry") { _, _ -> scheduleDiskSwaps(rules, sources) }
            .setNeutralButton("Choose floppy") { _, _ -> showFloppyMenu(rule?.drive ?: 0) }
            .setNegativeButton("Continue manually", null).showStyled()
    }

    private fun showStartupTimeout(steps: List<StartupHashMatcher.Step>,
                                   step: StartupHashMatcher.Step) {
        AlertDialog.Builder(this).setTitle("Startup screen not recognized")
            .setMessage("Waiting for ${step.label}. The game is still running.")
            .setPositiveButton("Retry") { _, _ ->
                scheduleStartupSteps(steps.drop(steps.indexOf(step).coerceAtLeast(0)))
            }
            .setNeutralButton("Open keyboard") { _, _ -> showKeyboard() }
            .setNegativeButton("Restart") { _, _ -> restartMachine() }
            .showStyled()
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
    private fun showGameDetails(entry: LibraryEntry) {
        val id = entry.contentId
        val game = romLibrary.catalog.resolve(id ?: "", entry.displayName)
        val catalog = romLibrary.catalog
        fun source(field: String, subfield: String? = null, fallback: String = "App default") =
            id?.let { catalog.sourceOf(it, field, subfield) } ?: fallback
        val controllerSource = if (game.controllerBindings == null ||
            (game.controllerBindings == "[]" && !game.overriddenFields.contains("controller"))) "Global"
            else source("controller", "bindings", fallback = "Global")
        val inputMode = InputModeDecider.parse(game.inputMode ?: InputModeDecider.storageValue(globalInputMode))
        val touch = game.inputTouch ?: touchStorage(globalTouchDirect)
        val touchLabel = "${inputMode.name.lowercase().replaceFirstChar(Char::uppercase)} " +
            "(${source("input", "mode")}) · " +
            "${if (touch == "direct") "direct tap" else "touchpad"} " +
            "(${source("input", "touch")}) · second screen: " +
            "${game.inputSecondary ?: "keyboard"} (${source("input", "secondary")})"
        val common = CommonGameSettings(
            title = GameSettingsValue(game.title, source("title", fallback = "Filename")),
            touch = GameSettingsValue(touchLabel),
            controller = GameSettingsValue("${effectiveControllerBindings(game).size} bindings",
                controllerSource),
            boxArt = GameSettingsValue(if (game.boxArt == null) "None" else "Available",
                source("artwork", "boxArt")),
            screenshot = GameSettingsValue(if (game.preview == null) "None" else "Available",
                source("artwork", "preview")),
            filePath = entry.path,
            zipEntry = entry.zipEntry,
            contentId = id,
            error = entry.error,
            deleteKind = if (libraryEntries.any { it.uri == entry.uri }) "game file" else null)
        val machineRows = listOf(
            GameSettingsRow("Machine clock", "${game.baseClockTenthsMHz?.let { "${it / 10.0} MHz" } ?: "App default"} · ${source("machine", "baseClockTenthsMHz")}", true) {
                editGameClock(entry) },
            GameSettingsRow("GDC clock", "${game.gdcClockTenthsMHz?.let { "${it / 10.0} MHz" } ?: "5.0 MHz (app default)"} · ${source("machine", "gdcClockTenthsMHz")}", true) {
                editGameGdcClock(entry) },
            GameSettingsRow("CPU speed", "${cpuSpeedLabel(game)} · ${source("machine", "cpuMultiple")}", true) {
                editGameCpuSpeed(entry) },
            GameSettingsRow("Startup command", "${game.launchCommand ?: "None"} · ${source("launch")}", true) {
                editGameLaunchCommand(entry, game.launchCommand ?: "") })
        GameSettingsCoordinator.show(this, common, entry.playable, machineRows,
            CommonGameSettingsActions(
                play = { launchEntry(entry, sessionFromFrontend && currentEntry?.id == entry.id &&
                    !libraryVisible) },
                editTouch = { showInputModeChoices(entry) },
                editController = { showControllerBindings(entry) },
                editTitle = { editGameTitle(entry, game.title) },
                editBoxArt = { artwork.edit(entry, "boxArt") },
                editScreenshot = { artwork.edit(entry, "preview") },
                viewScreenshot = { showGamePreview(entry) },
                delete = if (common.deleteKind == null) null else {{ confirmDeleteGame(entry) }},
                reset = id?.let { { saveGameSetting(entry,
                    "Game settings reset. Machine changes apply on next launch or restart.") {
                    catalog.resetOverride(it)
                    if (currentEntry?.contentId == it) {
                        currentGame = catalog.resolve(it, entry.displayName)
                        setSecondaryInitialMode(currentGame?.inputSecondary == "touchpad")
                        gamepadMapper.bindings = effectiveControllerBindings(currentGame)
                    }
                } } },
                resetFailed = { failure -> toast(failure.message ?: "Could not reset game settings") },
                resetCancelled = { showGameDetails(entry) },
                noHash = { toast("This file needs a successful hash before settings can be saved") }))
    }

    private fun editGameTitle(entry: LibraryEntry, current: String) {
        val id = entry.contentId ?: return
        GameTitleEditor.show(this, current,
            { value -> saveGameSetting(entry) { romLibrary.catalog.setOverride(id, "title", value) } },
            { saveGameSetting(entry) { romLibrary.catalog.resetOverride(id, "title") } },
            { showGameDetails(entry) })
    }

    private fun confirmDeleteGame(entry: LibraryEntry) {
        val selected = romTree
        if (selected == null || !libraryFlow.hasWriteGrant(selected)) {
            AlertDialog.Builder(this).setTitle("Write access required")
                .setMessage("Select the same ROM folder again and grant write access before " +
                    "deleting game files.")
                .setPositiveButton("Choose ROM folder") { _, _ -> chooseRomFolder() }
                .setNegativeButton("Cancel", null).showStyled()
            return
        }
        if (!libraryVisible && currentEntry?.uri == entry.uri) {
            toast("Exit this game before deleting its source file")
            return
        }
        val related = libraryEntries.count { it.uri == entry.uri }
        val sourceEntries = libraryEntries
        GameDeletionFlow.show(this, libraryScreen,
            GameDeletionFlow.Prompt(entry.path, "file", affectedEntries = related),
            "Kairo98-delete-game", { romLibrary.deleteSource(entry, sourceEntries) },
            { refreshLibrary(false) }, ::toast)
    }

    private fun showGamePreview(entry: LibraryEntry) =
        artwork.view(entry, returnToSettings = true)

    private fun showDetailPreview(entry: LibraryEntry) = artwork.view(entry)

    private fun saveGameSetting(entry: LibraryEntry,
                                message: String = "Saved. Machine changes apply on next launch or restart.",
                                action: () -> Unit) {
        try {
            action()
            libraryScreen.showEntries(libraryEntries)
            toast(message)
        } catch (error: Exception) {
            toast("Could not save: ${error.message ?: "Invalid value"}")
        }
        showGameDetails(entry)
    }

    private fun editGameLaunchCommand(entry: LibraryEntry, current: String) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(current)
            setSelection(text.length)
            hint = "DOS command, such as GAME"
        }
        AlertDialog.Builder(this).setTitle("Guest command").setView(input)
            .setPositiveButton("Save") { _, _ -> saveGameSetting(entry) {
                val value = input.text.toString().trim()
                if (value.isEmpty()) romLibrary.catalog.updateOverrideSubfields(
                    entry.contentId!!, "launch", mapOf("text" to null, "commands" to null,
                        "screenHashes" to null))
                else romLibrary.catalog.updateOverrideSubfields(entry.contentId!!, "launch",
                    mapOf("text" to value, "commands" to null, "screenHashes" to null))
            } }.setNeutralButton("Reset") { _, _ -> saveGameSetting(entry) {
                romLibrary.catalog.resetOverride(entry.contentId!!, "launch")
            } }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    private fun editGameClock(entry: LibraryEntry) {
        val current = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
        AlertDialog.Builder(this).setTitle("Machine clock")
            .setSingleChoiceItems(arrayOf("Use catalog default", "2 MHz", "2.5 MHz"),
                if (romLibrary.catalog.sourceOf(entry.contentId!!, "machine", "baseClockTenthsMHz") !=
                    "User override") 0 else when (current.baseClockTenthsMHz) {
                    20 -> 1; 25 -> 2; else -> 0
                }) { dialog, which ->
                dialog.dismiss()
                saveGameSetting(entry) {
                    if (which == 0) romLibrary.catalog.resetOverrideSubfield(entry.contentId!!,
                        "machine", "baseClockTenthsMHz")
                    else romLibrary.catalog.setOverrideSubfield(entry.contentId!!, "machine",
                        "baseClockTenthsMHz", if (which == 1) 20 else 25)
                }
            }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    /** CPU MHz for a clock multiple on the game's base clock (2.4576 or 1.9968 MHz). */
    private fun cpuMhz(game: GameCatalog.Game, multiple: Int) =
        (if ((game.baseClockTenthsMHz ?: clock) == 20) 1.9968 else 2.4576) * multiple

    private fun cpuSpeedLabel(game: GameCatalog.Game): String {
        val multiple = game.cpuMultiple ?: DEFAULT_CPU_MULTIPLE
        return "%.1f MHz (x%d)".format(cpuMhz(game, multiple), multiple) +
            if (game.cpuMultiple == null) " · app default" else ""
    }

    private fun editGameCpuSpeed(entry: LibraryEntry) {
        val current = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
        val multiples = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, DEFAULT_CPU_MULTIPLE)
        val labels = listOf("Use catalog default") +
            multiples.map { "%.1f MHz (x%d)".format(cpuMhz(current, it), it) }
        val selected = if (romLibrary.catalog.sourceOf(entry.contentId!!, "machine", "cpuMultiple") ==
            "User override") current.cpuMultiple?.let { multiples.indexOf(it) + 1 } ?: 0 else 0
        AlertDialog.Builder(this).setTitle("CPU speed")
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                saveGameSetting(entry) {
                    if (which == 0) romLibrary.catalog.resetOverrideSubfield(entry.contentId!!,
                        "machine", "cpuMultiple")
                    else romLibrary.catalog.setOverrideSubfield(entry.contentId!!, "machine",
                        "cpuMultiple", multiples[which - 1])
                }
            }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    private fun editGameGdcClock(entry: LibraryEntry) {
        val current = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
        AlertDialog.Builder(this).setTitle("GDC clock")
            .setSingleChoiceItems(arrayOf("Use catalog default", "2.5 MHz", "5 MHz"),
                if (romLibrary.catalog.sourceOf(entry.contentId!!, "machine", "gdcClockTenthsMHz") !=
                    "User override") 0 else when (current.gdcClockTenthsMHz) {
                    25 -> 1; 50 -> 2; else -> 0
                }) { dialog, which ->
                dialog.dismiss()
                saveGameSetting(entry) {
                    if (which == 0) romLibrary.catalog.resetOverrideSubfield(entry.contentId!!,
                        "machine", "gdcClockTenthsMHz")
                    else romLibrary.catalog.setOverrideSubfield(entry.contentId!!, "machine",
                        "gdcClockTenthsMHz", if (which == 1) 25 else 50)
                }
            }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

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

    private fun showFloppyMenu(drive: Int) {
        if (currentDisk == null || !(nativeStatus().startsWith("Running") ||
                nativeStatus().startsWith("Paused"))) {
            toast("Start a game before inserting a floppy")
            return
        }
        if (floppyBusy) return
        val current = currentEntry
        val candidates = libraryEntries.filter { entry ->
            entry.playable && entry.isFloppy && current != null &&
                (if (current.zipEntry != null) entry.uri == current.uri
                else entry.path.substringBeforeLast('/', "") ==
                    current.path.substringBeforeLast('/', ""))
        }
        val labels = ArrayList<String>().apply {
            add("Eject")
            addAll(candidates.map { it.displayName })
            add("Choose floppy file…")
        }
        AlertDialog.Builder(this).setTitle("Floppy ${'A' + drive}" +
            (mountedFloppies[drive]?.let { " · $it" } ?: " · empty"))
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> changeFloppy(drive, null)
                    labels.lastIndex -> chooseFloppyFile(drive)
                    else -> changeFloppy(drive, candidates[which - 1])
                }
            }.setNegativeButton("Cancel", null).showStyled()
    }

    private fun showMountMenu() {
        val hardDisk = currentEntry?.displayName ?: currentTitle ?: "Empty"
        AlertDialog.Builder(this).setTitle("Mount")
            .setItems(arrayOf(
                "Hard disk  ·  $hardDisk",
                "Floppy A  ·  ${mountedFloppies[0] ?: "Empty"}",
                "Floppy B  ·  ${mountedFloppies[1] ?: "Empty"}"
            )) { _, which ->
                when (which) {
                    0 -> chooseHdi()
                    1 -> showFloppyMenu(0)
                    2 -> showFloppyMenu(1)
                }
            }.setNegativeButton("Close", null).showStyled()
    }

    private fun changeFloppy(drive: Int, entry: LibraryEntry?) {
        if (!beginFloppyChange()) return
        menuStatus.text = if (entry == null) "Ejecting floppy ${'A' + drive}…"
            else "Preparing ${entry.displayName}…"
        Thread {
            val result = try {
                val disk = entry?.let { romLibrary.prepare(it, AtomicBoolean(false)) { message ->
                    runOnUiThread { menuStatus.text = message }
                } }
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
                menuStatus.text = result ?: "Floppy ${'A' + drive}: ${entry?.displayName ?: "empty"}"
                if (result != null) toast(result)
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

    private fun showMachine() {
        val options = mutableListOf("Base clock  ·  ${if (clock == 25) "2.5" else "2"} MHz")
        if (firmwareConfigurable) options += listOf(
            "BIOS ROM  ·  ${if (biosFile().isFile) "Imported" else "None"}",
            "Font BMP  ·  ${if (fontBitmapFile().isFile) "Imported" else "Built-in"}",
            "YM2608 rhythm ROM  ·  ${if (rhythmRomFile().isFile) "Imported" else "None"}")
        AlertDialog.Builder(this).setTitle("Machine")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showMachineClock()
                    1 -> showBiosRom()
                    2 -> showFontBitmap()
                    else -> showRhythmRom()
                }
            }.setNegativeButton("Close", null).showStyled()
    }

    private fun showMachineClock() {
        val options = arrayOf("2.5 MHz base clock", "2 MHz base clock")
        AlertDialog.Builder(this).setTitle("Machine")
            .setSingleChoiceItems(options, if (clock == 25) 0 else 1) { dialog, which ->
                val selected = if (which == 0) 25 else 20
                if (clock != selected) {
                    clock = selected
                    preferences.edit().putInt("base_clock", clock).apply()
                    if (currentGame?.baseClockTenthsMHz == null) {
                        nativeClock(clock)
                        toast("Clock change resets the machine")
                    } else toast("Global clock saved; this game uses its own clock")
                }
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).showStyled()
    }

    private fun showBiosRom() {
        if (!firmwareConfigurable) return
        val installed = biosFile().isFile
        val dialog = AlertDialog.Builder(this).setTitle("BIOS ROM")
            .setPositiveButton("Choose file") { _, _ -> chooseBiosFile() }
            .setNegativeButton("Close", null)
        if (installed) dialog.setNeutralButton("Remove") { _, _ ->
            if (firmwareConfigurable && !biosBusy) {
                if (biosFile().delete()) toast("BIOS ROM removed" +
                    if (currentDisk != null) ". Restart the game to apply." else ".")
                else toast("Could not remove BIOS ROM")
            }
        }
        dialog.showStyled()
    }

    private fun showFontBitmap() {
        if (!firmwareConfigurable) return
        val dialog = AlertDialog.Builder(this).setTitle("Font BMP")
            .setPositiveButton("Choose file") { _, _ -> chooseFontFile() }
            .setNegativeButton("Close", null)
        if (fontBitmapFile().isFile) dialog.setNeutralButton("Remove") { _, _ ->
            if (firmwareConfigurable && !fontBusy) {
                if (fontBitmapFile().delete()) toast("Font BMP removed" +
                    if (currentDisk != null) ". Restart the game to apply." else ".")
                else toast("Could not remove Font BMP")
            }
        }
        dialog.showStyled()
    }

    private fun chooseBiosFile() {
        if (!firmwareConfigurable) return
        DocumentPicker.open(this, BIOS_REQUEST, failed = ::toast)
    }

    private fun chooseFontFile() {
        if (!firmwareConfigurable) return
        DocumentPicker.open(this, FONT_REQUEST, failed = ::toast)
    }

    private fun showRhythmRom() {
        if (!firmwareConfigurable) return
        val dialog = AlertDialog.Builder(this).setTitle("YM2608 rhythm ROM")
            .setPositiveButton("Choose file") { _, _ -> chooseRhythmFile() }
            .setNegativeButton("Close", null)
        if (rhythmRomFile().isFile) dialog.setNeutralButton("Remove") { _, _ ->
            if (firmwareConfigurable && !rhythmBusy) {
                if (rhythmRomFile().delete()) toast("Rhythm ROM removed" +
                    if (currentDisk != null) ". Restart the game to apply." else ".")
                else toast("Could not remove rhythm ROM")
            }
        }
        dialog.showStyled()
    }

    private fun chooseRhythmFile() {
        if (!firmwareConfigurable) return
        DocumentPicker.open(this, RHYTHM_REQUEST, failed = ::toast)
    }

    private fun showInputMode() {
        val entry = currentEntry?.takeIf { it.contentId != null && !libraryVisible }
        showInputModeChoices(entry)
    }

    private fun touchStorage(direct: Boolean) = if (direct) "direct" else "touchpad"

    private fun setSecondaryInitialMode(touchpad: Boolean) {
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setInitialMode(touchpad)
        if (::swappedKeyboardPanel.isInitialized) swappedKeyboardPanel.setInitialMode(touchpad)
    }

    private fun directTapActive(): Boolean = touchSelection().selection.directTouch

    private fun touchInputLabel(): String {
        val game = currentGame.takeIf { !libraryVisible }
        val selection = touchSelection(game).selection
        val modeLabel = when (selection.mode) {
            InputModeDecider.Mode.AUTO -> "Auto"
            InputModeDecider.Mode.KEYBOARD -> "Keyboard"
            InputModeDecider.Mode.MOUSE -> "Mouse"
        }
        return "$modeLabel · ${if (selection.directTouch) "direct tap" else "touchpad"}" +
            if (game != null && game.overriddenFields.contains("input")) " · this game" else ""
    }

    private fun showInputModeChoices(entry: LibraryEntry?) {
        touchSettingsCoordinator.builder(entry)?.showStyled()
    }

    private fun showAbout() {
        AboutDocuments.show(this, "Kairo98",
            "Open the menu with a controller Mode/Home button when Android delivers it, Back, Menu, or a swipe from the left edge. Swipe inward from the right edge to open the PC-98 keyboard. Android reserves the system Home key.\n\nPhysical keyboard input goes to the PC-98 while the menu is closed.\n\nKairo98 uses Neko Project 21/W and ymfm. Source and provenance: github.com/MrJackSpade/Kairo98.",
            "PRIVACY_POLICY.txt", "THIRD_PARTY_NOTICES.txt")
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
            "restart" -> confirmRestart()
            "exit" -> confirmExit()
            "fastForward" -> nativeSetFastForward(true)
        }
    }

    private fun controllerActionReleased(action: String) {
        if (action == "fastForward") nativeSetFastForward(false)
    }

    private fun showControllerScope() {
        controllerEditorFlow.showScope(currentEntry?.takeIf {
            !libraryVisible && it.contentId != null })
    }

    private fun showOnScreenControls() {
        controllerEditorFlow.showOnScreenControls()
    }

    private fun showControllerBindings(entry: LibraryEntry) {
        controllerEditorFlow.showGame(entry)
    }

    private fun AlertDialog.Builder.showStyled(): AlertDialog {
        val dialog = create()
        dialog.setOnDismissListener { refreshSettingValues() }
        dialog.show()
        Ui.styleDialog(dialog)
        // A new window can open in touch mode even while a controller is in use, and then the
        // first D-pad press only leaves touch mode. Match the main window instead.
        if (::root.isInitialized && !root.isInTouchMode) dialog.window?.decorView?.post {
            val decor = dialog.window?.decorView as? ViewGroup ?: return@post
            val target = decor.findFocus()
                ?: android.view.FocusFinder.getInstance().findNextFocus(decor, null, View.FOCUS_DOWN)
            target?.requestFocusFromTouch()
        }
        return dialog
    }

    private fun confirmRestart() {
        val title = currentTitle ?: "the machine"
        AlertDialog.Builder(this).setTitle("Restart $title?")
            .setMessage("Progress since your last save state or in-game save is lost.")
            .setPositiveButton("Restart") { _, _ -> restartMachine() }
            .setNegativeButton("Cancel", null).showStyled()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this).setTitle("Exit Kairo98?")
            .setMessage("The machine stops. Progress since your last save state or in-game save is lost.")
            .setPositiveButton("Exit") { _, _ -> exitApp() }
            .setNegativeButton("Cancel", null).showStyled()
    }

    private fun showStateSlots(saving: Boolean) = stateFlow.show(saving)

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
        menuStatus.text = "Stopping machine…"
        Thread {
            nativeStop()
            runOnUiThread { finish() }
        }.start()
    }

    private fun toast(message: String) =
        Ui.message(this, message)

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
        if (relocating) return
        graphics.load()
        root.post { updateViewport() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (relocating) return
        guestLifecycle.onWindowFocusChanged(hasFocus)
    }

    override fun onDestroy() {
        if (relocating) { super.onDestroy(); return }
        catalogUpdates.cancel()
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
        if (artwork.handleActivityResult(requestCode, resultCode, data)) return
        if (libraryFlow.handleActivityResult(requestCode, resultCode, data)) return
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
        preparingFont = true
        applyPauseState()
        toast("Importing disk image")
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
                    preferences.edit().putString("disk_name", name).apply()
                    controllerProfiles.configuration.endSession()
                    controllerProfiles.configuration.beginSession()
                    currentEntry = null
                    currentDisk = disk
                    currentIsFloppy = DiskFormat.isFloppy(name)
                    mountedFloppies[0] = if (currentIsFloppy) name else null
                    mountedFloppies[1] = null
                    currentTitle = name
                    currentGame = null
                    setSecondaryInitialMode(false)
                    inputModeDecider.reset()
                    gamepadMapper.bindings = controllerFlow.global()
                    sessionNavigation.enterGame(notify = false)
                }
                applyPauseState()
                toast(result)
            }
        }.start()
    }

    private fun importFloppyFile(drive: Int, uri: Uri, name: String) {
        if (!beginFloppyChange()) return
        menuStatus.text = "Importing $name…"
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
                } else {
                    disk.delete()
                    toast(result)
                }
                menuStatus.text = result ?: "Floppy ${'A' + drive}: $name"
            }
        }.start()
    }

    private fun importBiosRom(uri: Uri) {
        if (!firmwareConfigurable || biosBusy) return
        biosBusy = true
        val restartNeeded = currentDisk != null
        if (firstRunSetup.isOpen) firstRunSetup.setBusy("Importing BIOS ROM…")
        if (menuOpen) menuStatus.text = "Importing BIOS ROM…"
        toast("Importing BIOS ROM")
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
                firstRunSetup.setBusy(null)
                firstRunSetup.refreshFirmware()
                if (menuOpen) menuStatus.text = result
                toast(result)
            }
        }.start()
    }

    private fun importFontBitmap(uri: Uri) {
        if (!firmwareConfigurable || fontBusy) return
        fontBusy = true
        val restartNeeded = currentDisk != null
        if (firstRunSetup.isOpen) firstRunSetup.setBusy("Importing Font BMP…")
        if (menuOpen) menuStatus.text = "Importing Font BMP…"
        toast("Importing Font BMP")
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
                firstRunSetup.setBusy(null)
                firstRunSetup.refreshFirmware()
                if (menuOpen) menuStatus.text = result
                toast(result)
            }
        }.start()
    }

    private fun importRhythmRom(uri: Uri) {
        if (!firmwareConfigurable || rhythmBusy) return
        rhythmBusy = true
        val restartNeeded = currentDisk != null
        if (firstRunSetup.isOpen) firstRunSetup.setBusy("Importing rhythm ROM…")
        if (menuOpen) menuStatus.text = "Importing rhythm ROM…"
        toast("Importing rhythm ROM")
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
                            require(size <= RHYTHM_ROM_BYTES) { "Rhythm ROM must be exactly 8 KiB" }
                            output.write(buffer, 0, count)
                        }
                        require(size == RHYTHM_ROM_BYTES) { "Rhythm ROM must be exactly 8 KiB" }
                    }
                }
                Files.move(partial.toPath(), rhythmRomFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
                if (restartNeeded) "Rhythm ROM imported. Restart the game to apply."
                else "Rhythm ROM imported."
            } catch (error: Exception) {
                "Rhythm ROM import failed: ${error.message ?: "Unknown error"}"
            } finally { partial.delete() }
            runOnUiThread {
                rhythmBusy = false
                firstRunSetup.setBusy(null)
                firstRunSetup.refreshFirmware()
                if (menuOpen) menuStatus.text = result
                toast(result)
            }
        }.start()
    }

    private fun startWithFont(disk: File, message: String, floppy: Boolean) {
        if (preparingFont) return
        val generation = ++startGeneration
        preparingFont = true
        applyPauseState()
        toast("Preparing PC-98 font")
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
        if (!::firstRunSetup.isInitialized || firstRunSetup.isOpen ||
            !::onScreenControls.isInitialized || onScreenControls.isOpen ||
            !::controllerEditor.isInitialized || controllerEditor.isOpen ||
            !::sessionFlow.isInitialized || !::keyboardPanel.isInitialized || libraryVisible)
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
