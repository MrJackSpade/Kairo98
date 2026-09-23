package com.mrjackspade.kairo98

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity(), SurfaceHolder.Callback {
    private external fun nativeStart(path: String?, mhzTimesTen: Int): Boolean
    private external fun nativeStop()
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeClock(mhzTimesTen: Int)
    private external fun nativeKey(scanCode: Int, down: Boolean)
    private external fun nativeStatus(): String
    private external fun nativeSetSurface(surface: Surface?, width: Int, height: Int)
    private external fun nativeSetMuted(muted: Boolean)

    private val preferences by lazy { getSharedPreferences("kairo98", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var screen: SurfaceView
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
    @Volatile private var preparingFont = false
    @Volatile private var startGeneration = 0

    private val updateStatus = object : Runnable {
        override fun run() {
            if (menuOpen && !preparingFont) menuStatus.text = nativeStatus()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        integerScaling = preferences.getBoolean("integer_scaling", true)
        integerCrop = preferences.getBoolean("integer_crop", false)
        muted = preferences.getBoolean("muted", false)
        clock = preferences.getInt("base_clock", 25).let { if (it == 20) 20 else 25 }
        nativeSetMuted(muted)
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
        handler.post(updateStatus)
        root.post {
            val disk = File(filesDir, DISK_NAME)
            if (disk.isFile) startWithFont(disk, "Starting machine") else openMenu()
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = SurfaceView(this).apply {
            holder.addCallback(this@MainActivity)
            isFocusableInTouchMode = true
            contentDescription = "PC-98 display"
        }
        root.addView(screen, FrameLayout.LayoutParams(640, 400, Gravity.CENTER))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }

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
        menuItem(content, "Choose HDI", "Import a disk image") { chooseHdi() }
        val pauseItem = menuItem(content, "Pause", "Keep the machine paused") {
            userPaused = !userPaused
            closeMenu()
        }
        pauseTitle = pauseItem.second
        pauseDetail = (pauseItem.first as LinearLayout).getChildAt(1) as TextView
        menuItem(content, "Exit", "Stop and close Kairo98") { exitApp() }

        section(content, "SETTINGS")
        menuItem(content, "Graphics", "Scaling and display") { showGraphics() }
        menuItem(content, "Machine", "Base clock") { showMachine() }
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
        val fit = minOf(root.width / 640f, root.height / 400f)
        if (fit <= 0f) return
        val scale = if (integerScaling && fit >= 1f) {
            if (integerCrop) ceil(fit) else floor(fit)
        } else fit
        val width = (640 * scale).roundToInt().coerceAtLeast(1)
        val height = (400 * scale).roundToInt().coerceAtLeast(1)
        val params = screen.layoutParams as FrameLayout.LayoutParams
        if (params.width != width || params.height != height) {
            screen.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
        }
    }

    private fun openMenu() {
        if (menuOpen) return
        menuOpen = true
        nativePause(true)
        drawer.scrollTo(0, 0)
        focusMenuItem(0)
        pauseTitle.text = if (userPaused) "Resume" else "Pause"
        pauseDetail.text = if (userPaused) "Continue running" else "Keep the machine paused"
        mediaLabel.text = if (File(filesDir, DISK_NAME).isFile)
            "HDI  ·  " + preferences.getString("disk_name", DISK_NAME)
        else "No HDI selected"
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
        nativePause(userPaused || menuOpen || !activityVisible || preparingFont)
    }

    private fun restartMachine() {
        val disk = File(filesDir, DISK_NAME)
        if (!disk.isFile) {
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
        } else {
            startWithFont(disk, "Starting machine")
        }
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
        val options = arrayOf("2.5 MHz base clock", "2 MHz base clock")
        AlertDialog.Builder(this).setTitle("Machine")
            .setSingleChoiceItems(options, if (clock == 25) 0 else 1) { dialog, which ->
                val selected = if (which == 0) 25 else 20
                if (clock != selected) {
                    clock = selected
                    preferences.edit().putInt("base_clock", clock).apply()
                    nativeClock(clock)
                    toast("Clock change resets the machine")
                }
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).showStyled()
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

    private fun showAbout() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        AlertDialog.Builder(this).setTitle("Kairo98 $version")
            .setMessage("Open the menu with a controller Mode/Home button when Android delivers it, Back, Menu, or a swipe from the left edge. Android reserves the system Home key.\n\nPhysical keyboard input goes to the PC-98 while the menu is closed.")
            .setPositiveButton("Done", null).showStyled()
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
        activityVisible = false
        applyPauseState()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activityVisible = true
        applyPauseState()
    }

    override fun onDestroy() {
        startGeneration++
        handler.removeCallbacks(updateStatus)
        nativeSetSurface(null, 0, 0)
        if (!exiting) Thread { nativeStop() }.start()
        super.onDestroy()
    }

    @Deprecated("First-boot document import; persistent URI handling comes later")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != HDI_REQUEST || resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data ?: return
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (name?.endsWith(".hdi", ignoreCase = true) != true) {
            toast("Select an .hdi image")
            return
        }
        val generation = ++startGeneration
        preparingFont = true
        applyPauseState()
        toast("Importing HDI")
        Thread {
            val disk = File(filesDir, DISK_NAME)
            val partial = File(filesDir, "$DISK_NAME.part")
            val result = try {
                nativeStop()
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected image" }
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                Files.move(partial.toPath(), disk.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Pc98FontCache.ensure(filesDir)
                if (generation != startGeneration) "Start cancelled"
                else if (nativeStart(disk.absolutePath, clock)) "Starting $name" else "Unable to start machine"
            } catch (error: Exception) {
                "HDI import failed: ${error.message}"
            } finally {
                partial.delete()
            }
            runOnUiThread {
                preparingFont = false
                if (result.startsWith("Starting ")) {
                    preferences.edit().putString("disk_name", name).apply()
                }
                applyPauseState()
                toast(result)
            }
        }.start()
    }

    private fun startWithFont(disk: File, message: String) {
        if (preparingFont) return
        val generation = ++startGeneration
        preparingFont = true
        applyPauseState()
        toast("Preparing PC-98 font")
        Thread {
            val result = try {
                Pc98FontCache.ensure(filesDir)
                nativeStop()
                if (generation != startGeneration) "Start cancelled"
                else if (nativeStart(disk.absolutePath, clock)) message else "Unable to start machine"
            } catch (error: Exception) {
                "PC-98 font generation failed: ${error.message}"
            }
            runOnUiThread {
                preparingFont = false
                applyPauseState()
                toast(result)
            }
        }.start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE ||
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
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return super.dispatchTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeSwipeConsumed = false
                if (!menuOpen && event.x <= dp(28)) {
                    edgeSwipeX = event.x
                    edgeSwipeY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
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
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
        if (menuOpen) closeMenu() else openMenu()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuOpen) return true
        val scanCode = pc98ScanCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) nativeKey(scanCode, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (menuOpen) return true
        val scanCode = pc98ScanCode(keyCode) ?: return super.onKeyUp(keyCode, event)
        nativeKey(scanCode, false)
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
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0x70
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0x74
            else -> null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val HDI_REQUEST = 98
        private const val DISK_NAME = "boot.hdi"
        init { System.loadLibrary("kairo98") }
    }
}
