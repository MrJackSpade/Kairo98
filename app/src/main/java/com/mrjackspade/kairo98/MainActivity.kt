package com.mrjackspade.kairo98

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
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
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity(), SurfaceHolder.Callback {
    private external fun nativeStart(path: String?, fontPath: String, biosDir: String,
                                     fontBitmap: Boolean, mhzTimesTen: Int, gdcMhzTimesTen: Int,
                                     floppy: Boolean): Boolean
    private external fun nativeFloppy(drive: Int, path: String?): Boolean
    private external fun nativeStop()
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeClock(mhzTimesTen: Int)
    private external fun nativeKey(scanCode: Int, down: Boolean)
    private external fun nativeMouseMove(dx: Int, dy: Int)
    private external fun nativeMouseButton(button: Int, down: Boolean)
    private external fun nativeJoystick(control: Int, down: Boolean)
    private external fun nativeInputTelemetry(): LongArray
    private external fun nativeStatus(): String
    private external fun nativeDosPromptReady(): Boolean
    private external fun nativeSetSurface(surface: Surface?, width: Int, height: Int)
    private external fun nativeSetMuted(muted: Boolean)

    private val inputRouter = InputRouter(::nativeKey)
    private val joystickRouter = JoystickInputRouter(::nativeJoystick)
    private val gamepadMapper = GamepadMapper(inputRouter, joystickRouter, ::controllerAction)
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
    private lateinit var libraryScreen: LibraryScreen
    private lateinit var romLibrary: RomLibrary
    private lateinit var controllerEditor: ControllerEditor
    private var libraryVisible = true
    private var romTree: Uri? = null
    private var scanCancelled = AtomicBoolean(false)
    private var currentEntry: LibraryEntry? = null
    private var currentDisk: File? = null
    private var currentIsFloppy = false
    private val mountedFloppies = arrayOfNulls<String>(2)
    private val manualFloppies = arrayOfNulls<File>(2)
    @Volatile private var floppyBusy = false
    @Volatile private var biosBusy = false
    @Volatile private var fontBusy = false
    private var currentTitle: String? = null
    private var currentGame: GameCatalog.Game? = null
    private var commandCancelled = AtomicBoolean(false)
    private var libraryEntries = emptyList<LibraryEntry>()
    private var pendingDebugGame: String? = null
    private lateinit var backdrop: View
    private lateinit var drawer: ScrollView
    private lateinit var menuStatus: TextView
    private lateinit var mediaLabel: TextView
    private lateinit var pauseTitle: TextView
    private lateinit var pauseDetail: TextView
    private val menuItems = ArrayList<View>()
    private var selectedMenuIndex = 0
    private var menuOpen = false
    private var userPaused = false
    private var activityVisible = false
    private var integerScaling = true
    private var integerCrop = false
    private var muted = false
    private var clock = 25
    private var exiting = false
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
    @Volatile private var startGeneration = 0

    private val updateStatus = object : Runnable {
        override fun run() {
            if (menuOpen && !preparingFont && !floppyBusy) menuStatus.text = nativeStatus()
            if (!libraryVisible && !preparingFont) {
                InputModeDecider.GuestInput.fromNative(nativeInputTelemetry())?.let {
                    inputModeDecider.observe(it, android.os.SystemClock.elapsedRealtime())
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            pendingDebugGame = intent.getStringExtra("kairo98.selectGame64")?.let { encoded ->
                runCatching {
                    String(android.util.Base64.decode(encoded,
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrNull()
            } ?: intent.getStringExtra("kairo98.selectGame")
        }
        integerScaling = preferences.getBoolean("integer_scaling", true)
        integerCrop = preferences.getBoolean("integer_crop", false)
        muted = preferences.getBoolean("muted", false)
        clock = preferences.getInt("base_clock", 25).let { if (it == 20) 20 else 25 }
        globalInputMode = InputModeDecider.parse(preferences.getString("input_mode", "auto"))
        nativeSetMuted(muted)
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
        romLibrary = RomLibrary(this)
        controllerEditor = ControllerEditor(this, root,
            ::loadControllerBindings, ::saveControllerBindings, ::resetControllerBindings,
            { gamepadMapper.deadZone }, { value ->
                gamepadMapper.deadZone = value
                preferences.edit().putFloat("controller_dead_zone", value).apply()
            }, ::applyPauseState)
        libraryScreen = LibraryScreen(this, romLibrary.catalog,
            ::chooseRomFolder, { refreshLibrary(false) }, { refreshLibrary(true) },
            { libraryScreen.closeActions(); showMachine() },
            ::launchEntry, ::showDetailPreview, ::showGameDetails)
        root.addView(libraryScreen, FrameLayout.LayoutParams(-1, -1))
        handler.post(updateStatus)
        root.post {
            val saved = preferences.getString("rom_tree", null)
            romTree = saved?.let(Uri::parse)
            libraryScreen.showFolder(romTree?.let(::folderLabel))
            if (romTree == null) {
                libraryScreen.showStatus("Choose a ROM folder to find disk images and ZIP games")
                chooseRomFolder()
            } else if (!hasRomGrant(romTree!!)) {
                libraryScreen.showStatus("Folder access expired. Select the ROM folder again.")
            } else {
                libraryEntries = romLibrary.cached(romTree!!)
                libraryScreen.showEntries(libraryEntries)
                selectPendingDebugGame()
                refreshLibrary(false)
            }
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = SurfaceView(this).apply {
            holder.addCallback(this@MainActivity)
            isFocusableInTouchMode = true
            contentDescription = "PC-98 display"
            setOnTouchListener { _, event -> handleScreenTouch(event) }
        }
        root.addView(screen, FrameLayout.LayoutParams(640, 400, Gravity.CENTER))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }

        keyboardPanel = Pc98KeyboardPanel(this, inputRouter, ::hideKeyboard)
        root.addView(keyboardPanel, FrameLayout.LayoutParams(-1, dp(260), Gravity.BOTTOM))

        backdrop = View(this).apply {
            setBackgroundColor(0xb8000000.toInt())
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
            setBackgroundColor(0xff171d27.toInt())
        }
        val drawerWidth = minOf(dp(320), resources.displayMetrics.widthPixels - dp(40))
        root.addView(drawer, FrameLayout.LayoutParams(drawerWidth, -1, Gravity.START))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(24))
        }
        drawer.addView(content)

        content.addView(TextView(this).apply {
            text = "KAIRO98"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(dp(12), 0, dp(12), 0)
        })
        content.addView(TextView(this).apply {
            text = "PC-98 EMULATOR"
            textSize = 11f
            letterSpacing = 0.18f
            setTextColor(0xff66d6df.toInt())
            setPadding(dp(12), dp(2), dp(12), dp(12))
        })
        mediaLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(0xffc8d0da.toInt())
            maxLines = 2
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        content.addView(mediaLabel)
        menuStatus = TextView(this).apply {
            textSize = 11f
            setTextColor(0xff8794a3.toInt())
            maxLines = 3
            setPadding(dp(12), 0, dp(12), dp(12))
        }
        content.addView(menuStatus)
        section(content, "SESSION")
        menuItem(content, "Continue", "Return to the machine") {
            userPaused = false
            closeMenu()
        }
        menuItem(content, "Restart", "Reset the current machine") { restartMachine() }
        menuItem(content, "Game library", "Choose another disk") { showLibrary() }
        menuItem(content, "Choose disk image", "Import a hard disk or floppy") { chooseHdi() }
        menuItem(content, "Floppy A", "Insert, swap, or eject a disk") { showFloppyMenu(0) }
        menuItem(content, "Floppy B", "Insert, swap, or eject a disk") { showFloppyMenu(1) }
        val pauseItem = menuItem(content, "Pause", "Keep the machine paused") {
            userPaused = !userPaused
            closeMenu()
        }
        pauseTitle = pauseItem.second
        pauseDetail = (pauseItem.first as LinearLayout).getChildAt(1) as TextView
        menuItem(content, "Exit", "Stop and close Kairo98") { exitApp() }

        section(content, "SETTINGS")
        menuItem(content, "Graphics", "Scaling and display") { showGraphics() }
        menuItem(content, "Input mode", "Auto, keyboard, or mouse touchpad") { showInputMode() }
        menuItem(content, "Machine", "Clock, BIOS ROM, and font") { showMachine() }
        menuItem(content, "Controller", "Gamepad buttons, sticks and hats") { showControllerScope() }
        menuItem(content, "Audio", "Sound output") { showAudio() }
        menuItem(content, "About & controls", "Version and shortcuts") { showAbout() }

        setContentView(root)
        screen.requestFocus()
    }

    private fun section(content: LinearLayout, title: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.14f
            setTextColor(0xff66d6df.toInt())
            setPadding(dp(12), dp(15), dp(12), dp(5))
        })
    }

    private fun menuItem(content: LinearLayout, title: String, detail: String,
                         action: () -> Unit): Pair<View, TextView> {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(57)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isFocusable = true
            isClickable = true
            background = StateListDrawable().apply {
                val highlight = GradientDrawable().apply {
                    setColor(0xff30475b.toInt())
                    cornerRadius = dp(8).toFloat()
                }
                addState(intArrayOf(android.R.attr.state_focused), highlight)
                addState(intArrayOf(android.R.attr.state_selected), highlight)
                addState(intArrayOf(android.R.attr.state_pressed), highlight)
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
            setOnClickListener { clicked ->
                focusMenuItem(menuItems.indexOf(clicked))
                action()
            }
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        item.addView(heading)
        item.addView(TextView(this).apply {
            text = detail
            textSize = 12f
            setTextColor(0xff9aa8b6.toInt())
        })
        content.addView(item, LinearLayout.LayoutParams(-1, -2))
        menuItems.add(item)
        return item to heading
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
        val availableHeight = (root.height - keyboardHeight).coerceAtLeast(1)
        val fit = minOf(root.width / 640f, availableHeight / 400f)
        if (fit <= 0f) return
        val scale = if (integerScaling && fit >= 1f) {
            if (integerCrop) ceil(fit) else floor(fit)
        } else fit
        val width = (640 * scale).roundToInt().coerceAtLeast(1)
        val height = (400 * scale).roundToInt().coerceAtLeast(1)
        val params = screen.layoutParams as FrameLayout.LayoutParams
        val top = ((availableHeight - height) / 2).coerceAtLeast(0)
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
        nativePause(true)
        drawer.scrollTo(0, 0)
        focusMenuItem(0)
        pauseTitle.text = if (userPaused) "Resume" else "Pause"
        pauseDetail.text = if (userPaused) "Continue running" else "Keep the machine paused"
        mediaLabel.text = currentTitle ?: "No disk selected"
        menuStatus.text = if (preparingFont) "Preparing PC-98 font" else nativeStatus()
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

    private fun applyPauseState() {
        nativePause(userPaused || menuOpen || libraryVisible || !activityVisible || preparingFont ||
            (::controllerEditor.isInitialized && controllerEditor.isOpen))
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

    private fun selectPendingDebugGame() {
        val query = pendingDebugGame ?: return
        if (libraryScreen.selectGame(query)) {
            pendingDebugGame = null
            android.util.Log.i("Kairo98", "Selected library game: $query")
        }
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
        val generation = ++startGeneration
        val cancelled = AtomicBoolean(false)
        preparingFont = true
        applyPauseState()
        libraryScreen.showStatus("Preparing ${entry.displayName}…")
        Thread {
            val game = romLibrary.catalog.resolve(entry.contentId!!, entry.displayName)
            val result = try {
                val disk = romLibrary.prepare(entry, cancelled) { message ->
                    runOnUiThread { if (generation == startGeneration) libraryScreen.showStatus(message) }
                }
                val font = prepareFont()
                if (generation != startGeneration) null
                else {
                    nativeStop()
                    if (generation != startGeneration) null
                    else if (nativeStart(disk.absolutePath, font.path, firmwareDir().absolutePath,
                            font.bitmap,
                            game.baseClockTenthsMHz ?: clock,
                            game.gdcClockTenthsMHz ?: 50, entry.isFloppy)) {
                        awaitMachineReady()?.let(::error)
                        disk
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
                        currentDisk = result
                        currentIsFloppy = entry.isFloppy
                        mountedFloppies[0] = if (entry.isFloppy) entry.displayName else null
                        mountedFloppies[1] = null
                        currentTitle = game.title
                        currentGame = game
                        inputModeDecider.reset()
                        gamepadMapper.bindings = effectiveControllerBindings(game)
                        userPaused = false
                        menuOpen = false
                        libraryVisible = false
                        libraryScreen.visibility = View.GONE
                        screen.requestFocus()
                        scheduleGuestCommand(game)
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
        libraryVisible = true
        closeMenu()
        libraryScreen.visibility = View.VISIBLE
        libraryScreen.showFolder(romTree?.let(::folderLabel))
        libraryScreen.showStatus("${libraryEntries.count { it.playable }} games ready")
        applyPauseState()
    }

    private fun configuredInputMode(): InputModeDecider.Mode =
        currentGame?.inputMode?.let(InputModeDecider::parse) ?: globalInputMode

    private fun showKeyboard() {
        if (keyboardPanel.visibility == View.VISIBLE) return
        keyboardPanel.visibility = View.VISIBLE
        updateViewport()
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
        screen.requestFocus()
    }

    private fun moveGuestMouse(event: MotionEvent) {
        if (screen.width <= 0 || screen.height <= 0) return
        mouseFractionX += (event.x - mouseTouchLastX) * 640f / screen.width
        mouseFractionY += (event.y - mouseTouchLastY) * 400f / screen.height
        mouseTouchLastX = event.x
        mouseTouchLastY = event.y
        val dx = mouseFractionX.toInt().coerceIn(-640, 640)
        val dy = mouseFractionY.toInt().coerceIn(-400, 400)
        mouseFractionX -= dx
        mouseFractionY -= dy
        if (dx != 0 || dy != 0) nativeMouseMove(dx, dy)
    }

    private fun handleScreenTouch(event: MotionEvent): Boolean {
        if (libraryVisible || menuOpen) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mouseTouchActive = inputModeDecider.resolve(configuredInputMode()) ==
                    InputModeDecider.Mode.MOUSE
                if (mouseTouchActive) {
                    pendingMouseRelease?.let(handler::removeCallbacks)
                    pendingMouseRelease = null
                    nativeMouseButton(1, false)
                    mouseDragging = false
                    mouseMoved = false
                    mouseTouchStartX = event.x
                    mouseTouchStartY = event.y
                    mouseTouchLastX = event.x
                    mouseTouchLastY = event.y
                    mouseFractionX = 0f
                    mouseFractionY = 0f
                    val hold = Runnable {
                        if (mouseTouchActive && !mouseMoved) {
                            mouseDragging = true
                            nativeMouseButton(1, true)
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
                moveGuestMouse(event)
            }
            MotionEvent.ACTION_UP -> {
                if (mouseTouchActive) {
                    pendingMouseHold?.let(handler::removeCallbacks)
                    pendingMouseHold = null
                    moveGuestMouse(event)
                    if (mouseDragging) nativeMouseButton(1, false)
                    else if (!mouseMoved) {
                        nativeMouseButton(1, true)
                        val release = Runnable {
                            nativeMouseButton(1, false)
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
                if (mouseTouchActive) nativeMouseButton(1, false)
                mouseTouchActive = false
                mouseDragging = false
                mouseMoved = false
            }
        }
        return true
    }

    private fun scheduleGuestCommand(game: GameCatalog.Game?) {
        commandCancelled.set(true)
        val command = game?.launchCommand ?: return
        val cancelled = AtomicBoolean(false)
        commandCancelled = cancelled
        val generation = startGeneration
        Thread {
            val deadline = android.os.SystemClock.elapsedRealtime() + game.launchTimeoutMs
            while (!cancelled.get() && generation == startGeneration &&
                android.os.SystemClock.elapsedRealtime() < deadline) {
                if (nativeDosPromptReady() && nativeStatus().startsWith("Running")) {
                    try {
                        for (character in command) {
                            if (cancelled.get() || generation != startGeneration) return@Thread
                            val scans = guestCommandScans(character)
                                ?: error("Unsupported launch character: $character")
                            inputRouter.hold("guest-command", scans)
                            try { Thread.sleep(70) } finally { inputRouter.release("guest-command") }
                            Thread.sleep(70)
                        }
                        if (!cancelled.get() && generation == startGeneration) {
                            inputRouter.hold("guest-command", listOf(0x1c))
                            try { Thread.sleep(70) } finally { inputRouter.release("guest-command") }
                        }
                    } catch (error: Exception) {
                        runOnUiThread { if (!cancelled.get()) toast(error.message ?: "Launch command failed") }
                    }
                    return@Thread
                }
                Thread.sleep(100)
            }
            if (!cancelled.get() && generation == startGeneration) {
                runOnUiThread { toast("DOS prompt not detected; type the launch command manually") }
            }
        }.start()
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
        val fields = arrayOf("Title", "Machine clock", "Guest command", "Preview art", "Box art",
            "View screenshot", "Reset all custom settings", "File information", "Controller mapping",
            "Input mode", "GDC clock")
        val values = arrayOf(
            "${game.title} · ${id?.let { catalog.sourceOf(it, "title") } ?: "Filename"}",
            "${game.baseClockTenthsMHz?.let { "${it / 10.0} MHz" } ?: "App default"} · ${id?.let { catalog.sourceOf(it, "machine") } ?: "App default"}",
            "${game.launchCommand ?: "None"} · ${id?.let { catalog.sourceOf(it, "launch") } ?: "App default"}",
            "${if (game.preview == null) "None" else "Available"} · ${id?.let { catalog.sourceOf(it, "artwork", "preview") } ?: "App default"}",
            "${if (game.boxArt == null) "None" else "Available"} · ${id?.let { catalog.sourceOf(it, "artwork", "boxArt") } ?: "App default"}",
            if (game.preview == null) "No screenshot available" else "Open preview",
            "Restore catalog values", "Path, ZIP entry, and content ID",
            "${effectiveControllerBindings(game).size} bindings · ${if (game.controllerBindings == null || (game.controllerBindings == "[]" && !game.overriddenFields.contains("controller"))) "Global" else id?.let { catalog.sourceOf(it, "controller") } ?: "Global"}",
            "${InputModeDecider.parse(game.inputMode ?: InputModeDecider.storageValue(globalInputMode))} · ${id?.let { catalog.sourceOf(it, "input") } ?: "App default"}",
            "${game.gdcClockTenthsMHz?.let { "${it / 10.0} MHz" } ?: "5.0 MHz (app default)"} · ${id?.let { catalog.sourceOf(it, "machine") } ?: "App default"}"
        )
        AlertDialog.Builder(this).setTitle(game.title)
            .setItems(fields.indices.map { "${fields[it]}\n${values[it]}" }.toTypedArray()) { _, which ->
                if (id == null && which !in 5..7) {
                    toast("This file needs a successful hash before settings can be saved")
                    return@setItems
                }
                when (which) {
                    0 -> editGameText(entry, "title", game.title)
                    1 -> editGameClock(entry)
                    2 -> editGameText(entry, "launch", game.launchCommand ?: "")
                    3 -> editGameArt(entry, "preview", game.preview ?: "")
                    4 -> editGameArt(entry, "boxArt", game.boxArt ?: "")
                    5 -> showGamePreview(entry)
                    6 -> AlertDialog.Builder(this).setTitle("Reset all settings?")
                        .setMessage("Restore this game's current catalog defaults.")
                        .setPositiveButton("Reset") { _, _ -> saveGameSetting(entry) {
                            catalog.resetOverride(id!!)
                        } }.setNegativeButton("Cancel", null).showStyled()
                    7 -> AlertDialog.Builder(this).setTitle("File information")
                        .setMessage("${entry.path}${entry.zipEntry?.let { "\n$it" } ?: ""}\n\n" +
                            (id ?: entry.error ?: "Not hashed"))
                        .setPositiveButton("Close", null).showStyled()
                    8 -> showControllerBindings(entry)
                    9 -> showInputModeChoices(entry)
                    10 -> editGameGdcClock(entry)
                }
            }.setPositiveButton("Play") { _, _ -> launchEntry(entry) }
            .setNegativeButton("Close", null).showStyled()
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
            assets.open(path).use { BitmapFactory.decodeStream(it, null,
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
        val dialog = AlertDialog.Builder(this).setTitle(game.title).setView(content)
            .setPositiveButton("Done") { _, _ -> if (returnToSettings) showGameDetails(entry) }
            .showStyled()
        if (url != null) view.setOnClickListener {
            view.isEnabled = false
            hint.text = "Loading larger image…"
            Thread {
                val larger = try { fetchLargerArt(url) } catch (_: Exception) { null }
                runOnUiThread {
                    if (dialog.isShowing) {
                        if (larger == null) hint.text = "Could not load larger image"
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
                connection.contentLengthLong > 16L * 1024 * 1024) return null
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 16 * 1024 * 1024) return null
                    bytes.write(buffer, 0, count)
                }
            }
            val data = bytes.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth < 1 || bounds.outHeight < 1 ||
                bounds.outWidth.toLong() * bounds.outHeight > 32_000_000L) return null
            val options = BitmapFactory.Options()
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
                        JSONObject().put("baseClockTenthsMHz", if (which == 1) 20 else 25).apply {
                            current.gdcClockTenthsMHz?.let { put("gdcClockTenthsMHz", it) }
                        })
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
                        JSONObject().put("gdcClockTenthsMHz", if (which == 1) 25 else 50).apply {
                            current.baseClockTenthsMHz?.let { put("baseClockTenthsMHz", it) }
                        })
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
            scheduleGuestCommand(currentGame)
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

    private fun changeFloppy(drive: Int, entry: LibraryEntry?) {
        if (floppyBusy) return
        floppyBusy = true
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
                floppyBusy = false
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

    private fun showGraphics() {
        val options = arrayOf(
            "Integer  ·  full image (default)",
            "Integer  ·  crop edges",
            "Fit display  ·  fractional scale"
        )
        AlertDialog.Builder(this).setTitle("Graphics")
            .setSingleChoiceItems(options, if (!integerScaling) 2 else if (integerCrop) 1 else 0) { dialog, which ->
                integerScaling = which != 2
                integerCrop = which == 1
                preferences.edit()
                    .putBoolean("integer_scaling", integerScaling)
                    .putBoolean("integer_crop", integerCrop)
                    .apply()
                updateViewport()
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).showStyled()
    }

    private fun showMachine() {
        val bios = biosFile()
        val font = fontBitmapFile()
        AlertDialog.Builder(this).setTitle("Machine")
            .setItems(arrayOf("Base clock  ·  ${if (clock == 25) "2.5" else "2"} MHz",
                "BIOS ROM  ·  ${if (bios.isFile) "Imported" else "None"}",
                "Font BMP  ·  ${if (font.isFile) "Imported" else "Generated"}")) { _, which ->
                when (which) {
                    0 -> showMachineClock()
                    1 -> showBiosRom()
                    else -> showFontBitmap()
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
            .setPositiveButton("Choose file") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, BIOS_REQUEST)
            }.setNegativeButton("Close", null)
        if (installed) dialog.setNeutralButton("Remove") { _, _ ->
            if (!biosBusy) {
                if (biosFile().delete()) toast("BIOS ROM removed. Restart the game to apply.")
                else toast("Could not remove BIOS ROM")
            }
        }
        dialog.showStyled()
    }

    private fun showFontBitmap() {
        val dialog = AlertDialog.Builder(this).setTitle("Font BMP")
            .setPositiveButton("Choose file") { _, _ ->
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, FONT_REQUEST)
            }.setNegativeButton("Close", null)
        if (fontBitmapFile().isFile) dialog.setNeutralButton("Remove") { _, _ ->
            if (!fontBusy) {
                if (fontBitmapFile().delete()) toast("Font BMP removed. Restart the game to apply.")
                else toast("Could not remove Font BMP")
            }
        }
        dialog.showStyled()
    }

    private fun showAudio() {
        val options = arrayOf("Sound on", "Muted")
        AlertDialog.Builder(this).setTitle("Audio")
            .setSingleChoiceItems(options, if (muted) 1 else 0) { dialog, which ->
                muted = which == 1
                preferences.edit().putBoolean("muted", muted).apply()
                nativeSetMuted(muted)
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).showStyled()
    }

    private fun showInputMode() {
        val entry = currentEntry?.takeIf { it.contentId != null }
        if (entry == null) {
            showInputModeChoices(null)
            return
        }
        AlertDialog.Builder(this).setTitle("Input mode")
            .setItems(arrayOf("This game", "Global default")) { _, which ->
                showInputModeChoices(if (which == 0) entry else null)
            }.setNegativeButton("Cancel", null).showStyled()
    }

    private fun showInputModeChoices(entry: LibraryEntry?) {
        val game = entry?.contentId?.let { romLibrary.catalog.resolve(it, entry.displayName) }
        val choices = InputModeDecider.Mode.entries
        var selected = choices.indexOf(if (entry == null) globalInputMode
            else InputModeDecider.parse(game?.inputMode ?: InputModeDecider.storageValue(globalInputMode)))
        val builder = AlertDialog.Builder(this)
            .setTitle(if (entry == null) "Global input mode" else "Input mode for ${game?.title}")
            .setSingleChoiceItems(arrayOf("Auto (keyboard fallback)", "Keyboard",
                "Mouse (drag to move, tap to click)"), selected) { _, which ->
                selected = which
            }
            .setPositiveButton("Save") { _, _ ->
                val mode = choices[selected]
                if (entry == null) {
                    globalInputMode = mode
                    preferences.edit().putString("input_mode", InputModeDecider.storageValue(mode)).apply()
                } else {
                    romLibrary.catalog.setOverride(entry.contentId!!, "input",
                        JSONObject().put("mode", InputModeDecider.storageValue(mode)))
                    if (currentEntry?.contentId == entry.contentId)
                        currentGame = romLibrary.catalog.resolve(entry.contentId, entry.displayName)
                }
                toast("Input mode saved")
            }
            .setNegativeButton("Cancel", null)
        if (entry != null) builder.setNeutralButton("Reset game") { _, _ ->
            romLibrary.catalog.resetOverride(entry.contentId!!, "input")
            if (currentEntry?.contentId == entry.contentId)
                currentGame = romLibrary.catalog.resolve(entry.contentId, entry.displayName)
            toast("Input mode restored")
        }
        builder.showStyled()
    }

    private fun showAbout() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        AlertDialog.Builder(this).setTitle("Kairo98 $version")
            .setMessage("Open the menu with a controller Mode/Home button when Android delivers it, Back, Menu, or a swipe from the left edge. Swipe inward from the right edge to open the PC-98 keyboard. Android reserves the system Home key.\n\nPhysical keyboard input goes to the PC-98 while the menu is closed.")
            .setPositiveButton("Done", null).showStyled()
    }

    private fun globalControllerBindings() =
        ControllerBindings.parse(preferences.getString("controller_global_v1", null))

    private fun effectiveControllerBindings(game: GameCatalog.Game?): List<ControllerBinding> {
        val configured = game?.controllerBindings
        return if (game?.overriddenFields?.contains("controller") == true ||
            (configured != null && (configured != "[]" || game.controllerProfile == "custom-v1")))
            ControllerBindings.parse(configured)
        else globalControllerBindings()
    }

    private fun releaseInputs() {
        gamepadMapper.releaseAll()
        inputRouter.releaseAll()
        pendingMouseHold?.let(handler::removeCallbacks)
        pendingMouseHold = null
        pendingMouseRelease?.let(handler::removeCallbacks)
        pendingMouseRelease = null
        nativeMouseButton(1, false)
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
            "restart" -> restartMachine()
            "exit" -> exitApp()
        }
    }

    private fun showControllerScope() {
        closeMenu()
        releaseInputs()
        hideKeyboard()
        controllerEditor.show(currentEntry)
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
        dialog.show()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(0xff202a36.toInt())
            cornerRadius = dp(14).toFloat()
        })
        return dialog
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun surfaceCreated(holder: SurfaceHolder) {
        nativeSetSurface(holder.surface, holder.surfaceFrame.width(), holder.surfaceFrame.height())
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        nativeSetSurface(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        nativeSetSurface(null, 0, 0)
    }

    override fun onPause() {
        commandCancelled.set(true)
        releaseInputs()
        activityVisible = false
        applyPauseState()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activityVisible = true
        applyPauseState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            commandCancelled.set(true)
            releaseInputs()
        }
    }

    override fun onDestroy() {
        startGeneration++
        scanCancelled.set(true)
        releaseInputs()
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
        handler.removeCallbacks(updateStatus)
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
                    refreshLibrary(false)
                } catch (error: Exception) {
                    libraryScreen.showStatus("Cannot keep folder access: ${error.message}")
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
                else if (nativeStart(disk.absolutePath, font.path, firmwareDir().absolutePath,
                        font.bitmap, clock,
                        50, DiskFormat.isFloppy(name)))
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
        if (floppyBusy) return
        floppyBusy = true
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
                floppyBusy = false
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
                "BIOS ROM imported. Restart the game to apply."
            } catch (error: Exception) {
                "BIOS import failed: ${error.message ?: "Unknown error"}"
            } finally { partial.delete() }
            runOnUiThread {
                biosBusy = false
                if (menuOpen) menuStatus.text = result
                toast(result)
            }
        }.start()
    }

    private fun importFontBitmap(uri: Uri) {
        if (fontBusy) return
        fontBusy = true
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
                "Font BMP imported. Restart the game to apply."
            } catch (error: Exception) {
                "Font BMP import failed: ${error.message ?: "Unknown error"}"
            } finally { partial.delete() }
            runOnUiThread {
                fontBusy = false
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
                else if (nativeStart(disk.absolutePath, font.path, firmwareDir().absolutePath,
                        font.bitmap, clock, 50, floppy))
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
        if (::controllerEditor.isInitialized && controllerEditor.isOpen) {
            if (controllerEditor.handleKey(event)) return true
            return super.dispatchKeyEvent(event)
        }
        if (libraryVisible) {
            if (libraryScreen.detailOpen) {
                if (event.keyCode in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE)) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER ->
                                libraryScreen.activateDetail()
                            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE ->
                                libraryScreen.detailsSelection()
                            else -> libraryScreen.closeDetail()
                        }
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            if (libraryScreen.actionsOpen) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> libraryScreen.moveActionSelection(1)
                        KeyEvent.KEYCODE_DPAD_UP -> libraryScreen.moveActionSelection(-1)
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER ->
                            if (event.repeatCount == 0) libraryScreen.activateAction()
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_BUTTON_MODE -> libraryScreen.closeActions()
                    }
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> libraryScreen.moveSelection(1)
                    KeyEvent.KEYCODE_DPAD_UP -> libraryScreen.moveSelection(-1)
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER ->
                        if (event.repeatCount == 0) libraryScreen.activateSelection()
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE ->
                        if (event.repeatCount == 0) libraryScreen.openActions()
                    KeyEvent.KEYCODE_BACK ->
                        if (event.repeatCount == 0) onBackPressed()
                    else -> return super.dispatchKeyEvent(event)
                }
            }
            return true
        }
        if ((event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE &&
            !gamepadMapper.hasButton(event.keyCode)) ||
            (event.keyCode == KeyEvent.KEYCODE_BACK &&
                !gamepadMapper.hasButton(event.keyCode)) ||
            event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (menuOpen) closeMenu() else openMenu()
            }
            return true
        }
        if (menuOpen) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    focusMenuItem(selectedMenuIndex +
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1)
                }
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) closeMenu()
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
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
        if (::controllerEditor.isInitialized && controllerEditor.isOpen)
            return controllerEditor.captureMotion(event)
        if (!menuOpen && !libraryVisible && gamepadMapper.motion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
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
                if (!menuOpen && event.x <= dp(28)) {
                    edgeSwipeX = event.x
                    edgeSwipeY = event.y
                    return true
                }
                if (!menuOpen && !libraryVisible && event.x >= root.width - dp(28)) {
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

    @Deprecated("The platform Back callback is the reliable menu shortcut on API 26+")
    override fun onBackPressed() {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuOpen || libraryVisible) return true
        if (KeyEvent.isGamepadButton(keyCode) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return true
        val scanCode = pc98ScanCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) inputRouter.hold("keyboard:${event.deviceId}:$keyCode", listOf(scanCode))
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (menuOpen || libraryVisible) return true
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

    private data class FontSelection(val path: String, val bitmap: Boolean)

    private fun prepareFont(): FontSelection {
        val bitmap = fontBitmapFile()
        if (bitmap.isFile) return FontSelection(bitmap.absolutePath, true)
        Pc98FontCache.ensure(filesDir)
        return FontSelection(File(filesDir, "android-font.bin").absolutePath, false)
    }

    private fun firmwareDir() = File(filesDir, "firmware")
    private fun biosFile() = File(firmwareDir(), "bios.rom")
    private fun fontBitmapFile() = File(firmwareDir(), "font.bmp")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val HDI_REQUEST = 98
        private const val ROM_FOLDER_REQUEST = 99
        private const val FLOPPY_A_REQUEST = 100
        private const val FLOPPY_B_REQUEST = 101
        private const val BIOS_REQUEST = 102
        private const val FONT_REQUEST = 103
        private const val BIOS_ROM_BYTES = 0x18000L
        init { System.loadLibrary("kairo98") }
    }
}
