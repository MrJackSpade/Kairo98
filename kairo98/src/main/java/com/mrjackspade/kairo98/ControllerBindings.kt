package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.ControllerBinding
import com.mrjackspade.kairo.frontend.ControllerBindingsCodec
import com.mrjackspade.kairo.frontend.JoystickInputRouter

import org.json.JSONArray

/** Assigns stable virtual controls to PC-98 keys, joystick, mouse, or app actions. */
object ControllerBindings {
    val JOYSTICK = JoystickInputRouter.DEFAULT_CONTROLS
    val codec = ControllerBindingsCodec({ it in 0..127 }, JOYSTICK,
        setOf("menu", "pause", "restart", "exit", "fastForward"))
    fun valid(array: JSONArray) = codec.valid(array)
    fun parse(text: String) = codec.parse(text)
    fun toJson(bindings: List<ControllerBinding>) = codec.toJson(bindings)

}
