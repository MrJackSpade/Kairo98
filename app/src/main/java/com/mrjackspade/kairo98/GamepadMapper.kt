package com.mrjackspade.kairo98

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Converts controller buttons, hats and axis directions into owned virtual input. */
class GamepadMapper(private val router: InputRouter,
                    private val runAction: (String) -> Unit) {
    var bindings: List<ControllerBinding> = ControllerBindings.defaults()
        set(value) {
            releaseAll()
            field = value
        }
    var deadZone = 0.35f
        set(value) { field = value.coerceIn(0.10f, 0.90f) }

    private val active = HashSet<String>()

    fun hasButton(keyCode: Int) = bindings.any { it.input == "button:$keyCode" }

    fun key(event: KeyEvent): Boolean {
        if (!KeyEvent.isGamepadButton(event.keyCode) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        val binding = bindings.firstOrNull { it.input == "button:${event.keyCode}" } ?: return false
        val owner = "gamepad:${event.deviceId}:${binding.input}"
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
        for (binding in bindings) {
            val parts = binding.input.split(':')
            if (parts.size != 3 || parts[0] !in setOf("axis", "hat")) continue
            val axis = parts[1].toIntOrNull() ?: continue
            val value = event.getAxisValue(axis)
            val signed = if (parts[2] == "+") value else -value
            val owner = "gamepad:${event.deviceId}:${binding.input}"
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
    }

    fun releaseAll() {
        active.clear()
        router.releasePrefix("gamepad:")
    }

    private fun activate(owner: String, binding: ControllerBinding) {
        if (!active.add(owner)) return
        if (binding.action == null) router.hold(owner, binding.keys)
        else runAction(binding.action)
    }

    private fun deactivate(owner: String) {
        active.remove(owner)
        router.release(owner)
    }
}
