package com.mrjackspade.kairo98

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/** Full-screen physical-to-virtual and virtual-to-guest controller mapping page. */
class ControllerEditor(
    private val activity: Activity,
    private val root: FrameLayout,
    private val load: (LibraryEntry?) -> List<ControllerBinding>,
    private val save: (LibraryEntry?, List<ControllerBinding>) -> Unit,
    private val reset: (LibraryEntry?) -> Unit,
    private val loadPhysical: () -> List<PhysicalControllerBinding>,
    private val savePhysical: (List<PhysicalControllerBinding>) -> Unit,
    private val resetPhysical: () -> Unit,
    private val getDeadZone: () -> Float,
    private val setDeadZone: (Float) -> Unit,
    private val onVisibilityChanged: () -> Unit
) {
    private enum class Stage { LIST, SOURCES, CAPTURE, MANUAL, TARGET, VIRTUAL, KEYS, JOYSTICK, ACTIONS, DEAD_ZONE, RESET }
    private data class Source(val group: String, val name: String, val input: String)

    private lateinit var page: LinearLayout
    private lateinit var heading: TextView
    private lateinit var body: LinearLayout
    private lateinit var footer: LinearLayout
    private var game: LibraryEntry? = null
    private var scope: LibraryEntry? = null
    private var physicalScope = false
    private var stage = Stage.LIST
    private var selectedInput = ""
    private val selectedScans = linkedSetOf<Int>()
    private var deadZoneSlider: SeekBar? = null
    private val captureBaseline = HashMap<Pair<Int, Int>, Float>()
    var isOpen = false
        private set

    fun show(gameEntry: LibraryEntry?) {
        if (isOpen) close()
        game = gameEntry
        scope = gameEntry
        physicalScope = false
        stage = Stage.LIST
        page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff10151d.toInt())
            isFocusableInTouchMode = true
            elevation = dp(20).toFloat()
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.BLACK)
        }
        bar.addView(TextView(activity).apply {
            text = "‹  Back"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(dp(100), dp(48)))
        heading = TextView(activity).apply {
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(heading, LinearLayout.LayoutParams(0, dp(48), 1f))
        page.addView(bar)
        body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
        }
        page.addView(ScrollView(activity).apply {
            isFillViewport = true
            addView(body)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(4), dp(18), dp(8))
        }
        page.addView(footer)
        isOpen = true
        render()
        page.requestFocus()
        onVisibilityChanged()
    }

    fun close() {
        if (!isOpen) return
        root.removeView(page)
        isOpen = false
        captureBaseline.clear()
        onVisibilityChanged()
    }

    fun back() {
        if (!isOpen) return
        stage = when (stage) {
            Stage.LIST -> { close(); return }
            Stage.SOURCES, Stage.DEAD_ZONE, Stage.RESET -> Stage.LIST
            Stage.CAPTURE, Stage.MANUAL -> Stage.SOURCES
            Stage.TARGET -> Stage.LIST
            Stage.VIRTUAL, Stage.KEYS, Stage.JOYSTICK, Stage.ACTIONS -> Stage.TARGET
        }
        render()
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!isOpen) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        if (stage == Stage.CAPTURE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                (KeyEvent.isGamepadButton(event.keyCode) ||
                    event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                    event.isFromSource(InputDevice.SOURCE_JOYSTICK))) {
                chooseInput("button:" + event.keyCode)
            }
            return true
        }
        val control = if (KeyEvent.isGamepadButton(event.keyCode) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK))
            loadPhysical().firstOrNull { it.input == "button:${event.keyCode}" }?.control
        else null
        if (control == "b" || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        if (control == "a" || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                activity.currentFocus?.performClick()
            return true
        }
        val direction = when (control) {
            "up" -> View.FOCUS_UP
            "down" -> View.FOCUS_DOWN
            "left" -> View.FOCUS_LEFT
            "right" -> View.FOCUS_RIGHT
            else -> null
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN)
                activity.currentFocus?.focusSearch(direction)?.requestFocus()
            return true
        }
        return false
    }

    fun captureMotion(event: MotionEvent): Boolean {
        if (!isOpen) return false
        if (stage != Stage.CAPTURE) return true
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return true
        val axes = event.device?.motionRanges?.filter {
            it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        }?.map { it.axis }?.distinct() ?: emptyList()
        for (axis in axes) {
            val value = event.getAxisValue(axis)
            val id = event.deviceId to axis
            val baseline = captureBaseline[id]
            val trigger = axis == MotionEvent.AXIS_LTRIGGER || axis == MotionEvent.AXIS_RTRIGGER ||
                axis == MotionEvent.AXIS_BRAKE || axis == MotionEvent.AXIS_GAS
            if (baseline == null) {
                captureBaseline[id] = value
                if (abs(value) >= 0.75f && (!trigger || value > 0.75f)) {
                    chooseInput(axisInput(axis, value))
                    return true
                }
            } else {
                if (abs(value) < 0.25f) captureBaseline[id] = value
                if (abs(value) >= 0.75f && abs(value - baseline) >= 0.75f &&
                    (!trigger || value > 0.75f)) {
                    chooseInput(axisInput(axis, value))
                    return true
                }
            }
        }
        return true
    }

    private fun axisInput(axis: Int, value: Float): String {
        val kind = if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) "hat" else "axis"
        return kind + ":" + axis + ":" + if (value > 0) "+" else "-"
    }

    private fun chooseInput(input: String) {
        selectedInput = input
        captureBaseline.clear()
        stage = Stage.TARGET
        render()
    }

    private fun render() {
        body.removeAllViews()
        footer.removeAllViews()
        heading.text = when (stage) {
            Stage.LIST -> "Controller mappings"
            Stage.SOURCES -> "Add controller input"
            Stage.CAPTURE -> "Listen for input"
            Stage.MANUAL -> "Enter input code"
            Stage.TARGET -> "Choose target"
            Stage.VIRTUAL -> "Virtual controller"
            Stage.KEYS -> "PC-98 keys"
            Stage.JOYSTICK -> "PC-98 joystick 1"
            Stage.ACTIONS -> "Emulator actions"
            Stage.DEAD_ZONE -> "Stick dead zone"
            Stage.RESET -> "Reset bindings"
        }
        when (stage) {
            Stage.LIST -> renderList()
            Stage.SOURCES -> renderSources()
            Stage.CAPTURE -> renderCapture()
            Stage.MANUAL -> renderManual()
            Stage.TARGET -> renderTarget()
            Stage.VIRTUAL -> renderVirtual()
            Stage.KEYS -> renderKeys()
            Stage.JOYSTICK -> renderJoystick()
            Stage.ACTIONS -> renderActions()
            Stage.DEAD_ZONE -> renderDeadZone()
            Stage.RESET -> renderReset()
        }
    }

    private fun renderList() {
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(tab("Physical", physicalScope) { physicalScope = true; scope = null; render() },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        tabs.addView(tab("Global", !physicalScope && scope == null) {
            physicalScope = false; scope = null; render()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        if (game != null) tabs.addView(tab("This game", !physicalScope && scope != null) {
            physicalScope = false; scope = game; render()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(tabs)
        if (physicalScope) {
            section("PHYSICAL CONTROLLER → VIRTUAL CONTROLLER")
            val controllers = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
                .filter { it.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                    it.supportsSource(InputDevice.SOURCE_JOYSTICK) }
                .joinToString { it.name }
            note(if (controllers.isEmpty()) "No controller connected. You can still assign controls."
                else "Connected: " + controllers)
            row("Add another input", "Choose from a list or capture it", true) {
                stage = Stage.SOURCES
                render()
            }
            val bindings = loadPhysical().associateBy { it.input }
            val shown = HashSet<String>()
            var group = ""
            for (source in commonSources()) {
                if (source.group != group) {
                    group = source.group
                    section(group.uppercase())
                }
                shown.add(source.input)
                row(source.name, (bindings[source.input]?.control?.let(::virtualLabel) ?: "Unassigned") + "  ▾", true) {
                    chooseInput(source.input)
                }
            }
            val custom = bindings.values.filter { it.input !in shown }
            if (custom.isNotEmpty()) section("OTHER INPUTS")
            for (binding in custom) row(inputLabel(binding.input), virtualLabel(binding.control) + "  ▾", true) {
                chooseInput(binding.input)
            }
        } else {
            section(if (scope == null) "VIRTUAL CONTROLLER → GLOBAL PC-98 PROFILE"
                else "VIRTUAL CONTROLLER → GAME PROFILE")
            val bindings = load(scope).associateBy { it.input }
            for (control in PhysicalControllerBindings.controls) {
                val input = "virtual:$control"
                row(virtualLabel(control), (bindings[input]?.let(::targetLabel) ?: "Unassigned") + "  ▾", true) {
                    chooseInput(input)
                }
            }
            val legacy = bindings.values.filter { !it.input.startsWith("virtual:") }
            if (legacy.isNotEmpty()) {
                section("LEGACY DIRECT INPUTS")
                note("These saved assignments still work. Reset this profile to use virtual controls.")
                for (binding in legacy) row(inputLabel(binding.input), targetLabel(binding) + "  ▾", true) {
                    chooseInput(binding.input)
                }
            }
        }
        section("OPTIONS")
        if (physicalScope) row("Stick dead zone", (getDeadZone() * 100).toInt().toString() + "%", true) {
            stage = Stage.DEAD_ZONE
            render()
        }
        row("Reset profile", "Restore " + when {
            physicalScope -> "standard controller layout"
            scope == null -> "built-in defaults"
            else -> "catalog or global defaults"
        }, true) {
            stage = Stage.RESET
            render()
        }
    }

    private fun renderSources() {
        note("Choose a controller input. A physical keyboard is not needed.")
        row("Listen for a controller input", "Optional capture", true) {
            captureBaseline.clear()
            stage = Stage.CAPTURE
            render()
        }
        row("Enter an Android input code", "For unusual controllers", true) {
            stage = Stage.MANUAL
            render()
        }
        section("AVAILABLE INPUTS")
        for (source in commonSources()) {
            row(source.name, source.input, true) { chooseInput(source.input) }
        }
        val known = commonSources().map { it.input }.toSet()
        for (device in InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)) {
            if (!device.supportsSource(InputDevice.SOURCE_JOYSTICK)) continue
            val extra = device.motionRanges.map { it.axis }.distinct()
                .flatMap { axis -> listOf(axisInput(axis, -1f), axisInput(axis, 1f)) }
                .filter { it !in known }
            if (extra.isNotEmpty()) section(device.name.uppercase())
            for (input in extra) row(inputLabel(input), input, true) { chooseInput(input) }
        }
    }

    private fun renderCapture() {
        note("Release the controller, then press a button or move a stick, trigger, or D-pad. Use Back to cancel.")
        row("Enter code instead", "No hardware capture required", true) {
            stage = Stage.MANUAL
            render()
        }
    }

    private fun renderManual() {
        note("Examples: button:96, axis:0:+, hat:15:-. Android key and axis codes are accepted.")
        val edit = EditText(activity).apply {
            setSingleLine(true)
            hint = "button:96"
            setTextColor(Color.WHITE)
            setHintTextColor(0xff9eacbd.toInt())
        }
        body.addView(edit)
        footerAction("Choose target") {
            val input = edit.text.toString().trim().lowercase()
            try {
                PhysicalControllerBindings.toJson(listOf(PhysicalControllerBinding(input, "a")))
                chooseInput(input)
            } catch (error: Exception) { toast(error.message ?: "Invalid input code") }
        }
    }

    private fun renderTarget() {
        section(inputLabel(selectedInput).uppercase())
        if (physicalScope) {
            val existing = loadPhysical().firstOrNull { it.input == selectedInput }
            note("Current target: " + (existing?.control?.let(::virtualLabel) ?: "Unassigned"))
            row("Virtual controller button", "Choose the logical button or direction", true) {
                stage = Stage.VIRTUAL
                render()
            }
            if (existing != null) row("Clear assignment", "Leave this input unassigned", true) {
                change { savePhysical(loadPhysical().filterNot { it.input == selectedInput }) }
            }
            return
        }
        val existing = load(scope).firstOrNull { it.input == selectedInput }
        note("Current target: " + (existing?.let(::targetLabel) ?: "Unassigned"))
        row("PC-98 key or key combination", "Choose from all scan codes", true) {
            selectedScans.clear()
            selectedScans.addAll(existing?.keys ?: emptyList())
            stage = Stage.KEYS
            render()
        }
        row("PC-98 joystick 1", "Up, down, left, right, button 1 or 2", true) {
            stage = Stage.JOYSTICK
            render()
        }
        row("Emulator action", "Menu, pause, restart, or exit", true) {
            stage = Stage.ACTIONS
            render()
        }
        if (existing != null) row("Clear assignment", "Leave this input unassigned", true) {
            change { save(scope, load(scope).filterNot { it.input == selectedInput }) }
        }
    }

    private fun renderVirtual() {
        note("Map this physical input once. Every game uses the same virtual layout.")
        for (control in PhysicalControllerBindings.controls) {
            row(virtualLabel(control), "Virtual controller", true) {
                change {
                    val updated = loadPhysical().filterNot { it.input == selectedInput } +
                        PhysicalControllerBinding(selectedInput, control)
                    PhysicalControllerBindings.toJson(updated)
                    savePhysical(updated)
                }
            }
        }
    }

    private fun renderKeys() {
        note("Select one to four real PC-98 keys. Modifiers are pressed before the other keys.")
        val saveButton = footerAction("Save selected keys") {
            if (selectedScans.isEmpty()) toast("Choose at least one key")
            else {
                val scans = selectedScans.sortedWith(compareBy<Int> { it !in MODIFIERS }.thenBy { it })
                change { put(ControllerBinding(selectedInput, scans)) }
            }
        }
        for (scan in 0..0x7f) {
            val selected = scan in selectedScans
            val mark = row(Pc98KeyNames.label(scan),
                "0x" + scan.toString(16).padStart(2, '0') + if (selected) "  ✓" else "", true) {
                if (!selectedScans.remove(scan) && selectedScans.size < 4) selectedScans.add(scan)
                markText(scan, saveButton)
            }
            mark.tag = scan
        }
    }

    private fun markText(scan: Int, saveButton: TextView) {
        for (index in 0 until body.childCount) {
            val view = body.getChildAt(index) as? LinearLayout ?: continue
            val right = view.getChildAt(1) as? TextView ?: continue
            if (right.tag == scan) {
                right.text = "0x" + scan.toString(16).padStart(2, '0') +
                    if (scan in selectedScans) "  ✓" else ""
                break
            }
        }
        saveButton.text = "Save " + selectedScans.size + " key" +
            if (selectedScans.size == 1) "" else "s"
    }

    private fun renderJoystick() {
        note("Uses the joystick input on the emulated sound board. Games must support joystick 1.")
        val labels = listOf("Up", "Down", "Left", "Right", "Button 1", "Button 2")
        ControllerBindings.JOYSTICK.forEachIndexed { index, control ->
            row(labels[index], "Joystick 1", true) {
                change { put(ControllerBinding(selectedInput, joystick = control)) }
            }
        }
    }

    private fun renderActions() {
        val actions = listOf("menu" to "Open menu", "pause" to "Pause or resume",
            "restart" to "Restart", "exit" to "Exit")
        for ((id, label) in actions) row(label, "Emulator action", true) {
            change { put(ControllerBinding(selectedInput, action = id)) }
        }
    }

    private fun renderDeadZone() {
        note("Ignore stick movement near its center.")
        deadZoneSlider = SeekBar(activity).apply {
            max = 80
            progress = ((getDeadZone() - 0.10f) * 100).toInt().coerceIn(0, 80)
        }
        body.addView(deadZoneSlider)
        footerAction("Save dead zone") {
            setDeadZone(((deadZoneSlider?.progress ?: 25) + 10) / 100f)
            stage = Stage.LIST
            render()
        }
    }

    private fun renderReset() {
        note(when {
            physicalScope -> "Restore the standard physical controller layout?"
            scope == null -> "Restore the built-in global mapping?"
            else -> "Remove this game's override and use its catalog or global mapping?"
        })
        row("Reset bindings", "Confirm", true) {
            change { if (physicalScope) resetPhysical() else reset(scope) }
        }
        row("Keep bindings", "Cancel", true) { back() }
    }

    private fun put(binding: ControllerBinding) {
        val updated = load(scope).filterNot { it.input == binding.input } + binding
        ControllerBindings.toJson(updated)
        save(scope, updated)
    }

    private fun change(action: () -> Unit) {
        try {
            action()
            stage = Stage.LIST
            render()
        } catch (error: Exception) { toast(error.message ?: "Could not save mapping") }
    }

    private fun section(label: String) {
        body.addView(TextView(activity).apply {
            text = label
            textSize = 13f
            setTextColor(0xff91c6d8.toInt())
            setPadding(dp(10), dp(18), dp(10), dp(8))
        })
    }

    private fun note(text: String) {
        body.addView(TextView(activity).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xffb7c2cf.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(16))
        })
    }

    private fun row(label: String, value: String, enabled: Boolean, action: () -> Unit): TextView {
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = box(false)
            isFocusable = enabled
            isClickable = enabled
            setOnFocusChangeListener { view, focused -> view.background = box(focused) }
            setOnClickListener { action() }
            contentDescription = label + ". " + value
        }
        line.addView(TextView(activity).apply {
            text = label
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -1, 1f))
        val right = TextView(activity).apply {
            text = value
            textSize = 15f
            setTextColor(0xffa6e3ec.toInt())
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            maxLines = 2
        }
        line.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(line, LinearLayout.LayoutParams(-1, dp(58)).apply {
            setMargins(0, 0, 0, dp(3))
        })
        return right
    }

    private fun tab(label: String, selected: Boolean, action: () -> Unit) = TextView(activity).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = box(selected)
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun footerAction(label: String, action: () -> Unit): TextView {
        val button = tab(label, true, action)
        footer.addView(button, LinearLayout.LayoutParams(-1, dp(50)))
        return button
    }

    private fun box(selected: Boolean) = GradientDrawable().apply {
        setColor(if (selected) 0xff304e63.toInt() else 0xff202a36.toInt())
        cornerRadius = dp(6).toFloat()
    }

    private fun targetLabel(binding: ControllerBinding): String = when {
        binding.joystick != null -> "Joystick 1 " + when (binding.joystick) {
            "button1" -> "Button 1"
            "button2" -> "Button 2"
            else -> binding.joystick.replaceFirstChar(Char::uppercase)
        }
        binding.action != null -> binding.action.replaceFirstChar(Char::uppercase)
        else -> binding.keys.joinToString(" + ") { Pc98KeyNames.label(it) }
    }

    private fun virtualLabel(control: String): String = when (control) {
        "l1", "r1", "l2", "r2" -> control.uppercase()
        else -> control.replaceFirstChar(Char::uppercase)
    }

    private fun inputLabel(input: String): String {
        if (input.startsWith("virtual:")) return virtualLabel(input.removePrefix("virtual:"))
        commonSources().firstOrNull { it.input == input }?.let { return it.name }
        val parts = input.split(':')
        return if (parts.size == 2) {
            val code = parts[1].toIntOrNull() ?: return input
            KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
        } else if (parts.size == 3) {
            val axis = parts[1].toIntOrNull() ?: return input
            (if (parts[0] == "hat") "Hat " else "Axis ") +
                MotionEvent.axisToString(axis).removePrefix("AXIS_") + " " + parts[2]
        } else input
    }

    private fun commonSources(): List<Source> {
        val buttons = listOf(
            "A" to KeyEvent.KEYCODE_BUTTON_A, "B" to KeyEvent.KEYCODE_BUTTON_B,
            "X" to KeyEvent.KEYCODE_BUTTON_X, "Y" to KeyEvent.KEYCODE_BUTTON_Y,
            "L1" to KeyEvent.KEYCODE_BUTTON_L1, "R1" to KeyEvent.KEYCODE_BUTTON_R1,
            "L2" to KeyEvent.KEYCODE_BUTTON_L2, "R2" to KeyEvent.KEYCODE_BUTTON_R2,
            "Start" to KeyEvent.KEYCODE_BUTTON_START, "Select" to KeyEvent.KEYCODE_BUTTON_SELECT,
            "Mode" to KeyEvent.KEYCODE_BUTTON_MODE,
            "Left stick press" to KeyEvent.KEYCODE_BUTTON_THUMBL,
            "Right stick press" to KeyEvent.KEYCODE_BUTTON_THUMBR
        ).map { (name, code) -> Source("Buttons", name, "button:" + code) }
        val dpad = listOf("Up" to KeyEvent.KEYCODE_DPAD_UP,
            "Down" to KeyEvent.KEYCODE_DPAD_DOWN, "Left" to KeyEvent.KEYCODE_DPAD_LEFT,
            "Right" to KeyEvent.KEYCODE_DPAD_RIGHT
        ).map { (name, code) -> Source("D-pad buttons", name, "button:" + code) }
        val analog = listOf(
            Source("Left stick", "Left", "axis:" + MotionEvent.AXIS_X + ":-"),
            Source("Left stick", "Right", "axis:" + MotionEvent.AXIS_X + ":+"),
            Source("Left stick", "Up", "axis:" + MotionEvent.AXIS_Y + ":-"),
            Source("Left stick", "Down", "axis:" + MotionEvent.AXIS_Y + ":+"),
            Source("Right stick", "Left", "axis:" + MotionEvent.AXIS_Z + ":-"),
            Source("Right stick", "Right", "axis:" + MotionEvent.AXIS_Z + ":+"),
            Source("Right stick", "Up", "axis:" + MotionEvent.AXIS_RZ + ":-"),
            Source("Right stick", "Down", "axis:" + MotionEvent.AXIS_RZ + ":+"),
            Source("Triggers", "Left trigger", "axis:" + MotionEvent.AXIS_LTRIGGER + ":+"),
            Source("Triggers", "Right trigger", "axis:" + MotionEvent.AXIS_RTRIGGER + ":+"),
            Source("D-pad hat", "Left", "hat:" + MotionEvent.AXIS_HAT_X + ":-"),
            Source("D-pad hat", "Right", "hat:" + MotionEvent.AXIS_HAT_X + ":+"),
            Source("D-pad hat", "Up", "hat:" + MotionEvent.AXIS_HAT_Y + ":-"),
            Source("D-pad hat", "Down", "hat:" + MotionEvent.AXIS_HAT_Y + ":+")
        )
        return buttons + dpad + analog
    }

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        private val MODIFIERS = setOf(0x70, 0x71, 0x72, 0x73, 0x74, 0x7d)
    }
}
