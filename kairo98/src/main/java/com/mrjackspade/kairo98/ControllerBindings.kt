package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.ControllerBinding
import com.mrjackspade.kairo.frontend.ControllerBindingsCodec
import com.mrjackspade.kairo.frontend.JoystickInputRouter

import org.json.JSONArray

/** Assigns stable virtual controls to PC-98 keys, joystick, mouse, or app actions. */
object ControllerBindings {
    val JOYSTICK = JoystickInputRouter.DEFAULT_CONTROLS
    private val codec = ControllerBindingsCodec(::defaults, { it in 0..127 }, JOYSTICK,
        setOf("menu", "pause", "restart", "exit", "fastForward"))
    fun valid(array: JSONArray) = codec.valid(array)
    fun parse(text: String?) = codec.parse(text)
    fun toJson(bindings: List<ControllerBinding>) = codec.toJson(bindings)

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
