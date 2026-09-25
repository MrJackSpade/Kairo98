package com.mrjackspade.kairo98

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.roundToInt

/** Touch buttons use the same virtual controls and per-game bindings as a physical controller. */
class OnScreenControls(
    private val activity: Activity,
    private val root: FrameLayout,
    private val mapper: GamepadMapper,
    private val preferences: SharedPreferences,
    private val onVisibilityChanged: () -> Unit
) {
    private enum class LayoutOrientation(val label: String, val preference: String) {
        PORTRAIT("Portrait", "onscreen_layout_portrait_v2"),
        LANDSCAPE("Landscape", "onscreen_layout_landscape_v2")
    }

    private data class Spec(val id: String, val label: String, val group: String,
                            val x: Float, val y: Float, val shown: Boolean)
    private data class State(var shown: Boolean, var x: Float, var y: Float)

    private val specs = listOf(
        Spec("up", "↑", "D-PAD", .16f, .67f, true),
        Spec("down", "↓", "D-PAD", .16f, .83f, true),
        Spec("left", "←", "D-PAD", .09f, .75f, true),
        Spec("right", "→", "D-PAD", .23f, .75f, true),
        Spec("a", "A", "FACE BUTTONS", .88f, .82f, true),
        Spec("b", "B", "FACE BUTTONS", .94f, .74f, true),
        Spec("x", "X", "FACE BUTTONS", .82f, .74f, true),
        Spec("y", "Y", "FACE BUTTONS", .88f, .66f, true),
        Spec("l1", "L1", "SHOULDERS", .08f, .39f, false),
        Spec("r1", "R1", "SHOULDERS", .92f, .39f, false),
        Spec("l2", "L2", "SHOULDERS", .08f, .51f, false),
        Spec("r2", "R2", "SHOULDERS", .92f, .51f, false),
        Spec("start", "Start", "SYSTEM", .57f, .91f, true),
        Spec("select", "Select", "SYSTEM", .43f, .91f, true),
        Spec("menu", "Menu", "SYSTEM", .50f, .18f, true),
        Spec("rsup", "R↑", "RIGHT STICK", .69f, .35f, false),
        Spec("rsdown", "R↓", "RIGHT STICK", .69f, .47f, false),
        Spec("rsleft", "R←", "RIGHT STICK", .63f, .41f, false),
        Spec("rsright", "R→", "RIGHT STICK", .75f, .41f, false)
    )
    private val portraitPositions = mapOf(
        "up" to (.18f to .62f), "down" to (.18f to .78f),
        "left" to (.09f to .70f), "right" to (.27f to .70f),
        "a" to (.84f to .77f), "b" to (.92f to .69f),
        "x" to (.76f to .69f), "y" to (.84f to .61f),
        "l1" to (.12f to .44f), "r1" to (.88f to .44f),
        "l2" to (.12f to .53f), "r2" to (.88f to .53f),
        "start" to (.59f to .91f), "select" to (.41f to .91f),
        "menu" to (.50f to .54f),
        "rsup" to (.51f to .66f), "rsdown" to (.51f to .78f),
        "rsleft" to (.45f to .72f), "rsright" to (.57f to .72f)
    )
    private val layouts = LayoutOrientation.entries.associateWith { LinkedHashMap<String, State>() }
    private var orientation = if (activity.resources.configuration.orientation ==
        Configuration.ORIENTATION_PORTRAIT) LayoutOrientation.PORTRAIT
        else LayoutOrientation.LANDSCAPE
    private val states: LinkedHashMap<String, State> get() = layouts.getValue(orientation)
    private val buttons = LinkedHashMap<String, TextView>()
    private val heldPointers = HashMap<String, MutableSet<Int>>()
    private val overlay = FrameLayout(activity).apply {
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        setMotionEventSplittingEnabled(true)
    }
    private var page: LinearLayout? = null
    private var settingsBody: LinearLayout? = null
    private var settingsScroll: ScrollView? = null
    private var settingsTitle: TextView? = null
    private var arrangement: FrameLayout? = null
    private var requestedVisible = false
    private var enabled = preferences.getBoolean("onscreen_enabled",
        InputDevice.getDeviceIds().none { id ->
            InputDevice.getDevice(id)?.let { device ->
                !device.isVirtual && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                    device.supportsSource(InputDevice.SOURCE_JOYSTICK))
            } == true
        })

    val isOpen: Boolean get() = page != null

    init {
        loadLayouts()
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        specs.forEach { spec ->
            buttons[spec.id] = controlView(spec, false).also {
                overlay.addView(it)
            }
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionAll() }
        root.post(::positionAll)
    }

    fun refreshVisibility(playing: Boolean) {
        requestedVisible = playing
        val show = playing && enabled && !isOpen
        if (!show) {
            mapper.releaseOnScreen()
            heldPointers.values.forEach(MutableSet<Int>::clear)
            buttons.values.forEach { it.isPressed = false; it.alpha = .72f }
        }
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) positionAll()
    }

    fun hitTest(x: Float, y: Float): Boolean {
        if (overlay.visibility != View.VISIBLE) return false
        return buttons.values.any { view ->
            view.visibility == View.VISIBLE && x >= view.left && x < view.right &&
                y >= view.top && y < view.bottom
        }
    }

    fun show() {
        if (isOpen) return
        positionAll()
        val settings = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff10151d.toInt())
            isFocusableInTouchMode = true
            elevation = dp(20).toFloat()
        }
        page = settings
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        bar.addView(TextView(activity).apply {
            text = "‹  Back"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(dp(100), dp(48)))
        settingsTitle = TextView(activity).apply {
            text = "Controls · ${orientation.label}"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(settingsTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        settings.addView(bar)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(20))
        }
        settingsBody = body
        val scroll = ScrollView(activity).apply { addView(body) }
        settingsScroll = scroll
        settings.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderSettings()
        settings.requestFocus()
        onVisibilityChanged()
    }

    fun back() {
        if (arrangement != null) {
            root.removeView(arrangement)
            arrangement = null
            page?.visibility = View.VISIBLE
            renderSettings()
            return
        }
        close()
    }

    fun close() {
        arrangement?.let(root::removeView)
        arrangement = null
        page?.let(root::removeView)
        page = null
        settingsBody = null
        settingsScroll = null
        settingsTitle = null
        refreshVisibility(requestedVisible)
        onVisibilityChanged()
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isOpen) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        return false
    }

    private fun renderSettings() {
        val body = settingsBody ?: return
        val scrollY = settingsScroll?.scrollY ?: 0
        body.removeAllViews()
        note(body, "Edit ${orientation.label.lowercase()} buttons here. Positions and visible buttons are saved separately for portrait and landscape. Each button follows the Global or This game controller mapping.")
        row(body, "Show on-screen controls", if (enabled) "On" else "Off") {
            enabled = !enabled
            preferences.edit().putBoolean("onscreen_enabled", enabled).apply()
            renderSettings()
        }
        row(body, "Arrange controls", "Drag buttons into place") { showArrangement() }
        for (group in specs.map { it.group }.distinct()) {
            section(body, group)
            specs.filter { it.group == group }.forEach { spec ->
                val state = states.getValue(spec.id)
                row(body, controlName(spec.id), if (state.shown) "On" else "Off") {
                    state.shown = !state.shown
                    saveLayout()
                    positionAll()
                    renderSettings()
                }
            }
        }
        section(body, "RESET")
        row(body, "Reset positions", "Restore the standard layout") {
            specs.forEach { spec ->
                val default = defaultPosition(spec, orientation)
                states.getValue(spec.id).apply { x = default.first; y = default.second }
            }
            saveLayout()
            positionAll()
            renderSettings()
        }
        row(body, "Reset visible buttons", "Restore the standard selection") {
            specs.forEach { spec -> states.getValue(spec.id).shown = spec.shown }
            saveLayout()
            positionAll()
            renderSettings()
        }
        settingsScroll?.post { settingsScroll?.scrollTo(0, scrollY) }
    }

    private fun showArrangement() {
        page?.visibility = View.GONE
        val layer = FrameLayout(activity).apply {
            setBackgroundColor(0x33000000)
            isClickable = true
            elevation = dp(22).toFloat()
        }
        arrangement = layer
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        for (spec in specs.filter { states.getValue(it.id).shown }) {
            layer.addView(controlView(spec, true))
        }
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(0xee10151d.toInt())
        }
        bar.addView(TextView(activity).apply {
            text = "Drag controls to reposition"
            textSize = 17f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(TextView(activity).apply {
            text = "Done"
            textSize = 18f
            setTextColor(0xff91dfe8.toInt())
            gravity = Gravity.CENTER
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(dp(76), -1))
        layer.addView(bar, FrameLayout.LayoutParams(-1, dp(50), Gravity.TOP))
        layer.post(::positionAll)
    }

    private fun controlView(spec: Spec, editing: Boolean): TextView {
        val held = if (editing) HashSet<Int>() else heldPointers.getOrPut(spec.id) { HashSet() }
        return TextView(activity).apply {
            tag = spec.id
            text = spec.label
            textSize = if (spec.label.length > 2) 11f else 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = buttonBackground(editing)
            contentDescription = controlName(spec.id)
            isClickable = true
            if (editing) {
                var rawX = 0f
                var rawY = 0f
                var startX = 0f
                var startY = 0f
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            rawX = event.rawX; rawY = event.rawY
                            startX = states.getValue(spec.id).x * root.width
                            startY = states.getValue(spec.id).y * root.height
                            view.alpha = 1f
                        }
                        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                            val size = dp(54)
                            val x = (startX + event.rawX - rawX)
                                .coerceIn(size / 2f, (root.width - size / 2f).coerceAtLeast(size / 2f))
                            val y = (startY + event.rawY - rawY)
                                .coerceIn(dp(50) + size / 2f,
                                    (root.height - size / 2f).coerceAtLeast(dp(50) + size / 2f))
                            states.getValue(spec.id).apply {
                                this.x = x / root.width.coerceAtLeast(1)
                                this.y = y / root.height.coerceAtLeast(1)
                            }
                            position(view, states.getValue(spec.id), root.width, root.height)
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                view.alpha = .85f
                                saveLayout()
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            states.getValue(spec.id).apply {
                                x = startX / root.width.coerceAtLeast(1)
                                y = startY / root.height.coerceAtLeast(1)
                            }
                            position(view, states.getValue(spec.id), root.width, root.height)
                            view.alpha = .85f
                        }
                    }
                    true
                }
            } else setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val pointer = event.getPointerId(event.actionIndex)
                        if (held.add(pointer)) mapper.pressVirtual(spec.id,
                            "onscreen:${spec.id}:$pointer")
                        view.isPressed = true
                        view.alpha = 1f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        val pointer = event.getPointerId(event.actionIndex)
                        if (held.remove(pointer)) mapper.releaseVirtual(
                            "onscreen:${spec.id}:$pointer")
                        view.isPressed = held.isNotEmpty()
                        view.alpha = if (held.isEmpty()) .72f else 1f
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        held.toList().forEach { mapper.releaseVirtual("onscreen:${spec.id}:$it") }
                        held.clear()
                        view.isPressed = false
                        view.alpha = .72f
                    }
                }
                true
            }
            alpha = if (editing) .85f else .72f
        }
    }

    private fun positionAll() {
        if (root.width <= 0 || root.height <= 0) return
        val next = if (root.height > root.width) LayoutOrientation.PORTRAIT
            else LayoutOrientation.LANDSCAPE
        if (next != orientation) {
            orientation = next
            mapper.releaseOnScreen()
            heldPointers.values.forEach(MutableSet<Int>::clear)
            buttons.values.forEach { it.isPressed = false; it.alpha = .72f }
            settingsTitle?.text = "Controls · ${orientation.label}"
            if (isOpen) renderSettings()
            rebuildArrangementButtons()
        }
        buttons.forEach { (id, view) ->
            val state = states.getValue(id)
            view.visibility = if (state.shown) View.VISIBLE else View.GONE
            position(view, state, root.width, root.height)
        }
        arrangement?.let { layer ->
            for (index in 0 until layer.childCount) {
                val view = layer.getChildAt(index) as? TextView ?: continue
                val id = view.tag as? String ?: continue
                position(view, states.getValue(id), root.width, root.height)
            }
        }
    }

    private fun rebuildArrangementButtons() {
        val layer = arrangement ?: return
        val bar = layer.getChildAt(layer.childCount - 1)
        layer.removeAllViews()
        specs.filter { states.getValue(it.id).shown }.forEach { spec ->
            layer.addView(controlView(spec, true))
        }
        layer.addView(bar)
    }

    private fun position(view: View, state: State, width: Int, height: Int) {
        val size = dp(54)
        val left = (state.x * width - size / 2f).roundToInt().coerceIn(0, (width - size).coerceAtLeast(0))
        val top = (state.y * height - size / 2f).roundToInt().coerceIn(0, (height - size).coerceAtLeast(0))
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(size, size)
        if (params.leftMargin == left && params.topMargin == top && params.width == size) return
        params.width = size; params.height = size
        params.gravity = Gravity.TOP or Gravity.START
        params.leftMargin = left; params.topMargin = top
        view.layoutParams = params
    }

    private fun loadLayouts() {
        for (layout in LayoutOrientation.entries) {
            val saved = preferences.getString(layout.preference, null)
                ?: if (layout == orientation) preferences.getString("onscreen_layout_v1", null)
                    else null
            val stored = try { JSONObject(saved ?: "{}") } catch (_: Exception) { JSONObject() }
            val controls = stored.optJSONObject("controls")
            for (spec in specs) {
                val item = controls?.optJSONObject(spec.id)
                val default = defaultPosition(spec, layout)
                val x = item?.optDouble("x", default.first.toDouble())?.toFloat() ?: default.first
                val y = item?.optDouble("y", default.second.toDouble())?.toFloat() ?: default.second
                layouts.getValue(layout)[spec.id] = State(
                    item?.optBoolean("shown", spec.shown) ?: spec.shown,
                    if (x.isFinite()) x.coerceIn(0f, 1f) else default.first,
                    if (y.isFinite()) y.coerceIn(0f, 1f) else default.second)
            }
        }
    }

    private fun defaultPosition(spec: Spec, layout: LayoutOrientation): Pair<Float, Float> =
        if (layout == LayoutOrientation.PORTRAIT) portraitPositions.getValue(spec.id)
        else spec.x to spec.y

    private fun saveLayout() {
        val controls = JSONObject()
        specs.forEach { spec ->
            val state = states.getValue(spec.id)
            controls.put(spec.id, JSONObject().put("shown", state.shown)
                .put("x", state.x.toDouble()).put("y", state.y.toDouble()))
        }
        preferences.edit().putString(orientation.preference,
            JSONObject().put("version", 2).put("controls", controls).toString()).apply()
    }

    private fun controlName(id: String) = when (id) {
        "up" -> "D-pad up"; "down" -> "D-pad down"
        "left" -> "D-pad left"; "right" -> "D-pad right"
        "rsup" -> "Right stick up"; "rsdown" -> "Right stick down"
        "rsleft" -> "Right stick left"; "rsright" -> "Right stick right"
        else -> id.uppercase()
    }

    private fun buttonBackground(editing: Boolean) = GradientDrawable().apply {
        setColor(if (editing) 0xdd304e63.toInt() else 0xb5253344.toInt())
        cornerRadius = dp(15).toFloat()
        setStroke(dp(1), 0xccffffff.toInt())
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(TextView(activity).apply {
            text = title; textSize = 13f; setTextColor(0xff91c6d8.toInt())
            setPadding(dp(10), dp(18), dp(10), dp(8))
        })
    }

    private fun note(parent: LinearLayout, message: String) {
        parent.addView(TextView(activity).apply {
            text = message; textSize = 15f; setTextColor(0xffb7c2cf.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(16))
        })
    }

    private fun row(parent: LinearLayout, title: String, value: String, action: () -> Unit) {
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = GradientDrawable().apply {
                setColor(0xff202a36.toInt()); cornerRadius = dp(6).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            contentDescription = "$title. $value"
        }
        line.addView(TextView(activity).apply {
            text = title; textSize = 17f; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(TextView(activity).apply {
            text = value; textSize = 15f; setTextColor(0xffa6e3ec.toInt())
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(-2, -2))
        parent.addView(line, LinearLayout.LayoutParams(-1, dp(58)).apply {
            bottomMargin = dp(3)
        })
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
}
