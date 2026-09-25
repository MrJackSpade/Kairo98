package com.mrjackspade.kairo98

import android.os.SystemClock
import java.io.File
import java.util.concurrent.locks.LockSupport
import kotlin.math.max

/** Debug-only repeat input for reproducible performance runs; never changes guest timing. */
class DebugAutoAdvance(
    private val input: InputRouter,
    private val status: () -> String,
    private val output: File,
    private val durationSeconds: Int,
    private val intervalMs: Int
) {
    @Volatile private var cancelled = false
    private val owner = "debug-auto-space"
    private var thread: Thread? = null

    fun start() {
        check(thread == null)
        output.writeText("state\tstarting\n")
        thread = Thread(::run, "Kairo98-auto-advance").also { it.start() }
    }

    fun cancel() {
        cancelled = true
        thread?.interrupt()
        input.release(owner)
    }

    private fun run() {
        val intervalNs = intervalMs * 1_000_000L
        val holdNs = minOf(55_000_000L, intervalNs / 2)
        val count = (durationSeconds * 1000L / intervalMs).toInt()
        val started = SystemClock.elapsedRealtimeNanos()
        var nextTarget = started
        var previousPress = 0L
        var sent = 0
        var late = 0
        var maxLateNs = 0L
        var slow = 0
        var maxGapNs = 0L
        try {
            output.appendText("state\trunning\tcount=$count\tintervalMs=$intervalMs\n")
            while (sent < count && !cancelled) {
                while (!cancelled) {
                    val remaining = nextTarget - SystemClock.elapsedRealtimeNanos()
                    if (remaining <= 0) break
                    LockSupport.parkNanos(remaining)
                }
                if (cancelled) break
                val actual = SystemClock.elapsedRealtimeNanos()
                val delay = max(0L, actual - nextTarget)
                if (delay > intervalNs) late++
                maxLateNs = max(maxLateNs, delay)
                if (previousPress != 0L) {
                    val gap = actual - previousPress
                    if (gap > intervalNs * 5 / 4) slow++
                    maxGapNs = max(maxGapNs, gap)
                }
                previousPress = actual
                nextTarget = max(nextTarget + intervalNs, actual + intervalNs)
                input.hold(owner, listOf(0x34))
                LockSupport.parkNanos(holdNs)
                input.release(owner)
                sent++
                if (sent % max(1, 10_000 / intervalMs) == 0 || sent == count) {
                    output.appendText("sample\t$sent\t${(SystemClock.elapsedRealtimeNanos() - started) / 1_000_000}" +
                        "\tlate=$late\tmaxLateMs=${maxLateNs / 1_000_000.0}" +
                        "\tslow=$slow\tmaxGapMs=${maxGapNs / 1_000_000.0}\t${status()}\n")
                }
            }
            output.appendText("state\t${if (cancelled) "cancelled" else "complete"}\t$sent" +
                "\t${(SystemClock.elapsedRealtimeNanos() - started) / 1_000_000}\n")
        } catch (error: Exception) {
            output.appendText("state\terror\t${error.javaClass.simpleName}: ${error.message}\n")
        } finally {
            input.release(owner)
        }
    }
}
