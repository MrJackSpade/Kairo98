package com.mrjackspade.kairo98

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private external fun nativeInitializeAndReset(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "Native core has not been initialized."
            textSize = 18f
        }
        val button = Button(this).apply {
            text = "Initialize and reset core"
            setOnClickListener {
                isEnabled = false
                Thread {
                    val result = try {
                        nativeInitializeAndReset()
                    } catch (error: Throwable) {
                        "Native initialization failed: ${error.message}"
                    }
                    runOnUiThread {
                        status.text = result
                        isEnabled = true
                    }
                }.start()
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            addView(status)
            addView(button)
        })
    }

    companion object {
        init {
            System.loadLibrary("kairo98")
        }
    }
}
