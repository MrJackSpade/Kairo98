package com.mrjackspade.kairo98

/** Shares six PC-98 joystick 1 controls across independent controller sources. */
class JoystickInputRouter(private val send: (Int, Boolean) -> Unit) {
    private val owners = HashMap<String, Int>()
    private val counts = IntArray(6)

    @Synchronized fun hold(owner: String, control: String) {
        val index = ControllerBindings.JOYSTICK.indexOf(control)
        require(index >= 0)
        if (owners[owner] == index) return
        release(owner)
        owners[owner] = index
        if (counts[index]++ == 0) send(index, true)
    }

    @Synchronized fun release(owner: String) {
        val index = owners.remove(owner) ?: return
        if (--counts[index] == 0) send(index, false)
    }

    @Synchronized fun releasePrefix(prefix: String) {
        owners.keys.filter { it.startsWith(prefix) }.toList().forEach(::release)
    }
}
