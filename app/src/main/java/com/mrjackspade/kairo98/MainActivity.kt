package com.mrjackspade.kairo98

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MainActivity : Activity(), SurfaceHolder.Callback {
    private external fun nativeStart(path: String?, mhzTimesTen: Int): Boolean
    private external fun nativeStop()
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeClock(mhzTimesTen: Int)
    private external fun nativeKey(scanCode: Int, down: Boolean)
    private external fun nativeDisk(path: String)
    private external fun nativeStatus(): String
    private external fun nativeSetSurface(surface: Surface?)

    private lateinit var status: TextView
    private lateinit var pauseButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var paused = false
    private var clock = 25
    @Volatile private var preparingFont = false
    @Volatile private var startGeneration = 0
    private val updateStatus = object : Runnable {
        override fun run() {
            if (!preparingFont) status.text = nativeStatus()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 15f; text = "No disk selected" }
        val screen = SurfaceView(this).apply {
            holder.addCallback(this@MainActivity)
            isFocusableInTouchMode = true
        }
        val chooseButton = Button(this).apply {
            text = "Choose HDI"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, HDI_REQUEST)
            }
        }
        val startButton = Button(this).apply {
            text = "Start"
            setOnClickListener {
                val disk = File(filesDir, DISK_NAME)
                if (disk.isFile) {
                    startWithFont(disk, "Starting machine")
                } else {
                    status.text = "Choose an HDI first"
                }
            }
        }
        pauseButton = Button(this).apply {
            text = "Pause"
            setOnClickListener {
                paused = !paused
                nativePause(paused)
                text = if (paused) "Resume" else "Pause"
            }
        }
        val resetButton = Button(this).apply {
            text = "Reset"
            setOnClickListener { nativeReset() }
        }
        val clockButton = Button(this).apply {
            text = "2.5 MHz"
            setOnClickListener {
                clock = if (clock == 25) 20 else 25
                nativeClock(clock)
                text = if (clock == 25) "2.5 MHz" else "2 MHz"
            }
        }
        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                startGeneration++
                Thread { nativeStop() }.start()
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chooseButton, LinearLayout.LayoutParams(0, -2, 2f))
            addView(startButton, LinearLayout.LayoutParams(0, -2, 1f))
            addView(pauseButton, LinearLayout.LayoutParams(0, -2, 1f))
            addView(resetButton, LinearLayout.LayoutParams(0, -2, 1f))
            addView(clockButton, LinearLayout.LayoutParams(0, -2, 1f))
            addView(stopButton, LinearLayout.LayoutParams(0, -2, 1f))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(screen, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(status)
            addView(row)
        })
        handler.post(updateStatus)
    }

    override fun surfaceCreated(holder: SurfaceHolder) { nativeSetSurface(holder.surface) }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        nativeSetSurface(holder.surface)
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) { nativeSetSurface(null) }

    override fun onPause() {
        nativePause(true)
        paused = true
        pauseButton.text = "Resume"
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        nativePause(false)
        paused = false
        if (::pauseButton.isInitialized) pauseButton.text = "Pause"
    }

    override fun onDestroy() {
        startGeneration++
        handler.removeCallbacks(updateStatus)
        nativeSetSurface(null)
        Thread { nativeStop() }.start()
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
            status.text = "Select an .hdi image"
            return
        }
        status.text = "Importing HDI into app storage"
        val generation = ++startGeneration
        preparingFont = true
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
            runOnUiThread { preparingFont = false; status.text = result }
        }.start()
    }

    private fun startWithFont(disk: File, message: String) {
        if (preparingFont) return
        val generation = ++startGeneration
        preparingFont = true
        status.text = "Preparing PC-98 font"
        Thread {
            val result = try {
                Pc98FontCache.ensure(filesDir)
                if (generation != startGeneration) "Start cancelled"
                else if (nativeStart(disk.absolutePath, clock)) message else "Stop the current machine before starting again"
            } catch (error: Exception) {
                "PC-98 font generation failed: ${error.message}"
            }
            runOnUiThread { preparingFont = false; status.text = result }
        }.start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val scanCode = pc98ScanCode(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) nativeKey(scanCode, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
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

    companion object {
        private const val HDI_REQUEST = 98
        private const val DISK_NAME = "boot.hdi"
        init { System.loadLibrary("kairo98") }
    }
}
