package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.MouseInputRouter
import com.mrjackspade.kairo.frontend.PhysicalControllerBindings
import com.mrjackspade.kairo.frontend.ControllerBinding
import com.mrjackspade.kairo.frontend.JoystickInputRouter

import org.json.JSONArray
import org.json.JSONObject

/** Assigns stable virtual controls to PC-98 keys, joystick, mouse, or app actions. */
object ControllerBindings {
    // Physical inputs remain valid while old user profiles are being migrated.
    private val INPUT = Regex("(?:virtual:[a-z0-9]+|button:[0-9]{1,4}|(?:axis|hat):[0-9]{1,3}:[+-])")
    private val ACTIONS = setOf("menu", "pause", "restart", "exit", "fastForward")
    val JOYSTICK = JoystickInputRouter.DEFAULT_CONTROLS

    fun valid(array: JSONArray): Boolean {
        if (array.length() > 128) return false
        val inputs = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return false
            val input = item.optString("input")
            if (!INPUT.matches(input) || !inputs.add(input)) return false
            if (input.startsWith("virtual:") &&
                input.removePrefix("virtual:") !in PhysicalControllerBindings.controls) return false
            val keys = item.optJSONArray("keys")
            val action = item.optString("action").takeIf(String::isNotEmpty)
            val joystick = item.optString("joystick").takeIf(String::isNotEmpty)
            val mouse = item.optString("mouse").takeIf(String::isNotEmpty)
            if (listOf(keys != null, action != null, joystick != null, mouse != null)
                    .count { it } != 1) return false
            if (keys != null) {
                if (keys.length() !in 1..4) return false
                val scans = ArrayList<Int>()
                for (keyIndex in 0 until keys.length()) {
                    val value = keys.opt(keyIndex)
                    if (value !is Int && value !is Long) return false
                    scans.add((value as Number).toInt())
                }
                if (scans.any { it !in 0..127 } || scans.distinct().size != scans.size) return false
            }
            if (action != null && action !in ACTIONS) return false
            if (joystick != null && joystick !in JOYSTICK) return false
            if (mouse != null && mouse !in MouseInputRouter.TARGETS) return false
        }
        return true
    }

    fun parse(text: String?): List<ControllerBinding> = try {
        if (text == null) defaults()
        else {
            val array = JSONArray(text)
            if (!valid(array)) defaults()
            else (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val keys = item.optJSONArray("keys")
                ControllerBinding(item.getString("input"),
                    if (keys == null) emptyList() else (0 until keys.length()).map(keys::getInt),
                    item.optString("action").takeIf(String::isNotEmpty),
                    item.optString("joystick").takeIf(String::isNotEmpty),
                    item.optString("mouse").takeIf(String::isNotEmpty))
            }
        }
    } catch (_: Exception) { defaults() }

    fun toJson(bindings: List<ControllerBinding>): JSONArray = JSONArray().also { array ->
        bindings.forEach { binding ->
            val item = JSONObject().put("input", binding.input)
            when {
                binding.action != null -> item.put("action", binding.action)
                binding.joystick != null -> item.put("joystick", binding.joystick)
                binding.mouse != null -> item.put("mouse", binding.mouse)
                else -> item.put("keys", JSONArray(binding.keys))
            }
            array.put(item)
        }
        require(valid(array)) { "Invalid controller mapping" }
    }

    fun defaults() = listOf(
        ControllerBinding("virtual:a", listOf(0x29)),
        ControllerBinding("virtual:b", listOf(0x2a)),
        ControllerBinding("virtual:x", listOf(0x2b)),
        ControllerBinding("virtual:y", listOf(0x34)),
        ControllerBinding("virtual:start", listOf(0x1c)),
        ControllerBinding("virtual:select", listOf(0x00)),
        ControllerBinding("virtual:menu", action = "menu"),
        ControllerBinding("virtual:up", listOf(0x3a)),
        ControllerBinding("virtual:down", listOf(0x3d)),
        ControllerBinding("virtual:left", listOf(0x3b)),
        ControllerBinding("virtual:right", listOf(0x3c)),
        ControllerBinding("virtual:lsup", mouse = "moveUp"),
        ControllerBinding("virtual:lsdown", mouse = "moveDown"),
        ControllerBinding("virtual:lsleft", mouse = "moveLeft"),
        ControllerBinding("virtual:lsright", mouse = "moveRight"),
        ControllerBinding("virtual:rsup", listOf(0x43)),
        ControllerBinding("virtual:rsdown", listOf(0x4b)),
        ControllerBinding("virtual:rsleft", listOf(0x46)),
        ControllerBinding("virtual:rsright", listOf(0x48))
    )
}
