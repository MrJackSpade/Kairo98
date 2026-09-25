package com.mrjackspade.kairo98

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper

/** Converts controller buttons, hats and axis directions into owned virtual input. */
class GamepadMapper(private val router: InputRouter,
                    private val joystick: JoystickInputRouter,
                    private val mouse: MouseInputRouter,
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
    private val handler = Handler(Looper.getMainLooper())
    private var lastMouseTick = 0L
    private val mouseTick = object : Runnable {
        override fun run() {
            if (!mouse.hasMovement()) { lastMouseTick = 0L; return }
            val now = android.os.SystemClock.uptimeMillis()
            mouse.tick(now - lastMouseTick)
            lastMouseTick = now
            handler.postDelayed(this, 16)
        }
    }

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
            if (binding.mouse?.startsWith("move") == true) {
                if (signed > deadZone) {
                    val strength = ((signed - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
                    activate(owner, binding, strength)
                } else deactivate(owner)
            } else if (signed >= deadZone) activate(owner, binding)
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
        mouse.releasePrefix(prefix)
        stopMouseTickIfIdle()
    }

    fun releaseAll() {
        active.clear()
        router.releasePrefix("gamepad:")
        joystick.releasePrefix("gamepad:")
        mouse.releasePrefix("gamepad:")
        stopMouseTickIfIdle()
    }

    private fun resolve(input: String): ControllerBinding? {
        val control = physicalBindings.firstOrNull { it.input == input }?.control
        if (control != null) bindings.firstOrNull { it.input == "virtual:$control" }?.let { return it }
        if (control == "menu") return ControllerBinding("virtual:menu", action = "menu")
        // Old saved profiles contain direct Android-to-guest bindings.
        return bindings.firstOrNull { it.input == input }
    }

    private fun activate(owner: String, binding: ControllerBinding, strength: Float = 1f) {
        if (!active.add(owner)) {
            if (binding.mouse?.startsWith("move") == true)
                mouse.hold(owner, binding.mouse, strength)
            return
        }
        when {
            binding.mouse != null -> {
                mouse.hold(owner, binding.mouse, strength)
                startMouseTick()
            }
            binding.joystick != null -> joystick.hold(owner, binding.joystick)
            binding.action != null -> runAction(binding.action)
            else -> router.hold(owner, binding.keys)
        }
    }

    private fun deactivate(owner: String) {
        active.remove(owner)
        router.release(owner)
        joystick.release(owner)
        mouse.release(owner)
        stopMouseTickIfIdle()
    }

    private fun startMouseTick() {
        if (lastMouseTick != 0L || !mouse.hasMovement()) return
        lastMouseTick = android.os.SystemClock.uptimeMillis()
        handler.postDelayed(mouseTick, 16)
    }

    private fun stopMouseTickIfIdle() {
        if (mouse.hasMovement()) return
        handler.removeCallbacks(mouseTick)
        lastMouseTick = 0L
    }
}
