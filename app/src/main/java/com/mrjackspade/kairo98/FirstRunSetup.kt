package com.mrjackspade.kairo98

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Two explicit first-run steps; Android's document picker opens only after a button press. */
internal class FirstRunSetup(
    private val activity: Activity,
    private val selectRomFolder: () -> Unit,
    private val skipRomFolder: () -> Unit,
    private val selectBios: () -> Unit,
    private val selectFont: () -> Unit,
    private val finish: () -> Unit,
    private val hasBios: () -> Boolean,
    private val hasFont: () -> Boolean
) : FrameLayout(activity) {
    enum class Step { ROM_FOLDER, FIRMWARE }

    private val card = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var step = Step.ROM_FOLDER
    private var busy: String? = null
    val isOpen: Boolean get() = visibility == View.VISIBLE
    val isChoosingRomFolder: Boolean get() = isOpen && step == Step.ROM_FOLDER

    init {
        visibility = View.GONE
        setBackgroundColor(0xff10151d.toInt())
        elevation = dp(24).toFloat()
        isFocusableInTouchMode = true
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val center = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }
        center.addView(card, LinearLayout.LayoutParams(
            minOf(dp(520), activity.resources.displayMetrics.widthPixels - dp(40)), -2))
        scroll.addView(center)
        addView(scroll, LayoutParams(-1, -1))
    }

    fun show(next: Step) {
        step = next
        busy = null
        visibility = View.VISIBLE
        render()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val widthAvailable = (width - dp(40)).coerceAtLeast(dp(240))
        card.layoutParams = card.layoutParams.apply { this.width = minOf(dp(520), widthAvailable) }
    }

    fun close() { visibility = View.GONE }

    fun setBusy(message: String?) {
        busy = message
        if (isOpen && step == Step.FIRMWARE) render()
    }

    fun refreshFirmware() {
        if (isOpen && step == Step.FIRMWARE) render()
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isOpen) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                activity.currentFocus?.performClick()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN)
                activity.currentFocus?.focusSearch(if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP)
                    View.FOCUS_UP else View.FOCUS_DOWN)?.requestFocus()
            return true
        }
        return true
    }

    fun back() {
        if (busy != null) return
        if (step == Step.ROM_FOLDER) skipRomFolder() else finish()
    }

    private fun render() {
        card.removeAllViews()
        text("KAIRO98", 18f, Color.WHITE, bold = true, bottom = 26)
        text(if (step == Step.ROM_FOLDER) "SETUP  ·  1 OF 2" else "SETUP  ·  2 OF 2",
            13f, 0xff80d4df.toInt(), bottom = 10)
        if (step == Step.ROM_FOLDER) {
            text("Choose a ROM folder", 30f, Color.WHITE, bold = true, bottom = 14)
            text("Select the folder containing your PC-98 games. Kairo98 will scan its disk images and ZIP files.",
                17f, 0xffbdc8d5.toInt(), bottom = 28)
            button("Select ROM folder", "Find games on this device", true, selectRomFolder)
            button("Skip for now", "You can choose one from the library later", false, skipRomFolder)
        } else {
            text("Optional BIOS and font", 30f, Color.WHITE, bold = true, bottom = 14)
            text("Import them now, or add them later from Library → Machine.",
                17f, 0xffbdc8d5.toInt(), bottom = 28)
            button("Import BIOS ROM", if (hasBios()) "Imported" else "Not set",
                false, selectBios, busy == null)
            button("Import Font BMP", if (hasFont()) "Imported" else "Using generated font",
                false, selectFont, busy == null)
            busy?.let { text(it, 15f, 0xff80d4df.toInt(), bottom = 10) }
            button(if (hasBios() || hasFont()) "Continue to library" else "Skip for now",
                "Open the game library", true, finish, busy == null)
        }
        if (busy == null) card.post {
            if (isOpen && activity.currentFocus?.isDescendantOf(card) != true)
                (0 until card.childCount).map(card::getChildAt).firstOrNull { it.isFocusable }
                    ?.requestFocus()
        }
    }

    private fun View.isDescendantOf(ancestor: View): Boolean {
        var parent = parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false,
                     bottom: Int = 0) {
        card.addView(TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) })
    }

    private fun button(title: String, subtitle: String, primary: Boolean,
                       action: () -> Unit, enabled: Boolean = true) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(if (primary) 0xff304e63.toInt() else 0xff202a36.toInt())
                cornerRadius = dp(9).toFloat()
            }
            isEnabled = enabled
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else .55f
            contentDescription = "$title. $subtitle"
            setOnClickListener { action() }
        }
        row.addView(TextView(activity).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        row.addView(TextView(activity).apply {
            text = subtitle
            textSize = 14f
            setTextColor(0xffb8c7d5.toInt())
        })
        card.addView(row, LinearLayout.LayoutParams(-1, dp(76)).apply {
            bottomMargin = dp(10)
        })
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
}
