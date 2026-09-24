package com.mrjackspade.kairo98

/** Host-side startup decisions. A step consumes one newly sampled complete guest frame. */
class StartupHashMatcher(
    private val snapshot: () -> LongArray,
    private val dosPromptReady: () -> Boolean,
    private val running: () -> Boolean,
    private val cancelled: () -> Boolean,
    private val send: (Step) -> Unit,
    private val failed: (Step) -> Unit
) {
    data class Step(val label: String, val text: String, val enter: Boolean,
                    val hashes: Set<Long>, val legacyDosPrompt: Boolean,
                    val timeoutMs: Int)

    fun run(steps: List<Step>) {
        var consumedSerial = snapshot().getOrNull(0) ?: 0L
        for (step in steps) {
            val deadline = System.nanoTime() + step.timeoutMs * 1_000_000L
            var matched = false
            while (!cancelled() && System.nanoTime() < deadline) {
                val sample = snapshot()
                val serial = sample.getOrNull(0) ?: 0L
                if (running() && serial > consumedSerial &&
                    ((step.hashes.isNotEmpty() && sample.getOrNull(1) in step.hashes) ||
                        (step.legacyDosPrompt && dosPromptReady()))) {
                    consumedSerial = serial
                    send(step)
                    matched = true
                    break
                }
                Thread.sleep(100)
            }
            if (cancelled()) return
            if (!matched) {
                failed(step)
                return
            }
            // The next step must see an actual sample after the preceding keys were sent.
            consumedSerial = snapshot().getOrNull(0) ?: consumedSerial
        }
    }
}
