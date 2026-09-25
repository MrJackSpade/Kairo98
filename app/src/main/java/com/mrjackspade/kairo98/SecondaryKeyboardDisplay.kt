package com.mrjackspade.kairo98

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/** Places the guest keyboard on another Android display when one is available. */
internal class SecondaryKeyboardDisplay(
    private val activity: Activity,
    private val input: InputRouter,
    private val onAvailabilityChanged: (Boolean) -> Unit
) : DisplayManager.DisplayListener {
    private val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var started = false
    private var keyboardVisible = false
    private var backgroundColor = Color.BLACK
    private var presentation: KeyboardPresentation? = null

    val isShowing: Boolean get() = presentation?.isShowing == true
    val isKeyboardVisible: Boolean get() = isShowing && keyboardVisible

    fun start(handler: Handler) {
        if (started) return
        started = true
        displayManager.registerDisplayListener(this, handler)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager.unregisterDisplayListener(this)
        dismiss()
    }

    fun setAppearance(showKeyboard: Boolean, color: Int) {
        keyboardVisible = showKeyboard
        backgroundColor = color
        refresh()
        presentation?.setAppearance(keyboardVisible, backgroundColor)
    }

    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()

    private fun refresh() {
        if (!started) {
            dismiss()
            return
        }
        val primaryId = activity.window.decorView.display?.displayId ?: Display.DEFAULT_DISPLAY
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val target = (presentationDisplays.asList() + displayManager.displays.asList())
            .firstOrNull { it.displayId != primaryId && it.isValid &&
                it.state != Display.STATE_OFF }
        if (target == null) {
            dismiss()
            return
        }
        if (presentation?.display?.displayId == target.displayId && isShowing) return
        dismiss()
        val next = KeyboardPresentation(activity, target, input)
        try {
            next.show()
            presentation = next
            next.setAppearance(keyboardVisible, backgroundColor)
            next.setOnDismissListener {
                if (presentation === next) {
                    presentation = null
                    onAvailabilityChanged(false)
                }
            }
            onAvailabilityChanged(true)
        } catch (error: WindowManager.InvalidDisplayException) {
            Log.w("Kairo98", "Secondary keyboard rejected display ${target.displayId}; game display $primaryId", error)
            next.dismiss()
        } catch (error: SecurityException) {
            Log.w("Kairo98", "Secondary keyboard cannot use display ${target.displayId}", error)
            next.dismiss()
        }
    }

    private fun dismiss() {
        val previous = presentation ?: return
        presentation = null
        previous.dismiss()
        onAvailabilityChanged(false)
    }

    private class KeyboardPresentation(
        activity: Activity,
        display: Display,
        private val input: InputRouter
    ) : Presentation(activity, display) {
        private lateinit var root: FrameLayout
        private lateinit var keyboard: Pc98KeyboardPanel

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
            keyboard = Pc98KeyboardPanel(context, input, {}, showClose = false).apply {
                visibility = View.GONE
            }
            root.addView(keyboard, FrameLayout.LayoutParams(-1, -1))
            setContentView(root)
        }

        fun setAppearance(showKeyboard: Boolean, color: Int) {
            if (!::root.isInitialized) return
            root.setBackgroundColor(color)
            if (showKeyboard) keyboard.visibility = View.VISIBLE
            else if (keyboard.visibility == View.VISIBLE) keyboard.close()
        }

        override fun onStop() {
            if (::keyboard.isInitialized) keyboard.close()
            super.onStop()
        }
    }
}
