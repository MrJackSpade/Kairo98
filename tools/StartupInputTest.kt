import com.mrjackspade.kairo98.InputRouter

fun main() {
    val events = mutableListOf<Pair<Int, Boolean>>()
    val input = InputRouter { scan, down -> events += scan to down }
    val automation = setOf("guest-command", "disk-swap", "debug-auto-space")

    input.hold("guest-command", listOf(1))
    input.hold("keyboard:1:1", listOf(1))
    input.hold("touch", listOf(2))
    input.hold("disk-swap", listOf(3))
    input.hold("debug-auto-space", listOf(4))
    input.releaseAll(automation)
    check(input.pressedScans() == setOf(1, 3, 4))
    check(events.count { it == (1 to false) } == 0) { "Focus cleanup interrupted a shared automatic key" }
    check(events.count { it == (2 to false) } == 1) { "Manual input remained held" }
    input.release("guest-command")
    check(events.count { it == (1 to false) } == 1)
    input.releaseAll()
    check(input.pressedScans().isEmpty()) { "Lifecycle cleanup must release every owner" }
    check(events.count { !it.second } == 4)
    println("Startup input ownership checks passed")
}
