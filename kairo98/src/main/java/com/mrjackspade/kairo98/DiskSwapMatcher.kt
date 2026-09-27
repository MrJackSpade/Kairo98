package com.mrjackspade.kairo98

/** Watches complete guest frames; a prompt must disappear before it can trigger again. */
class DiskSwapMatcher(
    private val snapshot: () -> LongArray,
    private val running: () -> Boolean,
    private val cancelled: () -> Boolean,
    private val swap: (GameCatalog.DiskSwap) -> Unit
) {
    fun run(rules: List<GameCatalog.DiskSwap>) {
        if (rules.isEmpty()) return
        val armed = BooleanArray(rules.size) { true }
        val misses = IntArray(rules.size)
        var lastSerial = snapshot().getOrNull(0) ?: 0L
        while (!cancelled()) {
            val sample = snapshot()
            val serial = sample.getOrNull(0) ?: 0L
            if (running() && serial > lastSerial) {
                lastSerial = serial
                val hash = sample.getOrNull(1)
                for ((index, rule) in rules.withIndex()) {
                    if (hash in rule.screenHashes) {
                        misses[index] = 0
                        if (armed[index]) {
                            armed[index] = false
                            swap(rule)
                        }
                    } else if (++misses[index] >= 3) {
                        armed[index] = true
                    }
                }
            }
            Thread.sleep(100)
        }
    }
}
