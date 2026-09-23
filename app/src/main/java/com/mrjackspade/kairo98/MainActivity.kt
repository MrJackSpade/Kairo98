package com.mrjackspade.kairo98

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private external fun nativeInitializeAndReset(): String
    private external fun nativeInspectHdi(path: String): String
    private lateinit var status: TextView
    private lateinit var resetCore: Button
    private lateinit var chooseDisk: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Native core has not been initialized."
            textSize = 18f
        }
        resetCore = Button(this).apply {
            text = "Initialize and reset core"
            setOnClickListener {
                setBusy(true)
                Thread {
                    val result = try {
                        nativeInitializeAndReset()
                    } catch (error: Throwable) {
                        "Native initialization failed: ${error.message}"
                    }
                    runOnUiThread {
                        status.text = result
                        setBusy(false)
                    }
                }.start()
            }
        }
        chooseDisk = Button(this).apply {
            text = "Choose HDI for read test"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, HDI_REQUEST)
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            addView(status)
            addView(resetCore)
            addView(chooseDisk)
        })
    }

    private fun setBusy(busy: Boolean) {
        resetCore.isEnabled = !busy
        chooseDisk.isEnabled = !busy
    }

    @Deprecated("Uses the platform document picker during the first-boot bring-up")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != HDI_REQUEST || resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data ?: return
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        if (name?.endsWith(".hdi", ignoreCase = true) != true) {
            status.text = "Select an .hdi image for this read test."
            return
        }
        setBusy(true)
        status.text = "Copying HDI into app storage for the native read test…"
        Thread {
            val localImage = File(cacheDir, "kairo98-media-probe.hdi")
            val result = try {
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open selected image" }
                    localImage.outputStream().use { output -> input.copyTo(output) }
                }
                nativeInspectHdi(localImage.absolutePath)
            } catch (error: Exception) {
                "HDI read test failed: ${error.message}"
            } finally {
                localImage.delete()
            }
            runOnUiThread {
                status.text = result
                setBusy(false)
            }
        }.start()
    }

    companion object {
        private const val HDI_REQUEST = 98
        init {
            System.loadLibrary("kairo98")
        }
    }
}
