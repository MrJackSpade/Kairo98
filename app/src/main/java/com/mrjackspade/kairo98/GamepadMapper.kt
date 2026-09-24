package com.mrjackspade.kairo98

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Converts controller buttons, hats and axis directions into owned virtual input. */
class GamepadMapper(private val router: InputRouter,
                    private val joystick: JoystickInputRouter,
                    private val runAction: (String) -> Unit) {
    var physicalBindings: List<PhysicalControllerBinding> = PhysicalControllerBindings.defaults()
        set(value) {
            releaseAll()
            field = value
            updateMotionInputs()
        }
    var bindings: List<ControllerBinding> = ControllerBindings.defaults()
        set(value) {
            releaseAll()
            field = value
            updateMotionInputs()
        }
    var deadZone = 0.35f
        set(value) { field = value.coerceIn(0.10f, 0.90f) }

    private val active = HashSet<String>()
    private var motionInputs = emptyList<String>()

    init { updateMotionInputs() }

    private fun updateMotionInputs() {
        motionInputs = (physicalBindings.map { it.input } + bindings.map { it.input })
            .filter { it.startsWith("axis:") || it.startsWith("hat:") }.distinct()
    }

    fun hasButton(keyCode: Int) = resolve("button:$keyCode") != null

    fun controlForButton(event: KeyEvent): String? {
        if (!KeyEvent.isGamepadButton(event.keyCode) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return null
        return physicalBindings.firstOrNull { it.input == "button:${event.keyCode}" }?.control
    }

    fun key(event: KeyEvent): Boolean {
        if (!KeyEvent.isGamepadButton(event.keyCode) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        val input = "button:${event.keyCode}"
        val binding = resolve(input) ?: return false
        val owner = "gamepad:${event.deviceId}:$input"
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) activate(owner, binding)
            KeyEvent.ACTION_UP -> deactivate(owner)
        }
        return true
    }

    fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        var handled = false
        for (input in motionInputs) {
            val binding = resolve(input) ?: continue
            val parts = input.split(':')
            if (parts.size != 3 || parts[0] !in setOf("axis", "hat")) continue
            val axis = parts[1].toIntOrNull() ?: continue
            val value = event.getAxisValue(axis)
            val signed = if (parts[2] == "+") value else -value
            val owner = "gamepad:${event.deviceId}:$input"
            if (signed >= deadZone) activate(owner, binding)
            else if (signed <= deadZone * 0.5f) deactivate(owner)
            handled = true
        }
        return handled
    }

    fun releaseDevice(deviceId: Int) {
        val prefix = "gamepad:$deviceId:"
        active.removeAll { it.startsWith(prefix) }
        router.releasePrefix(prefix)
        joystick.releasePrefix(prefix)
    }

    fun releaseAll() {
        active.clear()
        router.releasePrefix("gamepad:")
        joystick.releasePrefix("gamepad:")
    }

    private fun resolve(input: String): ControllerBinding? {
        val control = physicalBindings.firstOrNull { it.input == input }?.control
        if (control != null) bindings.firstOrNull { it.input == "virtual:$control" }?.let { return it }
        if (control == "menu") return ControllerBinding("virtual:menu", action = "menu")
        // Old saved profiles contain direct Android-to-guest bindings.
        return bindings.firstOrNull { it.input == input }
    }

    private fun activate(owner: String, binding: ControllerBinding) {
        if (!active.add(owner)) return
        when {
            binding.joystick != null -> joystick.hold(owner, binding.joystick)
            binding.action != null -> runAction(binding.action)
            else -> router.hold(owner, binding.keys)
        }
    }

    private fun deactivate(owner: String) {
        active.remove(owner)
        router.release(owner)
        joystick.release(owner)
    }
}
