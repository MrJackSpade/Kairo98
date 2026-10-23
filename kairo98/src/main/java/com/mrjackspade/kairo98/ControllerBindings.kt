package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.ControllerBinding
import com.mrjackspade.kairo.frontend.ControllerBindingsCodec
import com.mrjackspade.kairo.frontend.JoystickInputRouter

import org.json.JSONArray

/** Assigns stable virtual controls to PC-98 keys, joystick, mouse, or app actions. */
object ControllerBindings {
    val JOYSTICK = JoystickInputRouter.DEFAULT_CONTROLS
    val codec = ControllerBindingsCodec(::defaults, { it in 0..127 }, JOYSTICK,
        setOf("menu", "pause", "restart", "exit", "fastForward"))
    fun valid(array: JSONArray) = codec.valid(array)
    fun parse(text: String?) = codec.parse(text)
    fun toJson(bindings: List<ControllerBinding>) = codec.toJson(bindings)

    fun defaults(layout: com.mrjackspade.kairo.frontend.ControllerLayout) =
        if (layout == com.mrjackspade.kairo.frontend.ControllerLayout.WITH_STICKS) defaults()
        else defaults().filterNot { it.input.startsWith("virtual:ls") || it.input.startsWith("virtual:rs") }

    /**
     * The D-pad is the cursor keys and the sound board's joystick, A and B are Z and X and its two
     * buttons, the left stick moves the mouse with L1 and R1 its buttons, L2 and R2 are F1 and F2,
     * and the right stick is the numeric keypad's arrows.
     */
    fun defaults() = listOf(
        ControllerBinding("virtual:a", listOf(0x29), joystick = "button1"),
        ControllerBinding("virtual:b", listOf(0x2a), joystick = "button2"),
        ControllerBinding("virtual:x", listOf(0x2b)),
        ControllerBinding("virtual:y", listOf(0x34)),
        ControllerBinding("virtual:l1", mouse = "leftButton"),
        ControllerBinding("virtual:r1", mouse = "rightButton"),
        ControllerBinding("virtual:l2", listOf(0x62)),
        ControllerBinding("virtual:r2", listOf(0x63)),
        ControllerBinding("virtual:start", listOf(0x1c)),
        ControllerBinding("virtual:select", listOf(0x00)),
        ControllerBinding("virtual:menu", action = "menu"),
        ControllerBinding("virtual:up", listOf(0x3a), joystick = "up"),
        ControllerBinding("virtual:down", listOf(0x3d), joystick = "down"),
        ControllerBinding("virtual:left", listOf(0x3b), joystick = "left"),
        ControllerBinding("virtual:right", listOf(0x3c), joystick = "right"),
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
