package com.mrjackspade.kairo98

import android.app.Activity
import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** Places the guest keyboard on another Android display when one is available. */
internal class SecondaryKeyboardDisplay(
    private val activity: Activity,
    private val input: InputRouter,
    private val onAvailabilityChanged: (Boolean) -> Unit,
    private val onGameSurface: (Surface?, Int, Int) -> Unit,
    private val onGameTouch: (MotionEvent, Int, Int) -> Boolean,
    private val onSwapChanged: (Boolean) -> Unit
) : DisplayManager.DisplayListener {
    private val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var started = false
    private var keyboardVisible = false
    private var backgroundColor = Color.BLACK
    private var presentation: KeyboardPresentation? = null
    private var companion: SecondaryKeyboardActivity? = null
    private var companionStarting = false
    var swapped = false
        private set

    val isShowing: Boolean get() = presentation?.isShowing == true ||
        companion?.isFinishing == false
    val isKeyboardVisible: Boolean get() = isShowing && keyboardVisible
    val isCompanionActive: Boolean get() = companionStarting || companion != null
    val activeGameSurface: SurfaceView?
        get() = presentation?.content?.activeGameSurface ?: companion?.activeGameSurface

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
        if (swapped) {
            swapped = false
            onSwapChanged(false)
        }
        dismiss()
    }

    fun toggleSwap() {
        if (!isShowing) return
        swapped = !swapped
        presentation?.content?.setAppearance(keyboardVisible, backgroundColor, swapped)
        companion?.setAppearance(keyboardVisible, backgroundColor, swapped)
        onSwapChanged(swapped)
    }

    fun setAppearance(showKeyboard: Boolean, color: Int) {
        keyboardVisible = showKeyboard
        backgroundColor = color
        refresh()
        presentation?.content?.setAppearance(keyboardVisible, backgroundColor, swapped)
        companion?.setAppearance(keyboardVisible, backgroundColor, swapped)
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
            if (swapped) {
                swapped = false
                onSwapChanged(false)
            }
            dismiss()
            return
        }
        if (target.displayId == Display.DEFAULT_DISPLAY) {
            dismissPresentation()
            if (companion != null || companionStarting) return
            launchCompanion(target)
            return
        }
        dismissCompanion()
        if (presentation?.display?.displayId == target.displayId && isShowing) return
        dismissPresentation()
        val next = KeyboardPresentation(activity, target)
        try {
            next.show()
            presentation = next
            next.content.setAppearance(keyboardVisible, backgroundColor, swapped)
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

    private fun launchCompanion(target: Display) {
        companionStarting = true
        pendingCompanion = this
        try {
            val intent = Intent(activity, SecondaryKeyboardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId)
            activity.startActivity(intent, options.toBundle())
        } catch (error: RuntimeException) {
            companionStarting = false
            if (pendingCompanion === this) pendingCompanion = null
            Log.w("Kairo98", "Could not open keyboard activity on display ${target.displayId}", error)
        }
    }

    internal fun attachCompanion(value: SecondaryKeyboardActivity): Pc98KeyboardPanel {
        companionStarting = false
        companion = value
        onAvailabilityChanged(true)
        return Pc98KeyboardPanel(value, input, {}, showClose = false, onSwap = ::toggleSwap)
    }

    internal fun updateCompanion(value: SecondaryKeyboardActivity) {
        if (companion === value) value.setAppearance(keyboardVisible, backgroundColor, swapped)
    }

    internal fun detachCompanion(value: SecondaryKeyboardActivity) {
        if (companion !== value) return
        companion = null
        if (pendingCompanion === this) pendingCompanion = null
        onAvailabilityChanged(false)
    }

    private fun dismiss() {
        dismissPresentation()
        dismissCompanion()
    }

    private fun dismissPresentation() {
        val previous = presentation ?: return
        presentation = null
        previous.dismiss()
        onAvailabilityChanged(false)
    }

    private fun dismissCompanion() {
        val previous = companion
        companion = null
        companionStarting = false
        if (pendingCompanion === this) pendingCompanion = null
        if (previous != null) {
            previous.finish()
            onAvailabilityChanged(false)
        }
    }

    companion object {
        internal var pendingCompanion: SecondaryKeyboardDisplay? = null
    }

    private inner class KeyboardPresentation(
        activity: Activity,
        display: Display
    ) : Presentation(activity, display) {
        lateinit var content: SecondaryKeyboardContent
            private set

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            content = SecondaryKeyboardContent(context,
                Pc98KeyboardPanel(context, input, {}, showClose = false,
                    onSwap = ::toggleSwap), ::forwardGameSurface, onGameTouch)
            setContentView(content)
        }

        override fun onStop() {
            if (::content.isInitialized) content.close()
            super.onStop()
        }
    }

    internal fun forwardGameSurface(surface: Surface?, width: Int, height: Int) {
        if (swapped) onGameSurface(surface, width, height)
    }

    internal fun forwardGameTouch(event: MotionEvent, width: Int, height: Int): Boolean =
        swapped && onGameTouch(event, width, height)
}

internal class SecondaryKeyboardContent(
    context: Context,
    private val keyboard: Pc98KeyboardPanel,
    private val onGameSurface: (Surface?, Int, Int) -> Unit,
    private val onGameTouch: (MotionEvent, Int, Int) -> Boolean
) : FrameLayout(context), SurfaceHolder.Callback {
    private val gameSurface = SurfaceView(context).apply {
        visibility = View.GONE
        holder.addCallback(this@SecondaryKeyboardContent)
        setOnTouchListener { view, event -> onGameTouch(event, view.width, view.height) }
    }
    private var gameActive = false

    init {
        setBackgroundColor(Color.BLACK)
        addView(gameSurface, LayoutParams(640, 400, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        addView(keyboard, LayoutParams(-1, -1))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        val scale = minOf(width / 640f, height / 400f)
        val gameWidth = (640 * scale).roundToInt().coerceAtLeast(1)
        val gameHeight = (400 * scale).roundToInt().coerceAtLeast(1)
        gameSurface.layoutParams = LayoutParams(gameWidth, gameHeight,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = (height - gameHeight) / 2
        }
    }

    fun setAppearance(showKeyboard: Boolean, color: Int, swapped: Boolean) {
        setBackgroundColor(color)
        gameActive = showKeyboard && swapped
        gameSurface.visibility = if (gameActive) View.VISIBLE else View.GONE
        if (showKeyboard && !swapped) keyboard.visibility = View.VISIBLE
        else if (keyboard.visibility == View.VISIBLE) keyboard.close()
        if (!gameActive) onGameSurface(null, 0, 0)
    }

    fun close() = keyboard.close()

    /** The surface showing the guest while the screens are swapped, else null. */
    val activeGameSurface: SurfaceView? get() = gameSurface.takeIf { gameActive }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (gameActive) onGameSurface(holder.surface, gameSurface.width, gameSurface.height)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (gameActive) onGameSurface(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (gameActive) onGameSurface(null, 0, 0)
    }
}
