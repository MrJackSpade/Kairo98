package com.mrjackspade.kairo98

/**
 * Watches the screen the guest shows, by its [signature] (null when no machine runs), every 100 ms;
 * a prompt must disappear before it can trigger again.
 */
class DiskSwapMatcher(
    private val signature: () -> Long?,
    private val running: () -> Boolean,
    private val cancelled: () -> Boolean,
    private val swap: (DiskSwap) -> Unit
) {
    fun run(rules: List<DiskSwap>) {
        if (rules.isEmpty()) return
        val armed = BooleanArray(rules.size) { true }
        val misses = IntArray(rules.size)
        while (!cancelled()) {
            val hash = signature()
            if (hash != null && running()) {
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
