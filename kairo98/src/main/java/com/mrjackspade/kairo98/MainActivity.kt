package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.SecondaryDisplayCoordinator
import com.mrjackspade.kairo.frontend.EdgeSwipeNavigation
import com.mrjackspade.kairo.frontend.RgDsDisplayRouter
import com.mrjackspade.kairo.frontend.GuestKeyboardPanel
import com.mrjackspade.kairo.frontend.GraphicsOptions
import com.mrjackspade.kairo.frontend.GameDeletionFlow

import com.mrjackspade.kairo.frontend.InputRouter
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
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.input.InputManager
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
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
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
    private lateinit var inputManager: InputManager
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) { gamepadMapper.releaseDevice(deviceId) }
        override fun onInputDeviceRemoved(deviceId: Int) { gamepadMapper.releaseDevice(deviceId) }
    }

    private val preferences by lazy { getSharedPreferences("kairo98", MODE_PRIVATE) }
    private val graphics by lazy {
        GraphicsOptions(this, preferences, { builder -> builder.showStyled() },
            { updateViewport(); refreshSettingValues() }, ::toast)
    }
    private val controllerProfiles by lazy {
        ControllerProfileStore(preferences, ControllerBindings::parse,
            { ControllerBindings.toJson(it).toString() })
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
            }, "art/example.webp", { libraryScreen.showEntries(libraryEntries) },
            ::showGameDetails, ::toast, GameCatalog::validImageUrl)
    }
    private lateinit var controllerEditor: ControllerEditor<LibraryEntry>
    private val controllerEditorFlow by lazy {
        ControllerEditorFlow(controllerEditor, LibraryEntry::contentId, ::closeMenu,
            ::releaseInputs, ::hideKeyboard, { onScreenControls.show() }, ::toast)
    }
    private var libraryVisible = true
    private var romTree: Uri?
        get() = libraryFlow.tree
        set(value) { libraryFlow.tree = value }
    private val artworkDownloads by lazy {
        CatalogArtworkDownloadController(this, libraryScreen, { libraryEntries },
            romLibrary.catalog::missingArtworkFor, romLibrary.catalog::downloadArtwork)
    }
    private val catalogUpdates by lazy {
        CatalogUpdateController(this, { romLibrary.catalog.downloadUpdate() },
            libraryScreen::showStatus, { libraryScreen.showEntries(libraryEntries) },
            { if (libraryVisible) toast("Game catalog updated") })
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
    private var userPaused = false
    private var activityVisible = false
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
    @Volatile private var stateBusy = false
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
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
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
        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(inputDeviceListener, handler)
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
            { ControllerBindings.toJson(it) })
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
        handler.post(updateStatus)
        root.post {
            libraryFlow.restore()
            val setupStep = preferences.getInt("onboarding_step_v1", if (romTree == null) 0 else 2)
            val externallyRequested = savedInstanceState == null &&
                ExternalGameIntent.hasRequest(intent)
            if (externallyRequested) dispatchExternalGame(intent)
            else if (romTree != null && hasRomGrant(romTree!!) && setupStep >= 2 &&
                !selectPendingDebugGame()) refreshLibrary(false)
            if (setupStep < 2 && !externallyRequested) firstRunSetup.show(if (setupStep == 0)
                FirstRunSetup.Step.ROM_FOLDER else FirstRunSetup.Step.FIRMWARE)
            applyPauseState()
            updateGameCatalog(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
            SessionAction("Library", R.drawable.ic_library) { showLibrary() }
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
        SettingsEntry("Sound", { if (muted) "Muted · tap to turn on" else "On · tap to mute" }) { toggleMute() },
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

    private val libraryArtExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var librarySelectionGeneration = 0

    /** Shows the selected library game on the second screen, if there is one. */
    private fun showLibrarySelection(entry: LibraryEntry?) {
        if (!::secondaryKeyboard.isInitialized) return
        val generation = ++librarySelectionGeneration
        if (entry == null) {
            secondaryKeyboard.setLibraryInfo(null)
            return
        }
        val game = romLibrary.catalog.resolve(entry.contentId ?: "", entry.displayName)
        val media = entry.zipEntry ?: entry.path
        val tags = listOf(if (DiskFormat.isFloppy(media)) "Floppy disk" else "Hard disk") +
            ((variantLabel(entry.path) ?: entry.zipEntry?.let(::variantLabel))?.split("  ·  ") ?: emptyList())
        val info = SecondaryDisplayCoordinator.LibraryInfo(game.title,
            listOf(fileLabel(entry)) + tags + game.tags,
            game.description ?: "No description available yet.", null)
        secondaryKeyboard.setLibraryInfo(info)
        val art = game.preview ?: return
        libraryArtExecutor.execute {
            val bitmap = try {
                romLibrary.catalog.openArtwork(art).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                }
            } catch (_: Exception) { null } ?: return@execute
            runOnUiThread {
                if (generation == librarySelectionGeneration && libraryVisible)
                    secondaryKeyboard.setLibraryInfo(info.copy(art = bitmap))
            }
        }
    }

    private fun applyPauseState() {
        if (!libraryVisible && ::secondaryKeyboard.isInitialized) {
            librarySelectionGeneration++
            secondaryKeyboard.setLibraryInfo(null)
        }
        val editingControls = ::onScreenControls.isInitialized && onScreenControls.isOpen
        val showingGuest = activityVisible && !libraryVisible && !menuOpen &&
            !editingControls && !preparingFont &&
            !(::controllerEditor.isInitialized && controllerEditor.isOpen)
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
        nativePause(userPaused || menuOpen || libraryVisible || !activityVisible || preparingFont ||
            (::controllerEditor.isInitialized && controllerEditor.isOpen) || editingControls)
        if (::onScreenControls.isInitialized) {
            val controlsPlaying = !libraryVisible && !menuOpen && !editingControls &&
                activityVisible && !preparingFont &&
                !(::controllerEditor.isInitialized && controllerEditor.isOpen)
            if (::libraryFlow.isInitialized) touchUi.refreshControls(onScreenControls,
                controlsPlaying, ::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped)
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
                        libraryScreen.dismissSystemKeyboard()
                        libraryVisible = false
                        libraryScreen.visibility = View.GONE
                        screen.requestFocus()
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

    private fun showLibrary() {
        edgeSwipes.reset()
        if (sessionFromFrontend) {
            exitApp()
            return
        }
        commandCancelled.set(true)
        releaseInputs()
        hideKeyboard()
        libraryScreen.closeActions()
        libraryScreen.closeDetail()
        libraryVisible = true
        closeMenu()
        libraryScreen.visibility = View.VISIBLE
        libraryScreen.showEntries(libraryEntries)
        libraryScreen.showFolder(romTree?.let(::folderLabel))
        libraryScreen.showStatus("${libraryEntries.count { it.playable }} games ready")
        applyPauseState()
    }

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
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, if (drive == 0) FLOPPY_A_REQUEST else FLOPPY_B_REQUEST)
    }

    private fun chooseHdi() {
        closeMenu()
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, HDI_REQUEST)
    }

    private fun showMachine() {
        val bios = biosFile()
        val font = fontBitmapFile()
        val rhythm = rhythmRomFile()
        AlertDialog.Builder(this).setTitle("Machine")
            .setItems(arrayOf("Base clock  ·  ${if (clock == 25) "2.5" else "2"} MHz",
                "BIOS ROM  ·  ${if (bios.isFile) "Imported" else "None"}",
                "Font BMP  ·  ${if (font.isFile) "Imported" else "Built-in"}",
                "YM2608 rhythm ROM  ·  ${if (rhythm.isFile) "Imported" else "None"}")) { _, which ->
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
        val installed = biosFile().isFile
        val dialog = AlertDialog.Builder(this).setTitle("BIOS ROM")
            .setPositiveButton("Choose file") { _, _ -> chooseBiosFile() }
            .setNegativeButton("Close", null)
        if (installed) dialog.setNeutralButton("Remove") { _, _ ->
            if (!biosBusy) {
                if (biosFile().delete()) toast("BIOS ROM removed" +
                    if (currentDisk != null) ". Restart the game to apply." else ".")
                else toast("Could not remove BIOS ROM")
            }
        }
        dialog.showStyled()
    }

    private fun showFontBitmap() {
        val dialog = AlertDialog.Builder(this).setTitle("Font BMP")
            .setPositiveButton("Choose file") { _, _ -> chooseFontFile() }
            .setNegativeButton("Close", null)
        if (fontBitmapFile().isFile) dialog.setNeutralButton("Remove") { _, _ ->
            if (!fontBusy) {
                if (fontBitmapFile().delete()) toast("Font BMP removed" +
                    if (currentDisk != null) ". Restart the game to apply." else ".")
                else toast("Could not remove Font BMP")
            }
        }
        dialog.showStyled()
    }

    private fun chooseBiosFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, BIOS_REQUEST)
    }

    private fun chooseFontFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, FONT_REQUEST)
    }

    private fun showRhythmRom() {
        val dialog = AlertDialog.Builder(this).setTitle("YM2608 rhythm ROM")
            .setPositiveButton("Choose file") { _, _ -> chooseRhythmFile() }
            .setNegativeButton("Close", null)
        if (rhythmRomFile().isFile) dialog.setNeutralButton("Remove") { _, _ ->
            if (!rhythmBusy) {
                if (rhythmRomFile().delete()) toast("Rhythm ROM removed" +
                    if (currentDisk != null) ". Restart the game to apply." else ".")
                else toast("Could not remove rhythm ROM")
            }
        }
        dialog.showStyled()
    }

    private fun chooseRhythmFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, RHYTHM_REQUEST)
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
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        AlertDialog.Builder(this).setTitle("Kairo98 $version")
            .setMessage("Open the menu with a controller Mode/Home button when Android delivers it, Back, Menu, or a swipe from the left edge. Swipe inward from the right edge to open the PC-98 keyboard. Android reserves the system Home key.\n\nPhysical keyboard input goes to the PC-98 while the menu is closed.")
            .setNeutralButton("Licenses") { _, _ -> showThirdPartyNotices() }
            .setNegativeButton("Privacy policy") { _, _ -> showPrivacyPolicy() }
            .setPositiveButton("Done", null).showStyled()
    }

    private fun showPrivacyPolicy() {
        val policy = assets.open("PRIVACY_POLICY.txt").bufferedReader().use { it.readText() }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = TextView(this).apply {
            text = policy
            textSize = Ui.SECONDARY
            setTextColor(Ui.TEXT)
            setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("Kairo98 Privacy Policy")
            .setView(scroll).setPositiveButton("Done", null).showStyled()
    }

    private fun showThirdPartyNotices() {
        val notice = assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = TextView(this).apply {
            text = notice
            textSize = Ui.LABEL
            setTextColor(Ui.TEXT)
            setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("Third-party licenses")
            .setView(scroll).setPositiveButton("Done", null).showStyled()
    }

    private fun effectiveControllerBindings(game: GameCatalog.Game?): List<ControllerBinding> {
        val configured = game?.controllerBindings
        return if (game?.overriddenFields?.contains("controller") == true ||
            (configured != null && (configured != "[]" || game.controllerProfile == "custom-v1")))
            ControllerBindings.parse(configured)
        else controllerFlow.global()
    }

    private fun releaseInputs(preserveAutomation: Boolean = false) {
        if (!preserveAutomation) {
            debugAutoAdvance?.cancel()
            debugAutoAdvance = null
        }
        gamepadMapper.releaseAll()
        inputRouter.releaseAll(if (preserveAutomation)
            setOf("guest-command", "disk-swap", "debug-auto-space") else emptySet())
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

    private fun stateSlots(): StateSlots? = currentEntry?.contentId?.takeIf { !libraryVisible }
        ?.let { StateSlots(File(filesDir, "states"), it) }

    private fun showStateSlots(saving: Boolean) {
        val slots = stateSlots()
        if (slots == null) {
            AlertDialog.Builder(this).setTitle(if (saving) "Save state" else "Load state")
                .setMessage("Save states are available for games started from the library.")
                .setPositiveButton("Close", null).showStyled()
            return
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle((if (saving) "Save state" else "Load state") + (currentTitle?.let { " · $it" } ?: ""))
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton("Cancel", null).showStyled()
        val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT)
        slots.slots().forEach { slot ->
            val available = saving || !slot.empty
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                isFocusable = available
                isClickable = available
                background = menuHighlight()
                alpha = if (available) 1f else 0.45f
            }
            val thumbnail = slots.thumbnail(slot)
            row.addView(if (thumbnail != null) ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = Ui.rounded(this@MainActivity, Ui.BG, 4)
                clipToOutline = true
                setImageBitmap(thumbnail)
            } else FrameLayout(this).apply {
                // An empty slot reads as a place to put something, not a missing image.
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setStroke(dp(1), Ui.LINE, dp(4).toFloat(), dp(3).toFloat())
                }
                if (saving) addView(Ui.icon(this@MainActivity, R.drawable.ic_save, Ui.TEXT_FAINT, 20),
                    FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(128), dp(80)))
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
            }
            text.addView(TextView(this).apply {
                this.text = "Slot ${slot.index}"
                textSize = Ui.BODY
                setTextColor(Ui.TEXT)
            })
            text.addView(TextView(this).apply {
                this.text = slot.savedAt?.let { format.format(java.util.Date(it)) }
                    ?: if (saving) "Empty · tap to save here" else "Empty"
                textSize = Ui.SECONDARY
                setTextColor(Ui.TEXT_MUTED)
            })
            row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            if (available) row.setOnClickListener {
                dialog.dismiss()
                val savedAt = slot.savedAt
                when {
                    saving && savedAt == null -> saveState(slots, slot.index)
                    saving -> AlertDialog.Builder(this).setTitle("Overwrite slot ${slot.index}?")
                        .setMessage("The save from ${format.format(java.util.Date(savedAt!!))} is replaced.")
                        .setPositiveButton("Overwrite") { _, _ -> saveState(slots, slot.index) }
                        .setNegativeButton("Cancel", null).showStyled()
                    else -> AlertDialog.Builder(this).setTitle("Load slot ${slot.index}?")
                        .setMessage("Progress since that save is lost.")
                        .setPositiveButton("Load") { _, _ -> loadState(slots, slot.index) }
                        .setNegativeButton("Cancel", null).showStyled()
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
        (0 until list.childCount).map(list::getChildAt).firstOrNull { it.isFocusable }?.requestFocus()
    }

    /** Captures the displayed guest frame for a slot thumbnail; null when it cannot be read. */
    private fun captureThumbnail(done: (Bitmap?) -> Unit) {
        val surface = (if (::secondaryKeyboard.isInitialized) secondaryKeyboard.activeGameSurface else null)
            ?: screen
        if (surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) {
            done(null)
            return
        }
        val bitmap = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
        try {
            android.view.PixelCopy.request(surface, bitmap, { result ->
                done(if (result == android.view.PixelCopy.SUCCESS) bitmap else null)
            }, handler)
        } catch (_: IllegalArgumentException) {
            done(null)
        }
    }

    private fun saveState(slots: StateSlots, index: Int) {
        if (stateBusy) return
        stateBusy = true
        menuStatus.text = "Saving slot $index…"
        captureThumbnail { thumbnail ->
            Thread {
                val scratch = try { slots.beginSave(index) } catch (_: Exception) { null }
                val result = scratch?.let { nativeSaveState(it.absolutePath) } ?: 3
                val message = if (result == 0 && scratch != null) {
                    try {
                        slots.commitSave(index, scratch, thumbnail)
                        "Saved to slot $index"
                    } catch (error: Exception) {
                        slots.abandonSave(scratch)
                        "Save failed: ${error.message}"
                    }
                } else {
                    scratch?.let(slots::abandonSave)
                    "Save failed (${stateError(result)})"
                }
                runOnUiThread {
                    stateBusy = false
                    menuStatus.text = message
                    toast(message)
                }
            }.start()
        }
    }

    private fun loadState(slots: StateSlots, index: Int) {
        if (stateBusy) return
        stateBusy = true
        menuStatus.text = "Loading slot $index…"
        val directory = slots.slot(index).directory
        Thread {
            val result = nativeLoadState(directory.absolutePath)
            runOnUiThread {
                stateBusy = false
                when (result) {
                    0 -> {
                        releaseInputs()
                        inputModeDecider.reset()
                        userPaused = false
                        closeMenu()
                        toast("Loaded slot $index")
                    }
                    // Nothing was changed yet, so the running game continues untouched.
                    1, 2, 3 -> {
                        menuStatus.text = "Load failed (${stateError(result)})"
                        toast("Load failed (${stateError(result)})")
                    }
                    else -> {
                        // The machine was partly replaced; start the game again from its disks.
                        toast("Load failed (${stateError(result)}). Restarting the game.")
                        currentEntry?.let { entry ->
                            userPaused = false
                            closeMenu()
                            launchEntry(entry, sessionFromFrontend)
                        }
                    }
                }
            }
        }.start()
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
        if (relocating) { super.onPause(); return }
        if (::secondaryKeyboard.isInitialized && secondaryKeyboard.isCompanionActive) {
            super.onPause()
            return
        }
        commandCancelled.set(true)
        releaseInputs()
        activityVisible = false
        applyPauseState()
        secondaryKeyboard.stop()
        super.onPause()
    }

    override fun onStop() {
        if (!relocating) {
            edgeSwipes.reset()
            commandCancelled.set(true)
            releaseInputs()
            activityVisible = false
            applyPauseState()
            secondaryKeyboard.stop()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (relocating) return
        secondaryKeyboard.start(handler)
        activityVisible = true
        applyPauseState()
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
        if (!hasFocus) {
            // Automated keys go directly to the core and do not require focus.
            // Release manual input without aborting or shortening those keys.
            releaseInputs(preserveAutomation = true)
        }
    }

    override fun onDestroy() {
        if (relocating) { super.onDestroy(); return }
        startGeneration++
        libraryFlow.cancel()
        externalDispatcher.cancel()
        if (::libraryFlow.isInitialized) artworkDownloads.cancel()
        releaseInputs()
        secondaryKeyboard.stop()
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
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
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
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
                    libraryVisible = false
                    libraryScreen.visibility = View.GONE
                    screen.requestFocus()
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
        if (biosBusy) return
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
        if (fontBusy) return
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
        if (rhythmBusy) return
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // A key press means a controller or keyboard is in use. Leave touch mode now, so the
        // next screen's focus lands where it is requested and no press is spent leaving it.
        if (event.action == KeyEvent.ACTION_DOWN && ::root.isInitialized && root.isInTouchMode)
            (currentFocus ?: screen).requestFocusFromTouch()
        if (::firstRunSetup.isInitialized && firstRunSetup.isOpen)
            return firstRunSetup.handleKey(event)
        if (::onScreenControls.isInitialized && onScreenControls.isOpen) {
            if (onScreenControls.handleKey(event)) return true
            return super.dispatchKeyEvent(event)
        }
        if (::controllerEditor.isInitialized && controllerEditor.isOpen) {
            if (controllerEditor.handleKey(event)) return true
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handleBack()
            return true
        }
        if (libraryVisible) {
            if (FrontendNavigation.library(libraryScreen,
                    FrontendNavigation.control(event, gamepadMapper), event,
                    ::handleBack)) return true
            return super.dispatchKeyEvent(event)
        }
        if ((event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE &&
            !gamepadMapper.hasButton(event.keyCode)) ||
            event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (menuOpen) closeMenu() else openMenu()
            }
            return true
        }
        if (menuOpen) {
            if (FrontendNavigation.session(sessionDrawer,
                    FrontendNavigation.control(event, gamepadMapper), event,
                    ::closeMenu)) return true
            super.dispatchKeyEvent(event)
            return true
        }
        if (gamepadMapper.key(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::firstRunSetup.isInitialized && firstRunSetup.isOpen) return true
        if (::onScreenControls.isInitialized && onScreenControls.isOpen) return true
        if (::controllerEditor.isInitialized && controllerEditor.isOpen)
            return controllerEditor.captureMotion(event)
        if (!menuOpen && !libraryVisible && gamepadMapper.motion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

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

    private fun handleBack() {
        if (::firstRunSetup.isInitialized && firstRunSetup.isOpen) {
            firstRunSetup.back()
            return
        }
        if (::onScreenControls.isInitialized && onScreenControls.isOpen) {
            onScreenControls.back()
            return
        }
        if (::controllerEditor.isInitialized && controllerEditor.isOpen) {
            controllerEditor.back()
            return
        }
        if (libraryVisible) {
            if (libraryScreen.closeDetail()) return
            if (libraryScreen.closeActions()) return
            if (currentDisk != null) {
                libraryVisible = false
                libraryScreen.visibility = View.GONE
                applyPauseState()
            } else finish()
        } else if (menuOpen) closeMenu()
        else if (keyboardPanel.visibility == View.VISIBLE) hideKeyboard()
        else openMenu()
    }

    /** App screens that take controller input instead of the guest. */
    private fun appScreenOpen() = menuOpen || libraryVisible ||
        (::controllerEditor.isInitialized && controllerEditor.isOpen) ||
        (::onScreenControls.isInitialized && onScreenControls.isOpen) ||
        (::firstRunSetup.isInitialized && firstRunSetup.isOpen)

    private fun isNavigationKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_TAB

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // On app screens, leave directions unhandled so Android moves focus between controls;
        // other keys stop here so they never reach the guest or close the activity.
        if (appScreenOpen()) return !isNavigationKey(keyCode)
        if (KeyEvent.isGamepadButton(keyCode) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true
        val scanCode = pc98ScanCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) inputRouter.hold("keyboard:${event.deviceId}:$keyCode", listOf(scanCode))
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (appScreenOpen()) return !isNavigationKey(keyCode)
        if (KeyEvent.isGamepadButton(keyCode) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true
        val scanCode = pc98ScanCode(keyCode) ?: return super.onKeyUp(keyCode, event)
        inputRouter.release("keyboard:${event.deviceId}:$keyCode")
        return true
    }

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
