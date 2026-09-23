package com.mrjackspade.kairo98

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Optional guest keyboard. Key ownership is independent of physical and controller keys. */
internal class Pc98KeyboardPanel(
    context: Context,
    private val input: InputRouter,
    private val onClose: () -> Unit
) : LinearLayout(context) {
    private data class Key(val label: String, val scan: Int, val width: Int = 40)
    private val handler = Handler(Looper.getMainLooper())
    private val latched = mutableSetOf<Int>()
    private val modifierViews = mutableMapOf<Int, TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xff171d27.toInt())
        elevation = dp(14).toFloat()
        visibility = View.GONE

        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(8), dp(5))
            addView(TextView(context).apply {
                text = "PC-98 keyboard"
                textSize = 16f
                setTextColor(Color.WHITE)
            }, LayoutParams(0, dp(34), 1f))
            addView(TextView(context).apply {
                text = "Close  ×"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(0xffa6e3ec.toInt())
                isClickable = true
                setOnClickListener { onClose() }
            }, LayoutParams(dp(76), dp(34)))
        }
        addView(header)

        val content = LinearLayout(context).apply { orientation = VERTICAL }
        val horizontal = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(content)
        }
        addView(ScrollView(context).apply {
            isFillViewport = true
            addView(horizontal)
        }, LayoutParams(-1, 0, 1f))

        row(content, listOf(Key("Stop", 0x60, 50), Key("Copy", 0x61, 50)) +
            (0..9).map { Key("F${it + 1}", 0x62 + it) })
        row(content, listOf(Key("Esc", 0x00, 50)) +
            (1..9).map { Key("$it", it) } + listOf(Key("0", 0x0a), Key("-", 0x0b),
                Key("^", 0x0c), Key("\\", 0x0d), Key("Back", 0x0e, 54)))
        row(content, listOf(Key("Tab", 0x0f, 54)) +
            "QWERTYUIOP".mapIndexed { index, char -> Key("$char", 0x10 + index) } +
            listOf(Key("@", 0x1a), Key("[", 0x1b), Key("Enter", 0x1c, 62)))
        row(content, listOf(Key("Caps", 0x71, 54)) +
            "ASDFGHJKL".mapIndexed { index, char -> Key("$char", 0x1d + index) } +
            listOf(Key(";", 0x26), Key(":", 0x27), Key("]", 0x28)))
        row(content, listOf(Key("Shift", 0x70, 58)) +
            "ZXCVBNM".mapIndexed { index, char -> Key("$char", 0x29 + index) } +
            listOf(Key(",", 0x30), Key(".", 0x31), Key("/", 0x32), Key("_", 0x33),
                Key("Shift", 0x7d, 58)))
        row(content, listOf(Key("Ctrl", 0x74, 54), Key("Graph", 0x73, 62),
            Key("Nfer", 0x51, 54), Key("Xfer", 0x35, 54), Key("Space", 0x34, 160),
            Key("Kana", 0x72, 54), Key("Ins", 0x38), Key("Del", 0x39)))
        row(content, listOf(Key("Home", 0x3e, 54), Key("Help", 0x3f, 54),
            Key("↑", 0x3a), Key("←", 0x3b), Key("↓", 0x3d), Key("→", 0x3c),
            Key("Pg↑", 0x36), Key("Pg↓", 0x37)) +
            (0..9).map { digit ->
                Key("N$digit", when (digit) {
                    0 -> 0x4e
                    in 1..3 -> 0x49 + digit
                    in 4..6 -> 0x42 + digit
                    else -> 0x3b + digit
                })
            })
    }

    fun close() {
        visibility = View.GONE
        input.releasePrefix("touch-key:")
        input.releasePrefix("touch-mod:")
        latched.clear()
        modifierViews.forEach { (scan, view) -> view.background = keyBackground(false) }
    }

    private fun row(parent: LinearLayout, keys: List<Key>) {
        val line = LinearLayout(context).apply { orientation = HORIZONTAL }
        keys.forEach { key ->
            val modifier = key.scan in setOf(0x70, 0x7d, 0x74, 0x73, 0x72, 0x71)
            val view = TextView(context).apply {
                text = key.label
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = keyBackground(false)
                isClickable = true
                if (modifier) modifierViews[key.scan] = this
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (modifier) toggleModifier(key.scan, this)
                            else input.hold("touch-key:${key.scan}", listOf(key.scan))
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (!modifier) input.release("touch-key:${key.scan}")
                            true
                        }
                        else -> true
                    }
                }
                setOnClickListener {
                    if (modifier) toggleModifier(key.scan, this)
                    else {
                        val owner = "touch-key:${key.scan}"
                        input.hold(owner, listOf(key.scan))
                        handler.postDelayed({ input.release(owner) }, 90)
                    }
                }
            }
            line.addView(view, LayoutParams(dp(key.width), dp(39)).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        parent.addView(line)
    }

    private fun toggleModifier(scan: Int, view: TextView) {
        if (latched.remove(scan)) input.release("touch-mod:$scan")
        else {
            latched.add(scan)
            input.hold("touch-mod:$scan", listOf(scan))
        }
        view.background = keyBackground(scan in latched)
    }

    private fun keyBackground(selected: Boolean) = GradientDrawable().apply {
        setColor(if (selected) 0xff304e63.toInt() else 0xff2a3543.toInt())
        cornerRadius = dp(5).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
