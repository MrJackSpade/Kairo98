package com.mrjackspade.kairo98

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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
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

    private val inputRouter = InputRouter(::nativeKey)
    private val joystickRouter = JoystickInputRouter(::nativeJoystick)
    private val mouseRouter = MouseInputRouter(::nativeMouseMove, ::nativeMouseButton)
    private val gamepadMapper = GamepadMapper(inputRouter, joystickRouter, mouseRouter, ::controllerAction)
    private lateinit var inputManager: InputManager
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) { gamepadMapper.releaseDevice(deviceId) }
        override fun onInputDeviceRemoved(deviceId: Int) { gamepadMapper.releaseDevice(deviceId) }
    }

    private val preferences by lazy { getSharedPreferences("kairo98", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var screen: SurfaceView
    private lateinit var keyboardPanel: Pc98KeyboardPanel
    private lateinit var swappedKeyboardPanel: Pc98KeyboardPanel
    private lateinit var secondaryKeyboard: SecondaryKeyboardDisplay
    private lateinit var onScreenControls: OnScreenControls
    private lateinit var libraryScreen: LibraryScreen
    private lateinit var firstRunSetup: FirstRunSetup
    private lateinit var romLibrary: RomLibrary
    private lateinit var controllerEditor: ControllerEditor
    private var libraryVisible = true
    private var romTree: Uri? = null
    private var scanCancelled = AtomicBoolean(false)
    private var artworkDownloadCancelled = AtomicBoolean(false)
    @Volatile private var artworkDownloadRunning = false
    private var currentEntry: LibraryEntry? = null
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
    private var libraryEntries = emptyList<LibraryEntry>()
    private var pendingDebugGame: String? = null
    private var pendingDebugLaunch: String? = null
    private lateinit var backdrop: View
    private lateinit var drawer: ScrollView
    private lateinit var menuStatus: TextView
    private lateinit var mediaLabel: TextView
    private val menuItems = ArrayList<View>()
    private val menuValues = ArrayList<Pair<TextView, () -> String>>()
    private var selectedMenuIndex = 0
    private var menuOpen = false
    private var userPaused = false
    private var activityVisible = false
    private var integerScaling = true
    private var integerCrop = false
    private var portraitNotchPadding = 0
    private var muted = false
    private var clock = 25
    private var exiting = false
    private var relocating = false
    private var edgeSwipeX: Float? = null
    private var edgeSwipeY = 0f
    private var edgeSwipeConsumed = false
    private var keyboardSwipeX: Float? = null
    private var keyboardSwipeY = 0f
    private var keyboardSwipeConsumed = false
    private var menuSwipeX: Float? = null
    private var menuSwipeY = 0f
    private var menuSwipeConsumed = false
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
        if (routeRgDsToUpperDisplay()) return
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
        loadGraphicsSettings()
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
        gamepadMapper.physicalBindings = physicalControllerBindings()
        gamepadMapper.bindings = globalControllerBindings()
        gamepadMapper.deadZone = preferences.getFloat("controller_dead_zone", 0.35f)
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
        secondaryKeyboard = SecondaryKeyboardDisplay(this, inputRouter,
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
            ::loadControllerBindings, ::saveControllerBindings, ::resetControllerBindings,
            ::physicalControllerBindings, ::savePhysicalControllerBindings,
            ::resetPhysicalControllerBindings,
            { gamepadMapper.deadZone }, { value ->
                gamepadMapper.deadZone = value
                preferences.edit().putFloat("controller_dead_zone", value).apply()
            }, ::applyPauseState, ::showOnScreenControls,
            { onScreenControls.eightWayDpad }, { onScreenControls.eightWayDpad = it })
        libraryScreen = LibraryScreen(this, romLibrary.catalog,
            ::chooseRomFolder, { refreshLibrary(false) }, { refreshLibrary(true) },
            if (resources.getBoolean(R.bool.catalog_art_download_enabled))
                ::downloadMissingImages else null, ::cancelArtworkDownload,
            settingsEntries(), { preferences.getString("last_played_entry", null) },
            ::launchEntry, ::showDetailPreview, ::showGameDetails, ::showLibrarySelection)
        root.addView(libraryScreen, FrameLayout.LayoutParams(-1, -1))
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
            val saved = preferences.getString("rom_tree", null)
            romTree = saved?.let(Uri::parse)
            val setupStep = preferences.getInt("onboarding_step_v1", if (romTree == null) 0 else 2)
            libraryScreen.showFolder(romTree?.let(::folderLabel))
            if (romTree == null) {
                libraryScreen.showStatus("Choose a ROM folder to find disk images and ZIP games")
            } else if (!hasRomGrant(romTree!!)) {
                libraryScreen.showStatus("Folder access expired. Select the ROM folder again.")
            } else {
                libraryEntries = romLibrary.cached(romTree!!)
                libraryScreen.showEntries(libraryEntries)
                if (setupStep >= 2 && !selectPendingDebugGame()) refreshLibrary(false)
            }
            if (setupStep < 2) firstRunSetup.show(if (setupStep == 0)
                FirstRunSetup.Step.ROM_FOLDER else FirstRunSetup.Step.FIRMWARE)
            applyPauseState()
        }
    }

    private fun routeRgDsToUpperDisplay(): Boolean {
        if (!Build.MODEL.equals("RG DS", ignoreCase = true) ||
            intent.getBooleanExtra("kairo98.displayRedirected", false)) return false
        val displays = (getSystemService(DISPLAY_SERVICE) as DisplayManager).displays
        val upper = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY &&
            it.isValid && it.state != Display.STATE_OFF } ?: return false
        @Suppress("DEPRECATION")
        val currentId = windowManager.defaultDisplay.displayId
        if (currentId == upper.displayId) return false
        return try {
            val redirected = Intent(intent).apply {
                setClass(this@MainActivity, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra("kairo98.displayRedirected", true)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(upper.displayId)
            startActivity(redirected, options.toBundle())
            relocating = true
            finish()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
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

        keyboardPanel = Pc98KeyboardPanel(this, inputRouter, ::hideKeyboard)
        root.addView(keyboardPanel, FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM))
        swappedKeyboardPanel = Pc98KeyboardPanel(this, inputRouter, {}, showClose = false,
            onSwap = { secondaryKeyboard.toggleSwap() })
        root.addView(swappedKeyboardPanel, FrameLayout.LayoutParams(-1, -1))

        backdrop = View(this).apply {
            setBackgroundColor(Ui.SCRIM)
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { closeMenu() }
        }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        drawer = ScrollView(this).apply {
            visibility = View.GONE
            elevation = dp(16).toFloat()
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Ui.SURFACE)
        }
        val drawerWidth = minOf(dp(320), resources.displayMetrics.widthPixels - dp(40))
        root.addView(drawer, FrameLayout.LayoutParams(drawerWidth, -1, Gravity.START))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(24))
        }
        drawer.addView(content)

        content.addView(PixelTextView(this).apply {
            text = "KAIRO98"
            scale = 2
            setPadding(dp(12), 0, dp(12), dp(14))
        })
        mediaLabel = Ui.text(this, "", Ui.TITLE, bold = true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), dp(2))
        }
        content.addView(mediaLabel)
        menuStatus = Ui.text(this, "", Ui.SECONDARY, Ui.TEXT_MUTED).apply {
            maxLines = 4
            setPadding(dp(12), 0, dp(12), dp(14))
            contentDescription = "Machine status. Tap for details."
            setOnClickListener {
                showMachineDetails = !showMachineDetails
                text = menuStatusText()
            }
        }
        content.addView(menuStatus)
        val sessionActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(sessionActions, LinearLayout.LayoutParams(-1, dp(66)).apply {
            bottomMargin = dp(8)
        })
        sessionIcon(sessionActions, R.drawable.ic_play, "Resume") {
            userPaused = false
            closeMenu()
        }
        sessionIcon(sessionActions, R.drawable.ic_save, "Save") { showStateSlots(saving = true) }
        sessionIcon(sessionActions, R.drawable.ic_load, "Load") { showStateSlots(saving = false) }
        sessionIcon(sessionActions, R.drawable.ic_restart, "Restart") { confirmRestart() }
        sessionIcon(sessionActions, R.drawable.ic_library, "Library") { showLibrary() }
        section(content, "SESSION")
        menuItem(content, "Pause", { if (userPaused) "On · tap to let the game run again"
            else "Close the menu with the game stopped" }) {
            userPaused = !userPaused
            closeMenu()
        }
        menuItem(content, "Mount", { "Hard disk and floppy images" }) { showMountMenu() }
        menuItem(content, "Exit", { "Stop the machine and close Kairo98" }) { confirmExit() }

        section(content, "SETTINGS")
        settingsEntries().forEach { entry -> menuItem(content, entry.title, entry.value, entry.action) }

        setContentView(root)
        screen.requestFocus()
    }

    private fun section(content: LinearLayout, title: String) {
        content.addView(Ui.sectionLabel(this, title))
    }

    private fun sessionIcon(row: LinearLayout, icon: Int, label: String,
                            action: () -> Unit): View {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            contentDescription = label
            isFocusable = true
            isClickable = true
            background = menuHighlight()
            setOnClickListener { clicked ->
                focusMenuItem(menuItems.indexOf(clicked))
                action()
            }
        }
        button.addView(Ui.icon(this, icon, if (icon == R.drawable.ic_play) Ui.ACCENT else Ui.TEXT))
        button.addView(Ui.text(this, label, Ui.LABEL, Ui.TEXT_MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
        menuItems.add(button)
        return button
    }

    private fun menuHighlight() = Ui.rowBackground(this)

    private fun menuItem(content: LinearLayout, title: String, detail: () -> String,
                         action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(14), dp(9), dp(12), dp(9))
            isFocusable = true
            isClickable = true
            background = menuHighlight()
            setOnClickListener { clicked ->
                focusMenuItem(menuItems.indexOf(clicked))
                action()
            }
        }
        item.addView(TextView(this).apply {
            text = title
            textSize = Ui.BODY
            setTextColor(Ui.TEXT)
        })
        val value = Ui.text(this, detail(), Ui.SECONDARY, Ui.TEXT_MUTED)
        item.addView(value)
        content.addView(item, LinearLayout.LayoutParams(-1, -2))
        menuItems.add(item)
        menuValues.add(value to detail)
    }


    /** One settings list, shown the same way in the game menu and the library menu. */
    private fun settingsEntries() = listOf(
        SettingsEntry("Graphics", { scalingLabel() + if (isPortrait()) " · notch ${portraitNotchPadding} dp" else "" }) { showGraphics() },
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
        menuValues.forEach { (view, value) -> view.text = value() }
        if (::libraryScreen.isInitialized) libraryScreen.refreshSettingValues()
    }

    private fun toggleMute() {
        muted = !muted
        preferences.edit().putBoolean("muted", muted).apply()
        nativeSetMuted(muted)
        refreshSettingValues()
    }

    private fun focusMenuItem(index: Int) {
        if (menuItems.isEmpty()) return
        menuItems[selectedMenuIndex].isSelected = false
        selectedMenuIndex = index.coerceIn(0, menuItems.lastIndex)
        val item = menuItems[selectedMenuIndex]
        item.isSelected = true
        item.requestFocus()
        if (drawer.height > 0 && item.bottom > drawer.scrollY + drawer.height) {
            drawer.smoothScrollTo(0, item.bottom - drawer.height + dp(16))
        } else if (item.top < drawer.scrollY) {
            drawer.smoothScrollTo(0, (item.top - dp(16)).coerceAtLeast(0))
        }
    }

    private fun updateViewport() {
        if (!::root.isInitialized || root.width <= 0 || root.height <= 0) return
        val keyboardHeight = if (::keyboardPanel.isInitialized &&
            keyboardPanel.visibility == View.VISIBLE) keyboardPanel.layoutParams.height else 0
        val portrait = root.height * 4L >= root.width * 5L
        val topPadding = if (portrait) dp(portraitNotchPadding) else 0
        val availableHeight = (root.height - keyboardHeight - topPadding).coerceAtLeast(1)
        val fit = minOf(root.width / 640f, availableHeight / 400f)
        if (fit <= 0f) return
        val scale = if (integerScaling && fit >= 1f) {
            if (integerCrop) ceil(fit) else floor(fit)
        } else fit
        val width = (640 * scale).roundToInt().coerceAtLeast(1)
        val height = (400 * scale).roundToInt().coerceAtLeast(1)
        val params = screen.layoutParams as FrameLayout.LayoutParams
        val top = if (portrait) topPadding
            else ((availableHeight - height) / 2).coerceAtLeast(0)
        if (params.width != width || params.height != height || params.topMargin != top ||
            params.gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL)) {
            screen.layoutParams = FrameLayout.LayoutParams(width, height,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = top }
        }
    }

    private fun openMenu() {
        commandCancelled.set(true)
        releaseInputs()
        hideKeyboard()
        if (menuOpen) return
        menuOpen = true
        onScreenControls.refreshVisibility(false)
        applyPauseState()
        drawer.scrollTo(0, 0)
        focusMenuItem(0)
        refreshSettingValues()
        mediaLabel.text = currentTitle ?: "No disk selected"
        menuStatus.text = if (preparingFont) "Preparing PC-98 font" else menuStatusText()
        backdrop.visibility = View.VISIBLE
        backdrop.alpha = 0f
        drawer.visibility = View.VISIBLE
        drawer.translationX = -drawer.layoutParams.width.toFloat()
        backdrop.animate().alpha(1f).setDuration(180).start()
        drawer.animate().translationX(0f).setDuration(180).start()
        handler.postDelayed({
            if (menuOpen && Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.hide(WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars())
            }
        }, 300)
    }

    private fun closeMenu() {
        if (!menuOpen) return
        menuOpen = false
        applyPauseState()
        screen.requestFocus()
        backdrop.animate().alpha(0f).setDuration(160).withEndAction {
            if (!menuOpen) backdrop.visibility = View.GONE
        }.start()
        drawer.animate().translationX(-drawer.layoutParams.width.toFloat())
            .setDuration(160).withEndAction {
                if (!menuOpen) {
                    drawer.visibility = View.GONE
                }
            }.start()
    }

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
        val info = SecondaryKeyboardDisplay.LibraryInfo(game.title, listOf(fileLabel(entry)) + tags,
            game.description ?: "No description available yet.", null)
        secondaryKeyboard.setLibraryInfo(info)
        val art = game.boxArt ?: game.preview ?: return
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
            if (::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped && showingGuest)
                swappedKeyboardPanel.visibility = View.VISIBLE
            else if (swappedKeyboardPanel.visibility == View.VISIBLE)
                swappedKeyboardPanel.close()
        }
        if (::secondaryKeyboard.isInitialized) secondaryKeyboard.setAppearance(
            showingGuest,
            if (::firstRunSetup.isInitialized && firstRunSetup.isOpen) Ui.BG
                else Color.BLACK)
        nativePause(userPaused || menuOpen || libraryVisible || !activityVisible || preparingFont ||
            (::controllerEditor.isInitialized && controllerEditor.isOpen) || editingControls)
        if (::onScreenControls.isInitialized) onScreenControls.refreshVisibility(
            !libraryVisible && !menuOpen && !editingControls && activityVisible &&
                !preparingFont && keyboardPanel.visibility != View.VISIBLE &&
                !(::secondaryKeyboard.isInitialized && secondaryKeyboard.swapped) &&
                !(::controllerEditor.isInitialized && controllerEditor.isOpen))
    }

    private fun hasRomGrant(uri: Uri) = contentResolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission
    }

    private fun folderLabel(uri: Uri): String = try {
        "ROM folder · " + DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/')
    } catch (_: Exception) { "ROM folder selected" }

    private fun chooseRomFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, ROM_FOLDER_REQUEST)
    }

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

    private fun refreshLibrary(forceHash: Boolean) {
        val tree = romTree ?: run {
            libraryScreen.showStatus("Select a ROM folder first")
            return
        }
        if (!hasRomGrant(tree)) {
            libraryScreen.showStatus("Folder access expired. Select the ROM folder again.")
            return
        }
        scanCancelled.set(true)
        val cancelled = AtomicBoolean(false)
        scanCancelled = cancelled
        libraryScreen.showStatus(if (forceHash) "Rehashing ROM folder…" else "Scanning ROM folder…")
        Thread {
            try {
                val entries = romLibrary.scan(tree, forceHash, cancelled) { message ->
                    runOnUiThread { if (!cancelled.get()) libraryScreen.showStatus(message) }
                }
                runOnUiThread {
                    if (!cancelled.get()) {
                        libraryEntries = entries
                        libraryScreen.showEntries(entries)
                        selectPendingDebugGame()
                        pendingDebugLaunch?.let { query ->
                            android.util.Log.w("Kairo98", "ADB game not found or ambiguous: $query")
                            pendingDebugLaunch = null
                        }
                        val errors = entries.count { it.error != null }
                        libraryScreen.showStatus("${entries.count { it.playable }} games" +
                            (if (errors == 0) "" else " · $errors unreadable") +
                            " · ${romLibrary.hashCount} hashes this scan")
                    }
                }
            } catch (_: java.util.concurrent.CancellationException) {
            } catch (error: Exception) {
                runOnUiThread {
                    if (!cancelled.get()) libraryScreen.showStatus(
                        "Scan failed: ${error.message ?: "Unknown error"}. Select folder or refresh.")
                }
            }
        }.start()
    }

    private fun cancelArtworkDownload() { artworkDownloadCancelled.set(true) }

    private fun downloadMissingImages() {
        if (artworkDownloadRunning) {
            toast("Images are already downloading")
            return
        }
        val entries = libraryEntries.toList()
        if (entries.none { it.playable }) {
            toast("Add games to the library first")
            return
        }
        artworkDownloadRunning = true
        val cancelled = AtomicBoolean(false)
        artworkDownloadCancelled = cancelled
        libraryScreen.showArtworkProgress(0, 0, 0)
        Thread {
            var completed = 0
            var downloaded = 0
            var failures = 0
            var total = 0
            var errorMessage: String? = null
            try {
                val sources = romLibrary.catalog.missingArtworkFor(entries)
                total = sources.size
                runOnUiThread {
                    if (!isDestroyed) libraryScreen.showArtworkProgress(0, total, 0)
                }
                for (source in sources) {
                    if (cancelled.get()) break
                    try {
                        romLibrary.catalog.downloadArtwork(source, cancelled)
                        downloaded++
                    } catch (_: CancellationException) {
                        break
                    } catch (error: Exception) {
                        failures++
                        android.util.Log.w("Kairo98", "Artwork download failed: ${source.path}", error)
                    }
                    completed++
                    val progress = completed
                    val failed = failures
                    runOnUiThread {
                        if (!isDestroyed) libraryScreen.showArtworkProgress(progress, total, failed)
                    }
                }
            } catch (error: Exception) {
                errorMessage = error.message ?: "Unknown error"
            }
            val result = when {
                errorMessage != null -> "Image download failed: $errorMessage"
                total == 0 -> "No missing catalog images for this library"
                cancelled.get() -> "Image download stopped · $downloaded saved"
                failures == 0 -> "Downloaded $downloaded images"
                else -> "Downloaded $downloaded images · $failures failed. Tap again to retry."
            }
            runOnUiThread {
                artworkDownloadRunning = false
                if (!isDestroyed) {
                    libraryScreen.refreshArtwork()
                    libraryScreen.finishArtworkDownload(result)
                }
            }
        }.start()
    }

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

    private fun launchEntry(entry: LibraryEntry) {
        commandCancelled.set(true)
        releaseInputs()
        if (!entry.playable) {
            toast(entry.error ?: "Refresh this entry before playing")
            return
        }
        if (romTree == null || !hasRomGrant(romTree!!)) {
            libraryScreen.showStatus("Folder access expired. Select the ROM folder again.")
            return
        }
        val game = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
        debugStartupOption?.let { requested ->
            val selected = game.startupChoices.mapNotNull { choice ->
                choice.options.firstOrNull { it.id == requested }?.let { choice to it }
            }
            if (selected.size == game.startupChoices.size) {
                startEntry(entry, game, selected)
                return
            }
            File(filesDir, "performance-auto.txt").writeText(
                "state\terror\tstartup option $requested unavailable\n")
            return
        }
        if (game.startupChoices.isNotEmpty() && !skipDebugChoices) {
            chooseStartupOptions(entry, game, 0, emptyList())
        } else startEntry(entry, game, emptyList())
    }

    private fun chooseStartupOptions(entry: LibraryEntry, game: GameCatalog.Game, index: Int,
                                     selected: List<Pair<GameCatalog.StartupChoice,
                                         GameCatalog.StartupOption>>) {
        if (index == game.startupChoices.size) {
            startEntry(entry, game, selected)
            return
        }
        val choice = game.startupChoices[index]
        AlertDialog.Builder(this).setTitle(choice.title)
            .setItems(choice.options.map { it.label }.toTypedArray()) { _, which ->
                chooseStartupOptions(entry, game, index + 1,
                    selected + (choice to choice.options[which]))
            }
            .setNeutralButton("Play manually") { _, _ -> startEntry(entry, game, selected) }
            .setNegativeButton("Cancel", null).showStyled()
    }

    private fun startEntry(entry: LibraryEntry, game: GameCatalog.Game,
                           choices: List<Pair<GameCatalog.StartupChoice,
                               GameCatalog.StartupOption>>) {
        val generation = ++startGeneration
        val cancelled = AtomicBoolean(false)
        preparingFont = true
        applyPauseState()
        libraryScreen.showStatus("Preparing ${entry.displayName}…")
        Thread {
            val result = try {
                val media = bootMediaFor(entry, libraryEntries, game.requiredBootFloppyId)
                val swapSources = game.diskSwaps.map { rule ->
                    rule.contentId to requiredFloppyFor(rule.contentId, entry, libraryEntries)
                }.toMap()
                val floppyB = game.initialFloppyBId?.let { id ->
                    requiredFloppyFor(id, entry, libraryEntries)
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
                        preferences.edit().putString("last_played_entry", entry.id).apply()
                        currentDisk = result.disk
                        currentIsFloppy = result.bootFloppy == null && entry.isFloppy
                        mountedFloppies[0] = result.bootFloppy?.displayName
                            ?: if (entry.isFloppy) entry.displayName else null
                        mountedFloppies[1] = result.floppyB?.displayName
                        currentTitle = game.title
                        currentGame = game
                        selectedStartup = choices
                        inputModeDecider.reset()
                        gamepadMapper.bindings = effectiveControllerBindings(game)
                        userPaused = false
                        menuOpen = false
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

    private fun configuredInputMode(): InputModeDecider.Mode =
        currentGame?.inputMode?.let(InputModeDecider::parse) ?: globalInputMode

    private fun showKeyboard() {
        if (::secondaryKeyboard.isInitialized && secondaryKeyboard.isKeyboardVisible) return
        if (keyboardPanel.visibility == View.VISIBLE) return
        keyboardPanel.visibility = View.VISIBLE
        updateViewport()
        applyPauseState()
        handler.postDelayed({
            if (keyboardPanel.visibility == View.VISIBLE && Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.hide(WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars())
            }
        }, 350)
    }

    private fun hideKeyboard() {
        if (!::keyboardPanel.isInitialized) return
        keyboardPanel.close()
        updateViewport()
        if (::onScreenControls.isInitialized) applyPauseState()
        screen.requestFocus()
    }

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
        data class Row(val title: String, val value: String, val needsHash: Boolean,
                       val destructive: Boolean = false, val action: () -> Unit)
        val controllerSource = if (game.controllerBindings == null ||
            (game.controllerBindings == "[]" && !game.overriddenFields.contains("controller"))) "Global"
            else source("controller", fallback = "Global")
        val inputMode = InputModeDecider.parse(game.inputMode ?: InputModeDecider.storageValue(globalInputMode))
        val touch = game.inputTouch ?: touchStorage(globalTouchDirect)
        val sections = listOf(
            "CONTROLS" to listOf(
                Row("Touch input", "${inputMode.name.lowercase().replaceFirstChar(Char::uppercase)} · " +
                    "${if (touch == "direct") "direct tap" else "touchpad"} · ${source("input")}", true) {
                    showInputModeChoices(entry) },
                Row("Controller mapping", "${effectiveControllerBindings(game).size} bindings · $controllerSource", true) {
                    showControllerBindings(entry) }),
            "MACHINE" to listOf(
                Row("Machine clock", "${game.baseClockTenthsMHz?.let { "${it / 10.0} MHz" } ?: "App default"} · ${source("machine")}", true) {
                    editGameClock(entry) },
                Row("GDC clock", "${game.gdcClockTenthsMHz?.let { "${it / 10.0} MHz" } ?: "5.0 MHz (app default)"} · ${source("machine")}", true) {
                    editGameGdcClock(entry) },
                Row("CPU speed", "${cpuSpeedLabel(game)} · ${source("machine")}", true) {
                    editGameCpuSpeed(entry) },
                Row("Startup command", "${game.launchCommand ?: "None"} · ${source("launch")}", true) {
                    editGameText(entry, "launch", game.launchCommand ?: "") }),
            "LIBRARY" to listOf(
                Row("Title", "${game.title} · ${source("title", fallback = "Filename")}", true) {
                    editGameText(entry, "title", game.title) },
                Row("Box art", "${if (game.boxArt == null) "None" else "Available"} · ${source("artwork", "boxArt")}", true) {
                    editGameArt(entry, "boxArt", game.boxArt ?: "") },
                Row("Screenshot", "${if (game.preview == null) "None" else "Available"} · ${source("artwork", "preview")}", true) {
                    editGameArt(entry, "preview", game.preview ?: "") },
                Row("View screenshot", if (game.preview == null) "No screenshot available" else "Open full size", false) {
                    showGamePreview(entry) },
                Row("File information", "Path, ZIP entry, and content ID", false) {
                    AlertDialog.Builder(this).setTitle("File information")
                        .setMessage("${entry.path}${entry.zipEntry?.let { "\n$it" } ?: ""}\n\n" +
                            (id ?: entry.error ?: "Not hashed"))
                        .setPositiveButton("Close", null).showStyled() }),
            "" to listOf(
                Row("Reset all custom settings", "Restore this game's catalog values", true, destructive = true) {
                    AlertDialog.Builder(this).setTitle("Reset all settings?")
                        .setMessage("Restore this game's current catalog defaults.")
                        .setPositiveButton("Reset") { _, _ -> saveGameSetting(entry) {
                            catalog.resetOverride(id!!)
                        } }.setNegativeButton("Cancel", null).showStyled() }))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        val builder = AlertDialog.Builder(this).setTitle(game.title)
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton("Close", null)
        if (entry.playable) builder.setPositiveButton("Play") { _, _ -> launchEntry(entry) }
        val dialog = builder.showStyled()
        sections.forEach { (heading, rows) ->
            list.addView(TextView(this).apply {
                text = heading
                textSize = Ui.LABEL
                letterSpacing = 0.14f
                setTextColor(Ui.ACCENT)
                setPadding(dp(12), dp(if (heading.isEmpty()) 6 else 14), dp(12), dp(4))
            })
            rows.forEach { row ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    isFocusable = true
                    isClickable = true
                    background = menuHighlight()
                    setOnClickListener {
                        if (id == null && row.needsHash) {
                            toast("This file needs a successful hash before settings can be saved")
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        row.action()
                    }
                }
                item.addView(TextView(this).apply {
                    text = row.title
                    textSize = Ui.BODY
                    setTextColor(if (row.destructive) Ui.DANGER else Color.WHITE)
                })
                item.addView(TextView(this).apply {
                    text = row.value
                    textSize = Ui.LABEL
                    setTextColor(Ui.TEXT_MUTED)
                })
                list.addView(item, LinearLayout.LayoutParams(-1, -2))
            }
        }
        (0 until list.childCount).map(list::getChildAt).firstOrNull { it.isFocusable }?.requestFocus()
    }

    private fun showGamePreview(entry: LibraryEntry) = showGameArt(entry, "preview", true)

    private fun showDetailPreview(entry: LibraryEntry) = showGameArt(entry, "preview", false)

    private fun showGameArt(entry: LibraryEntry, kind: String, returnToSettings: Boolean) {
        val game = romLibrary.catalog.resolve(entry.contentId ?: "", entry.displayName)
        val path = (if (kind == "preview") game.preview else game.boxArt)
            ?: run { toast("No ${if (kind == "preview") "screenshot" else "box art"} available"); return }
        val urlKind = if (kind == "preview") "previewUrl" else "boxArtUrl"
        val url = (if (kind == "preview") game.previewUrl else game.boxArtUrl)
            ?.takeIf { entry.contentId?.let { id ->
                romLibrary.catalog.sourceOf(id, "artwork", kind) ==
                    romLibrary.catalog.sourceOf(id, "artwork", urlKind)
            } == true }
        val bitmap = try {
            romLibrary.catalog.openArtwork(path).use { BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options()) }
        } catch (_: Exception) { null }
        if (bitmap == null) {
            toast("Image unavailable")
            return
        }
        val view = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(view)
        val hint = TextView(this).apply {
            text = if (url == null) "" else "Tap image to load a larger version"
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        content.addView(hint)
        val dialog = AlertDialog.Builder(this).setView(content)
            .setPositiveButton("Done") { _, _ -> if (returnToSettings) showGameDetails(entry) }
            .showStyled()
        if (url != null) view.setOnClickListener {
            view.isEnabled = false
            hint.text = "Loading larger image…"
            Thread {
                val larger = try { fetchLargerArt(url) } catch (_: Exception) { null }
                runOnUiThread {
                    if (dialog.isShowing) {
                        if (larger == null) {
                            hint.text = "Could not load larger image"
                            view.isEnabled = true
                        }
                        else {
                            view.setImageBitmap(larger)
                            hint.text = "Larger image loaded"
                        }
                    }
                }
            }.start()
        }
    }

    private fun fetchLargerArt(url: String): android.graphics.Bitmap? {
        if (!GameCatalog.validImageUrl(url)) return null
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                connection.contentLengthLong > 16L * 1024 * 1024) {
                return null
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 16 * 1024 * 1024) {
                        return null
                    }
                    bytes.write(buffer, 0, count)
                }
            }
            val data = bytes.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth < 1 || bounds.outHeight < 1 ||
                bounds.outWidth.toLong() * bounds.outHeight > 32_000_000L) {
                return null
            }
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (bounds.outWidth / options.inSampleSize > 2048 ||
                bounds.outHeight / options.inSampleSize > 2048) options.inSampleSize *= 2
            return BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } finally { connection.disconnect() }
    }

    private fun saveGameSetting(entry: LibraryEntry, action: () -> Unit) {
        try {
            action()
            libraryScreen.showEntries(libraryEntries)
            toast("Saved. Machine changes apply on next launch or restart.")
        } catch (error: Exception) {
            toast("Could not save: ${error.message ?: "Invalid value"}")
        }
        showGameDetails(entry)
    }

    private fun editGameText(entry: LibraryEntry, field: String, current: String) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(current)
            setSelection(text.length)
            hint = if (field == "title") "Game title" else "DOS command, such as GAME"
        }
        AlertDialog.Builder(this).setTitle(if (field == "title") "Game title" else "Guest command")
            .setView(input)
            .setPositiveButton("Save") { _, _ -> saveGameSetting(entry) {
                val value = input.text.toString().trim()
                if (field == "title") romLibrary.catalog.setOverride(entry.contentId!!, field, value)
                else if (value.isEmpty()) romLibrary.catalog.resetOverride(entry.contentId!!, field)
                else romLibrary.catalog.setOverride(entry.contentId!!, field,
                    JSONObject().put("type", "guestCommand").put("text", value)
                        .put("ready", "dosPrompt").put("timeoutMs", 30000))
            } }.setNeutralButton("Reset") { _, _ -> saveGameSetting(entry) {
                romLibrary.catalog.resetOverride(entry.contentId!!, field)
            } }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    private fun editGameClock(entry: LibraryEntry) {
        val current = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
        AlertDialog.Builder(this).setTitle("Machine clock")
            .setSingleChoiceItems(arrayOf("Reset machine settings", "2 MHz", "2.5 MHz"),
                when (current.baseClockTenthsMHz) { 20 -> 1; 25 -> 2; else -> 0 }) { dialog, which ->
                dialog.dismiss()
                saveGameSetting(entry) {
                    if (which == 0) romLibrary.catalog.resetOverride(entry.contentId!!, "machine")
                    else romLibrary.catalog.setOverride(entry.contentId!!, "machine",
                        machineSettings(current).put("baseClockTenthsMHz", if (which == 1) 20 else 25))
                }
            }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    /** The game's current machine settings, so editing one keeps the others. */
    private fun machineSettings(game: GameCatalog.Game) = JSONObject().apply {
        game.baseClockTenthsMHz?.let { put("baseClockTenthsMHz", it) }
        game.gdcClockTenthsMHz?.let { put("gdcClockTenthsMHz", it) }
        game.cpuMultiple?.let { put("cpuMultiple", it) }
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
        val labels = listOf("App default (%.1f MHz)".format(cpuMhz(current, DEFAULT_CPU_MULTIPLE))) +
            multiples.map { "%.1f MHz (x%d)".format(cpuMhz(current, it), it) }
        val selected = current.cpuMultiple?.let { multiples.indexOf(it) + 1 } ?: 0
        AlertDialog.Builder(this).setTitle("CPU speed")
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                saveGameSetting(entry) {
                    val settings = machineSettings(current).apply {
                        remove("cpuMultiple")
                        if (which > 0) put("cpuMultiple", multiples[which - 1])
                    }
                    if (settings.length() == 0) romLibrary.catalog.resetOverride(entry.contentId!!, "machine")
                    else romLibrary.catalog.setOverride(entry.contentId!!, "machine", settings)
                }
            }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    private fun editGameGdcClock(entry: LibraryEntry) {
        val current = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
        AlertDialog.Builder(this).setTitle("GDC clock")
            .setSingleChoiceItems(arrayOf("Reset machine settings", "2.5 MHz", "5 MHz"),
                when (current.gdcClockTenthsMHz) { 25 -> 1; 50 -> 2; else -> 0 }) { dialog, which ->
                dialog.dismiss()
                saveGameSetting(entry) {
                    if (which == 0) romLibrary.catalog.resetOverride(entry.contentId!!, "machine")
                    else romLibrary.catalog.setOverride(entry.contentId!!, "machine",
                        machineSettings(current).put("gdcClockTenthsMHz", if (which == 1) 25 else 50))
                }
            }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    private fun editGameArt(entry: LibraryEntry, kind: String, current: String) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(current)
            setSelection(text.length)
            hint = "art/example.webp"
        }
        AlertDialog.Builder(this).setTitle(if (kind == "preview") "Preview art" else "Box art")
            .setMessage("Use a packaged art path. Missing art falls back to the game title.")
            .setView(input)
            .setPositiveButton("Save") { _, _ -> saveGameSetting(entry) {
                val path = input.text.toString().trim()
                if (path.isEmpty()) romLibrary.catalog.resetArtworkOverride(entry.contentId!!, kind)
                else romLibrary.catalog.setArtworkOverride(entry.contentId!!, kind, path)
            } }.setNeutralButton("Reset ${if (kind == "preview") "preview" else "box art"}") { _, _ -> saveGameSetting(entry) {
                romLibrary.catalog.resetArtworkOverride(entry.contentId!!, kind)
            } }.setNegativeButton("Cancel") { _, _ -> showGameDetails(entry) }.showStyled()
    }

    private fun restartMachine() {
        commandCancelled.set(true)
        releaseInputs()
        currentEntry?.let { entry ->
            userPaused = false
            closeMenu()
            launchEntry(entry)
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

    private fun scalingLabel() = when {
        !integerScaling -> "Fit display"
        integerCrop -> "Integer crop"
        else -> "Integer full image"
    }

    private fun showGraphics() {
        val orientation = if (isPortrait()) "portrait" else "landscape"
        val scaling = scalingLabel()
        val items = if (orientation == "portrait")
            arrayOf("Scaling  ·  $scaling", "Notch padding  ·  $portraitNotchPadding dp")
        else arrayOf("Scaling  ·  $scaling")
        AlertDialog.Builder(this).setTitle("Graphics · $orientation")
            .setItems(items) { _, which ->
                if (which == 0) showScalingChoices(orientation) else showNotchPadding()
            }.setNegativeButton("Close", null).showStyled()
    }

    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun loadGraphicsSettings() {
        val suffix = if (isPortrait()) "portrait" else "landscape"
        integerScaling = preferences.getBoolean("integer_scaling_$suffix",
            preferences.getBoolean("integer_scaling", true))
        integerCrop = preferences.getBoolean("integer_crop_$suffix",
            preferences.getBoolean("integer_crop", false))
        portraitNotchPadding = preferences.getInt("portrait_notch_padding", 0).coerceIn(0, 240)
    }

    private fun showScalingChoices(orientation: String) {
        val options = arrayOf(
            "Integer  ·  full image (default)",
            "Integer  ·  crop edges",
            "Fit display  ·  fractional scale"
        )
        AlertDialog.Builder(this).setTitle("Scaling · $orientation")
            .setSingleChoiceItems(options, if (!integerScaling) 2 else if (integerCrop) 1 else 0) { dialog, which ->
                integerScaling = which != 2
                integerCrop = which == 1
                preferences.edit()
                    .putBoolean("integer_scaling_$orientation", integerScaling)
                    .putBoolean("integer_crop_$orientation", integerCrop)
                    .apply()
                updateViewport()
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).showStyled()
    }

    private fun showNotchPadding() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(portraitNotchPadding.toString())
            selectAll()
        }
        AlertDialog.Builder(this).setTitle("Portrait notch padding (dp)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(0, 240)
                if (value == null) { toast("Enter a number from 0 to 240"); return@setPositiveButton }
                portraitNotchPadding = value
                preferences.edit().putInt("portrait_notch_padding", value).apply()
                updateViewport()
            }.setNegativeButton("Cancel", null).showStyled()
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

    private fun directTapActive(): Boolean =
        (currentGame?.inputTouch ?: touchStorage(globalTouchDirect)) == "direct"

    private fun touchInputLabel(): String {
        val game = currentGame.takeIf { !libraryVisible }
        val mode = game?.inputMode?.let(InputModeDecider::parse) ?: globalInputMode
        val direct = (game?.inputTouch ?: touchStorage(globalTouchDirect)) == "direct"
        val modeLabel = when (mode) {
            InputModeDecider.Mode.AUTO -> "Auto"
            InputModeDecider.Mode.KEYBOARD -> "Keyboard"
            InputModeDecider.Mode.MOUSE -> "Mouse"
        }
        return "$modeLabel · ${if (direct) "direct tap" else "touchpad"}" +
            if (game != null && game.overriddenFields.contains("input")) " · this game" else ""
    }

    private fun showInputModeChoices(entry: LibraryEntry?) {
        val game = entry?.contentId?.let { romLibrary.catalog.resolve(it, entry.displayName) }
        val modes = InputModeDecider.Mode.entries
        val mode = if (entry == null) globalInputMode
            else InputModeDecider.parse(game?.inputMode ?: InputModeDecider.storageValue(globalInputMode))
        val direct = if (entry == null) globalTouchDirect
            else (game?.inputTouch ?: touchStorage(globalTouchDirect)) == "direct"
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        fun heading(text: String) = Ui.sectionLabel(this, text).apply { setPadding(0, dp(14), 0, dp(4)) }
        fun group(labels: List<String>, selected: Int) = RadioGroup(this).apply {
            labels.forEachIndexed { index, label ->
                addView(RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    text = label
                    textSize = Ui.SECONDARY
                    isChecked = index == selected
                })
            }
        }
        body.addView(heading("SCREEN TAP"))
        val modeGroup = group(listOf("Auto (follows what the game reads)",
            "Keyboard (tap opens the PC-98 keyboard)", "Mouse"), modes.indexOf(mode))
        body.addView(modeGroup)
        body.addView(heading("MOUSE TOUCH"))
        val touchGroup = group(listOf("Touchpad (drag to move, tap to click)",
            "Direct tap (click where you touch)"), if (direct) 1 else 0)
        body.addView(touchGroup)
        body.addView(TextView(this).apply {
            text = "Direct tap lands on the touched point in games that move the cursor one pixel " +
                "per mouse count and stop it at the screen's top-left edge."
            textSize = Ui.LABEL
            setTextColor(Ui.TEXT_MUTED)
            setPadding(0, dp(6), 0, dp(8))
        })
        fun checkedIndex(group: RadioGroup) =
            (0 until group.childCount).indexOfFirst { (group.getChildAt(it) as RadioButton).isChecked }
        val builder = AlertDialog.Builder(this)
            .setTitle(if (entry == null) "Touch input" else "Touch input for ${game?.title}")
            .setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Save") { _, _ ->
                val chosenMode = modes[checkedIndex(modeGroup).coerceAtLeast(0)]
                val chosenDirect = checkedIndex(touchGroup) == 1
                if (entry == null) {
                    globalInputMode = chosenMode
                    globalTouchDirect = chosenDirect
                    preferences.edit()
                        .putString("input_mode", InputModeDecider.storageValue(chosenMode))
                        .putString("touch_mouse", touchStorage(chosenDirect)).apply()
                } else {
                    romLibrary.catalog.setOverride(entry.contentId!!, "input", JSONObject()
                        .put("mode", InputModeDecider.storageValue(chosenMode))
                        .put("touch", touchStorage(chosenDirect)))
                    if (currentEntry?.contentId == entry.contentId)
                        currentGame = romLibrary.catalog.resolve(entry.contentId, entry.displayName)
                }
            }
            .setNegativeButton("Cancel", null)
        if (entry != null) builder.setNeutralButton("Use app default") { _, _ ->
            romLibrary.catalog.resetOverride(entry.contentId!!, "input")
            if (currentEntry?.contentId == entry.contentId)
                currentGame = romLibrary.catalog.resolve(entry.contentId, entry.displayName)
        }
        builder.showStyled()
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

    private fun globalControllerBindings() =
        ControllerBindings.parse(preferences.getString("controller_global_v1", null))

    private fun physicalControllerBindings() =
        PhysicalControllerBindings.parse(preferences.getString("controller_physical_v1", null))

    private fun savePhysicalControllerBindings(bindings: List<PhysicalControllerBinding>) {
        preferences.edit().putString("controller_physical_v1",
            PhysicalControllerBindings.toJson(bindings).toString()).apply()
        gamepadMapper.physicalBindings = bindings
    }

    private fun resetPhysicalControllerBindings() {
        preferences.edit().remove("controller_physical_v1").apply()
        gamepadMapper.physicalBindings = physicalControllerBindings()
    }

    private fun effectiveControllerBindings(game: GameCatalog.Game?): List<ControllerBinding> {
        val configured = game?.controllerBindings
        return if (game?.overriddenFields?.contains("controller") == true ||
            (configured != null && (configured != "[]" || game.controllerProfile == "custom-v1")))
            ControllerBindings.parse(configured)
        else globalControllerBindings()
    }

    private fun releaseInputs() {
        debugAutoAdvance?.cancel()
        debugAutoAdvance = null
        gamepadMapper.releaseAll()
        inputRouter.releaseAll()
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
        }
    }

    private fun showControllerScope() {
        closeMenu()
        releaseInputs()
        hideKeyboard()
        controllerEditor.show(currentEntry?.takeIf { !libraryVisible && it.contentId != null })
    }

    private fun showOnScreenControls() {
        if (menuOpen) closeMenu()
        releaseInputs()
        hideKeyboard()
        onScreenControls.show()
    }

    private fun showControllerBindings(entry: LibraryEntry) {
        if (entry.contentId == null) { toast("Hash this game before editing its controls"); return }
        releaseInputs()
        controllerEditor.show(entry)
    }

    private fun loadControllerBindings(entry: LibraryEntry?): List<ControllerBinding> =
        if (entry == null) globalControllerBindings()
        else effectiveControllerBindings(romLibrary.catalog.resolve(entry.contentId!!, entry.displayName))

    private fun saveControllerBindings(entry: LibraryEntry?, bindings: List<ControllerBinding>) {
        val json = ControllerBindings.toJson(bindings)
        if (entry == null) preferences.edit().putString("controller_global_v1", json.toString()).apply()
        else romLibrary.catalog.setOverride(entry.contentId!!, "controller",
            JSONObject().put("profile", "custom-v1").put("bindings", json))
        refreshControllerBindings(entry)
    }

    private fun resetControllerBindings(entry: LibraryEntry?) {
        if (entry == null) preferences.edit().remove("controller_global_v1").apply()
        else romLibrary.catalog.resetOverride(entry.contentId!!, "controller")
        refreshControllerBindings(entry)
    }

    private fun refreshControllerBindings(entry: LibraryEntry?) {
        if (entry != null && currentEntry?.contentId != entry.contentId) return
        currentEntry?.let { currentGame = romLibrary.catalog.resolve(it.contentId!!, it.displayName) }
        gamepadMapper.bindings = effectiveControllerBindings(currentGame)
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
                            launchEntry(entry)
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
        loadGraphicsSettings()
        root.post { updateViewport() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (relocating) return
        if (!hasFocus) {
            commandCancelled.set(true)
            releaseInputs()
        }
    }

    override fun onDestroy() {
        if (relocating) { super.onDestroy(); return }
        startGeneration++
        scanCancelled.set(true)
        artworkDownloadCancelled.set(true)
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
        if (requestCode == ROM_FOLDER_REQUEST) {
            if (resultCode == RESULT_OK && data?.data != null) {
                val tree = data.data ?: return
                try {
                    contentResolver.takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    romTree = tree
                    preferences.edit().putString("rom_tree", tree.toString()).apply()
                    libraryScreen.showFolder(folderLabel(tree))
                    libraryScreen.showEntries(emptyList())
                    if (firstRunSetup.isChoosingRomFolder) advanceFirstRunFirmware()
                    else refreshLibrary(false)
                } catch (error: Exception) {
                    val message = "Cannot keep folder access: ${error.message}"
                    libraryScreen.showStatus(message)
                    if (firstRunSetup.isOpen) toast(message)
                }
            } else if (romTree == null) {
                libraryScreen.showStatus("Choose a ROM folder when you're ready")
            }
            return
        }
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
                    inputModeDecider.reset()
                    gamepadMapper.bindings = globalControllerBindings()
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

    private fun uiControl(event: KeyEvent): String? {
        gamepadMapper.controlForButton(event)?.let { return it }
        // D-pad keys mean the same thing on menus from any source, including a built-in gamepad.
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> return "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> return "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> return "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> return "right"
            KeyEvent.KEYCODE_DPAD_CENTER -> return "a"
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> return "b"
            KeyEvent.KEYCODE_MENU -> return "menu"
        }
        if (KeyEvent.isGamepadButton(event.keyCode) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return null
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_ENTER -> "a"
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> "b"
            KeyEvent.KEYCODE_MENU -> "menu"
            else -> null
        }
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
            val control = uiControl(event)
            if (libraryScreen.detailOpen) {
                if (control == "a" || control == "b" || control == "menu") {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        when (control) {
                            "a" -> libraryScreen.activateDetail()
                            "menu" -> libraryScreen.detailsSelection()
                            else -> libraryScreen.closeDetail()
                        }
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            if (libraryScreen.actionsOpen) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (control) {
                        "down" -> libraryScreen.moveActionSelection(1)
                        "up" -> libraryScreen.moveActionSelection(-1)
                        "a" -> if (event.repeatCount == 0) libraryScreen.activateAction()
                        "b", "menu" -> libraryScreen.closeActions()
                    }
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (control) {
                    "down" -> libraryScreen.moveSelection(1)
                    "up" -> libraryScreen.moveSelection(-1)
                    // The list is one column; sideways must not wander to the header buttons.
                    "left", "right" -> {}
                    "a" -> if (event.repeatCount == 0) libraryScreen.activateSelection()
                    "menu" -> if (event.repeatCount == 0) libraryScreen.openActions()
                    "b" -> if (event.repeatCount == 0) onBackPressed()
                    else -> return super.dispatchKeyEvent(event)
                }
            }
            return true
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
            val control = uiControl(event)
            // The session row reads left to right, the list top to bottom; both walk the same order.
            if (control == "down" || control == "up" || control == "left" || control == "right") {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    focusMenuItem(selectedMenuIndex +
                        if (control == "down" || control == "right") 1 else -1)
                }
                return true
            }
            if (control == "b") {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) closeMenu()
                return true
            }
            if (control == "a") {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    menuItems[selectedMenuIndex].performClick()
                }
                return true
            }
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::firstRunSetup.isInitialized && firstRunSetup.isOpen)
            return super.dispatchTouchEvent(event)
        if (::onScreenControls.isInitialized && onScreenControls.isOpen)
            return super.dispatchTouchEvent(event)
        if (::controllerEditor.isInitialized && controllerEditor.isOpen)
            return super.dispatchTouchEvent(event)
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return super.dispatchTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            menuSwipeConsumed = false
            keyboardSwipeConsumed = false
        }
        if (keyboardSwipeConsumed) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL) keyboardSwipeConsumed = false
            return true
        }
        if (menuSwipeConsumed) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL) menuSwipeConsumed = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeSwipeConsumed = false
                if (menuOpen) {
                    menuSwipeX = event.x
                    menuSwipeY = event.y
                }
                if (!menuOpen && event.x <= dp(28) &&
                    !onScreenControls.hitTest(event.x, event.y)) {
                    edgeSwipeX = event.x
                    edgeSwipeY = event.y
                    return true
                }
                if (!menuOpen && !libraryVisible && event.x >= root.width - dp(28) &&
                    !onScreenControls.hitTest(event.x, event.y)) {
                    keyboardSwipeX = event.x
                    keyboardSwipeY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val menuStart = menuSwipeX
                if (menuOpen && menuStart != null) {
                    val horizontal = event.x - menuStart
                    if (horizontal <= -dp(72) &&
                        -horizontal > abs(event.y - menuSwipeY) * 1.3f) {
                        menuSwipeX = null
                        menuSwipeConsumed = true
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        closeMenu()
                        return true
                    }
                }
                val start = edgeSwipeX
                if (start != null) {
                    val horizontal = event.x - start
                    if (horizontal >= dp(72) && horizontal > abs(event.y - edgeSwipeY) * 1.3f) {
                        edgeSwipeX = null
                        edgeSwipeConsumed = true
                        openMenu()
                    }
                    return true
                }
                val keyboardStart = keyboardSwipeX
                if (keyboardStart != null) {
                    val horizontal = event.x - keyboardStart
                    if (horizontal <= -dp(72) &&
                        -horizontal > abs(event.y - keyboardSwipeY) * 1.3f) {
                        keyboardSwipeX = null
                        keyboardSwipeConsumed = true
                        showKeyboard()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                menuSwipeX = null
                if (keyboardSwipeX != null || keyboardSwipeConsumed) {
                    keyboardSwipeX = null
                    keyboardSwipeConsumed = false
                    return true
                }
                if (edgeSwipeX != null || edgeSwipeConsumed) {
                    edgeSwipeX = null
                    edgeSwipeConsumed = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
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
