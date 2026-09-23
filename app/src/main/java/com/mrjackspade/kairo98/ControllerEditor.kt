package com.mrjackspade.kairo98

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import kotlin.math.abs

/** Dialog editor for the same binding records used by catalog defaults and overrides. */
class ControllerEditor(
    private val activity: Activity,
    private val load: (LibraryEntry?) -> List<ControllerBinding>,
    private val save: (LibraryEntry?, List<ControllerBinding>) -> Unit,
    private val reset: (LibraryEntry?) -> Unit,
    private val getDeadZone: () -> Float,
    private val setDeadZone: (Float) -> Unit
) {
    private var captureDialog: AlertDialog? = null
    private var captureScope: LibraryEntry? = null
    private val captureBaseline = HashMap<Pair<Int, Int>, Float>()

    fun show(scope: LibraryEntry?) {
        val bindings = load(scope)
        val rows = ArrayList<String>()
        rows.add("Add binding")
        rows.addAll(bindings.map { "${inputLabel(it.input)}  →  ${targetLabel(it)}" })
        if (scope == null) rows.add("Stick dead zone · ${(getDeadZone() * 100).toInt()}%")
        rows.add("Reset ${if (scope == null) "global" else "game"} bindings")
        val controllers = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull()
            .filter { it.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                it.supportsSource(InputDevice.SOURCE_JOYSTICK) }
            .joinToString { it.name }
        AlertDialog.Builder(activity).setTitle(if (scope == null) "Global controller" else "Game controller")
            .setMessage(if (controllers.isEmpty()) "No controller connected. Manual input is available."
                else "Connected: $controllers")
            .setItems(rows.toTypedArray()) { _, which ->
                when {
                    which == 0 -> capture(scope)
                    which in 1..bindings.size -> edit(scope, bindings[which - 1])
                    scope == null && which == bindings.size + 1 -> editDeadZone()
                    else -> AlertDialog.Builder(activity).setTitle("Reset bindings?")
                        .setMessage(if (scope == null) "Restore the built-in global mapping."
                            else "Use this game's catalog mapping or the global mapping.")
                        .setPositiveButton("Reset") { _, _ -> change(scope) { reset(scope) } }
                        .setNegativeButton("Cancel") { _, _ -> show(scope) }.styled()
                }
            }.setNegativeButton("Done", null).styled()
    }

    fun captureKey(event: KeyEvent): Boolean {
        val dialog = captureDialog ?: return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (KeyEvent.isGamepadButton(event.keyCode) ||
                event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                event.isFromSource(InputDevice.SOURCE_JOYSTICK))) {
            val scope = captureScope
            captureDialog = null
            dialog.dismiss()
            chooseTarget(scope, "button:${event.keyCode}")
        }
        return true
    }

    fun captureMotion(event: MotionEvent): Boolean {
        val dialog = captureDialog ?: return false
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        val axes = intArrayOf(MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER)
        for (axis in axes) {
            val value = event.getAxisValue(axis)
            val key = event.deviceId to axis
            val baseline = captureBaseline.putIfAbsent(key, value)
            if (baseline == null) continue
            if (abs(value) < 0.5f) {
                captureBaseline[key] = value
                continue
            }
            if (abs(value) < 0.75f || abs(value - baseline) < 0.75f) continue
            val scope = captureScope
            captureDialog = null
            dialog.dismiss()
            val kind = if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y)
                "hat" else "axis"
            chooseTarget(scope, "$kind:$axis:${if (value > 0) "+" else "-"}")
            return true
        }
        return true
    }

    private fun capture(scope: LibraryEntry?) {
        captureBaseline.clear()
        val dialog = AlertDialog.Builder(activity).setTitle("Press a controller control")
            .setMessage("Release controls, then press a button or move a stick or D-pad. You can also enter its code manually.")
            .setNeutralButton("Enter code") { _, _ ->
                captureDialog = null
                manualInput(scope)
            }
            .setNegativeButton("Cancel") { _, _ ->
                captureDialog = null
                show(scope)
            }.styled()
        captureScope = scope
        captureDialog = dialog
        dialog.setOnKeyListener { _, _, event -> captureKey(event) }
        dialog.window?.decorView?.setOnGenericMotionListener { _, event -> captureMotion(event) }
        dialog.setOnDismissListener { if (captureDialog === dialog) captureDialog = null }
    }

    private fun manualInput(scope: LibraryEntry?) {
        val input = EditText(activity).apply {
            setSingleLine(true)
            hint = "button:96 or axis:0:+ or hat:15:-"
        }
        AlertDialog.Builder(activity).setTitle("Controller input code")
            .setMessage("Button codes use Android keycodes. Axis and hat codes use Android axis numbers and a direction.")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val code = input.text.toString().trim().lowercase()
                try {
                    ControllerBindings.toJson(listOf(ControllerBinding(code, listOf(0x1c))))
                    chooseTarget(scope, code)
                } catch (error: Exception) { toast(error.message ?: "Invalid control code"); show(scope) }
            }.setNegativeButton("Cancel") { _, _ -> show(scope) }.styled()
    }

    private fun edit(scope: LibraryEntry?, binding: ControllerBinding) {
        AlertDialog.Builder(activity).setTitle(inputLabel(binding.input))
            .setItems(arrayOf("Change target", "Remove binding")) { _, which ->
                if (which == 0) chooseTarget(scope, binding.input)
                else change(scope) { save(scope, load(scope).filterNot { it.input == binding.input }) }
            }.setNegativeButton("Cancel") { _, _ -> show(scope) }.styled()
    }

    private fun chooseTarget(scope: LibraryEntry?, input: String) {
        AlertDialog.Builder(activity).setTitle(inputLabel(input))
            .setItems(arrayOf("PC-98 key or chord", "Emulator action")) { _, which ->
                if (which == 0) pickKeys(scope, input) else pickAction(scope, input)
            }.setNegativeButton("Cancel") { _, _ -> show(scope) }.styled()
    }

    private fun pickKeys(scope: LibraryEntry?, input: String) {
        val choices = (0..0x7f).toList()
        val selected = BooleanArray(choices.size)
        AlertDialog.Builder(activity).setTitle("PC-98 keys · select up to 4")
            .setMultiChoiceItems(choices.map { "${Pc98KeyNames.label(it)}  ·  0x${it.toString(16).padStart(2, '0')}" }
                .toTypedArray(), selected) { _, which, checked -> selected[which] = checked }
            .setPositiveButton("Save") { _, _ ->
                val scans = choices.indices.filter { selected[it] }
                    .sortedWith(compareBy<Int> { it !in setOf(0x70, 0x71, 0x72, 0x73, 0x74, 0x7d) }.thenBy { it })
                if (scans.size !in 1..4) { toast("Choose one to four keys"); show(scope) }
                else put(scope, ControllerBinding(input, scans))
            }
            .setNeutralButton("Enter scan codes") { _, _ -> manualScans(scope, input) }
            .setNegativeButton("Cancel") { _, _ -> show(scope) }.styled()
    }

    private fun manualScans(scope: LibraryEntry?, input: String) {
        val text = EditText(activity).apply { setSingleLine(true); hint = "70,1d  (Shift+A)" }
        AlertDialog.Builder(activity).setTitle("PC-98 scan codes")
            .setMessage("Enter one to four hexadecimal scan codes from 00 to 7f, separated by commas. Keys are pressed in this order.")
            .setView(text)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val scans = text.text.toString().split(',').map {
                        it.trim().removePrefix("0x").toInt(16)
                    }
                    put(scope, ControllerBinding(input, scans))
                } catch (_: Exception) { toast("Enter 1–4 scan codes, for example 70,1d"); show(scope) }
            }.setNegativeButton("Cancel") { _, _ -> show(scope) }.styled()
    }

    private fun pickAction(scope: LibraryEntry?, input: String) {
        val actions = arrayOf("Open menu", "Pause or resume", "Restart", "Exit")
        val ids = arrayOf("menu", "pause", "restart", "exit")
        AlertDialog.Builder(activity).setTitle("Emulator action")
            .setItems(actions) { _, which -> put(scope, ControllerBinding(input, action = ids[which])) }
            .setNegativeButton("Cancel") { _, _ -> show(scope) }.styled()
    }

    private fun put(scope: LibraryEntry?, binding: ControllerBinding) {
        change(scope) {
            val updated = load(scope).filterNot { it.input == binding.input } + binding
            ControllerBindings.toJson(updated)
            save(scope, updated)
        }
    }

    private fun editDeadZone() {
        val slider = SeekBar(activity).apply {
            max = 80
            progress = ((getDeadZone() - 0.10f) * 100).toInt().coerceIn(0, 80)
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(activity).setTitle("Stick dead zone")
            .setMessage("Ignore stick movement near center. Current ${(getDeadZone() * 100).toInt()}%.")
            .setView(slider)
            .setPositiveButton("Save") { _, _ ->
                setDeadZone((slider.progress + 10) / 100f)
                show(null)
            }.setNegativeButton("Cancel") { _, _ -> show(null) }.styled()
    }

    private fun change(scope: LibraryEntry?, action: () -> Unit) {
        try { action() } catch (error: Exception) { toast(error.message ?: "Could not save mapping") }
        show(scope)
    }

    private fun inputLabel(input: String): String {
        val parts = input.split(':')
        return if (parts.size == 2) {
            val code = parts[1].toIntOrNull() ?: return input
            KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
        } else if (parts.size == 3) {
            val axis = parts[1].toIntOrNull() ?: return input
            "${if (parts[0] == "hat") "Hat" else "Axis"} " +
                MotionEvent.axisToString(axis).removePrefix("AXIS_") + " ${parts[2]}"
        } else input
    }

    private fun targetLabel(binding: ControllerBinding): String = binding.action?.replaceFirstChar(Char::uppercase)
        ?: binding.keys.joinToString("+") { Pc98KeyNames.label(it) }

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    private fun AlertDialog.Builder.styled(): AlertDialog = create().also { dialog ->
        dialog.show()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(0xff202a36.toInt())
            cornerRadius = 14 * activity.resources.displayMetrics.density
        })
    }
}
