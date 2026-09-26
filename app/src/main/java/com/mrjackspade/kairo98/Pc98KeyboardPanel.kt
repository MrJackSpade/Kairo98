package com.mrjackspade.kairo98

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Fitted, paged guest keyboard. Every key uses the same scan-code input router. */
internal class Pc98KeyboardPanel(
    context: Context,
    private val input: InputRouter,
    private val onClose: () -> Unit,
    private val showClose: Boolean = true,
    private val onSwap: (() -> Unit)? = null
) : LinearLayout(context) {
    private data class Key(
        val label: String,
        val scan: Int,
        val width: Float = 1f,
        val shifted: String? = null,
        val chordShift: Boolean = false
    )
    private enum class Page { ABC, SYMBOLS, PC98 }

    private val handler = Handler(Looper.getMainLooper())
    private val latched = mutableSetOf<Int>()
    private val keyViews = mutableListOf<Pair<Key, TextView>>()
    private val pageViews = mutableMapOf<Page, TextView>()
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val highlightUntil = mutableMapOf<Int, Long>()
    private var lastPressed = emptySet<Int>()
    private val inputListener: () -> Unit = {
        if (Looper.myLooper() == Looper.getMainLooper()) updateHighlights()
        else handler.post { updateHighlights() }
    }
    private var page = Page.ABC

    init {
        orientation = VERTICAL
        if (onSwap != null) gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Ui.SURFACE)
        elevation = dp(14).toFloat()
        visibility = View.GONE

        if (onSwap != null) {
            val brand = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(4), dp(8), dp(4))
            }
            brand.addView(PixelTextView(context).apply { text = "KAIRO98" }, LayoutParams(0, -2, 1f))
            brand.addView(TextView(context).apply {
                text = "\u2191\u2193"
                contentDescription = "Swap game and keyboard screens"
                gravity = Gravity.CENTER
                textSize = 22f
                setTextColor(keyText())
                background = keyBackground(action = true)
                setOnClickListener { onSwap.invoke() }
            }, LayoutParams(dp(54), dp(40)))
            addView(brand, LayoutParams(-1, dp(48)))
        }

        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        for ((target, label) in listOf(Page.ABC to "ABC", Page.SYMBOLS to "?123",
            Page.PC98 to "PC-98")) {
            val tab = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = Ui.SECONDARY
                setTextColor(keyText())
                setOnClickListener { showPage(target) }
            }
            tab.background = keyBackground(action = true)
            pageViews[target] = tab
            header.addView(tab, LayoutParams(0, dp(34), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        if (showClose) {
            header.addView(TextView(context).apply {
                text = "Close ×"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(Ui.ACCENT_SOFT)
                setOnClickListener { onClose() }
            }, LayoutParams(dp(76), dp(34)))
        }
        addView(header, LayoutParams(-1, dp(42)))
        addView(content, if (onSwap == null) LayoutParams(-1, 0, 1f)
            else LayoutParams(-1, dp(320)))
        showPage(Page.ABC)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        input.addListener(inputListener)
        updateHighlights()
    }

    override fun onDetachedFromWindow() {
        input.removeListener(inputListener)
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (onSwap != null) {
            val rowsHeight = minOf(dp(320), (height - dp(90)).coerceAtLeast(0))
            val rowsWidth = minOf(width, dp(900))
            if (content.layoutParams.height != rowsHeight ||
                content.layoutParams.width != rowsWidth)
                content.layoutParams = LayoutParams(rowsWidth, rowsHeight)
        }
    }

    fun close() {
        visibility = View.GONE
        input.releasePrefix("touch-key:")
        input.releasePrefix("touch-mod:")
        latched.clear()
        highlightUntil.clear()
        lastPressed = emptySet()
        updateLegends()
    }

    private fun showPage(target: Page) {
        input.releasePrefix("touch-key:")
        if (page != target) {
            for (shift in listOf(0x70, 0x7d)) {
                if (latched.remove(shift)) input.release("touch-mod:$shift")
            }
        }
        page = target
        content.removeAllViews()
        keyViews.clear()
        val rows = when (page) {
            Page.ABC -> alphabetRows()
            Page.SYMBOLS -> symbolRows()
            Page.PC98 -> pc98Rows()
        }
        rows.forEach(::row)
        pageViews.forEach { (name, view) -> view.isActivated = name == page }
        updateLegends()
    }

    private fun alphabetRows(): List<List<Key>> = listOf(
        listOf(Key("Esc", 0x00, 1.3f)) +
            listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", "0")
                .mapIndexed { index, shifted -> Key("${(index + 1) % 10}", index + 1, shifted = shifted) } +
            Key("Back", 0x0e, 1.5f),
        listOf(Key("Tab", 0x0f, 1.3f)) +
            "qwertyuiop".mapIndexed { index, char -> Key("$char", 0x10 + index) } +
            listOf(Key("@", 0x1a, shifted = "~"), Key("[", 0x1b, shifted = "{"),
                Key("Enter", 0x1c, 1.5f)),
        listOf(Key("Caps", 0x71, 1.4f)) +
            "asdfghjkl".mapIndexed { index, char -> Key("$char", 0x1d + index) } +
            listOf(Key(";", 0x26, shifted = "+"), Key(":", 0x27, shifted = "*"),
                Key("]", 0x28, shifted = "}")),
        listOf(Key("Shift", 0x70, 1.5f)) +
            "zxcvbnm".mapIndexed { index, char -> Key("$char", 0x29 + index) } +
            listOf(Key(",", 0x30, shifted = "<"), Key(".", 0x31, shifted = ">"),
                Key("/", 0x32, shifted = "?"), Key("Shift", 0x7d, 1.5f)),
        listOf(Key("Ctrl", 0x74), Key("Graph", 0x73), Key("Nfer", 0x51),
            Key("Xfer", 0x35), Key("Space", 0x34, 4f), Key("Kana", 0x72),
            Key("Ins", 0x38), Key("Del", 0x39))
    )

    private fun symbolRows(): List<List<Key>> = listOf(
        listOf(Key("Esc", 0x00, 1.3f)) +
            (1..9).map { Key("$it", it) } + Key("0", 0x0a) + Key("Back", 0x0e, 1.5f),
        listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")")
            .mapIndexed { index, label -> Key(label, index + 1, chordShift = true) } +
            Key("Enter", 0x1c, 1.5f),
        listOf(Key("-", 0x0b), Key("^", 0x0c), Key("\\", 0x0d),
            Key("@", 0x1a), Key("[", 0x1b), Key("]", 0x28), Key(";", 0x26),
            Key(":", 0x27), Key(",", 0x30), Key(".", 0x31), Key("/", 0x32)),
        listOf(Key("=", 0x0b, chordShift = true), Key("`", 0x0c, chordShift = true),
            Key("|", 0x0d, chordShift = true), Key("~", 0x1a, chordShift = true),
            Key("{", 0x1b, chordShift = true), Key("}", 0x28, chordShift = true),
            Key("+", 0x26, chordShift = true), Key("*", 0x27, chordShift = true),
            Key("<", 0x30, chordShift = true), Key(">", 0x31, chordShift = true),
            Key("?", 0x32, chordShift = true), Key("_", 0x33, chordShift = true)),
        listOf(Key("Tab", 0x0f), Key("Space", 0x34, 5f), Key("Enter", 0x1c, 1.5f),
            Key("Ins", 0x38), Key("Del", 0x39))
    )

    private fun pc98Rows(): List<List<Key>> = listOf(
        listOf(Key("Stop", 0x60, 1.4f), Key("Copy", 0x61, 1.4f)) +
            (0..9).map { Key("F${it + 1}", 0x62 + it) },
        (0..4).map { Key("VF${it + 1}", 0x52 + it) } +
            listOf(Key("Home", 0x3e), Key("Help", 0x3f), Key("Roll↑", 0x36),
                Key("Roll↓", 0x37), Key("Ins", 0x38), Key("Del", 0x39)),
        listOf(Key("7", 0x42), Key("8", 0x43), Key("9", 0x44),
            Key("/", 0x41), Key("*", 0x45), Key("-", 0x40)),
        listOf(Key("4", 0x46), Key("5", 0x47), Key("6", 0x48),
            Key("+", 0x49), Key("=", 0x4d), Key("↑", 0x3a)),
        listOf(Key("1", 0x4a), Key("2", 0x4b), Key("3", 0x4c),
            Key("0", 0x4e), Key(",", 0x4f), Key(".", 0x50),
            Key("←", 0x3b), Key("↓", 0x3d), Key("→", 0x3c))
    )

    private fun row(keys: List<Key>) {
        val line = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (key in keys) {
            val modifier = key.scan in MODIFIERS
            // Letters, digits, and symbols sit on the lighter key; named keys are darker.
            val action = modifier || key.label.length > 1
            val view = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(keyText())
                setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, TypedValue.COMPLEX_UNIT_SP)
                isClickable = true
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (modifier) toggleModifier(key.scan)
                            else press(key)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (!modifier) release(key)
                            true
                        }
                        else -> true
                    }
                }
                setOnClickListener {
                    if (modifier) toggleModifier(key.scan)
                    else {
                        press(key)
                        handler.postDelayed({ release(key) }, 90)
                    }
                }
            }
            view.background = keyBackground(action)
            keyViews.add(key to view)
            line.addView(view, LayoutParams(0, -1, key.width).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        content.addView(line, LayoutParams(-1, 0, 1f))
    }

    private fun press(key: Key) {
        highlightUntil[key.scan] = SystemClock.uptimeMillis() + 90
        input.hold("touch-key:${key.scan}", if (key.chordShift) listOf(0x70, key.scan)
            else listOf(key.scan))
    }

    private fun release(key: Key) {
        input.release("touch-key:${key.scan}")
        val delay = (highlightUntil[key.scan] ?: 0L) - SystemClock.uptimeMillis()
        if (delay > 0) handler.postDelayed({ updateHighlights() }, delay)
    }

    private fun toggleModifier(scan: Int) {
        if (latched.remove(scan)) input.release("touch-mod:$scan")
        else {
            latched.add(scan)
            input.hold("touch-mod:$scan", listOf(scan))
        }
        updateLegends()
    }

    private fun updateLegends() {
        val shifted = 0x70 in latched || 0x7d in latched
        val caps = 0x71 in latched
        for ((key, view) in keyViews) {
            val letter = key.label.length == 1 && key.label[0] in 'a'..'z'
            view.text = when {
                letter && (shifted xor caps) -> key.label.uppercase()
                key.shifted != null && shifted -> key.shifted
                else -> key.label
            }
            view.isActivated = key.scan in latched
        }
        updateHighlights()
    }

    private fun updateHighlights() {
        val pressed = input.pressedScans()
        val now = SystemClock.uptimeMillis()
        for (scan in pressed - lastPressed)
            highlightUntil[scan] = now + 90
        for (scan in lastPressed - pressed) {
            val delay = (highlightUntil[scan] ?: 0L) - now
            if (delay > 0) handler.postDelayed({ updateHighlights() }, delay)
        }
        lastPressed = pressed
        val shifted = 0x70 in pressed || 0x7d in pressed
        highlightUntil.entries.removeAll { it.value <= now }
        for ((key, view) in keyViews) {
            val matchingShift = !key.chordShift || shifted
            view.isPressed = matchingShift &&
                (key.scan in pressed || (highlightUntil[key.scan] ?: 0L) > now)
        }
    }

    /** Latched modifiers and the current page fill with the accent; everything else stays quiet. */
    private fun keyBackground(action: Boolean = false): RippleDrawable {
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_activated), keyShape(Ui.ACCENT))
            addState(intArrayOf(android.R.attr.state_pressed), keyShape(Ui.SELECTED))
            addState(intArrayOf(), if (action) keyShape(Ui.SURFACE, Ui.LINE) else keyShape(Ui.RAISED))
        }
        return RippleDrawable(ColorStateList.valueOf(0x80a8eef1.toInt()), states, null)
    }

    private fun keyText() = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()), intArrayOf(Ui.ON_ACCENT, Ui.TEXT))

    private fun keyShape(color: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(5).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val MODIFIERS = setOf(0x70, 0x7d, 0x71, 0x72, 0x73, 0x74)
    }
}
