package com.mrjackspade.kairo98

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import java.io.File
import java.security.MessageDigest

/** Explicit debug-only device benchmark for the deterministic 10,000-record fixture. */
class CatalogBenchmarkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "KAIRO98 CATALOG BENCHMARK\n\nPreparing 10,000 synthetic records…"
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 32, 32, 32)
        }
        setContentView(status)
        Thread {
            try {
                val ids = (0 until 10_000).map { index ->
                    val digest = MessageDigest.getInstance("SHA-256")
                        .digest("kairo98-synthetic:$index".toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    "sha256-hdi-v1:$digest"
                }
                val catalog = GameCatalog(this)
                val start = SystemClock.elapsedRealtimeNanos()
                var matches = 0
                for ((index, id) in ids.withIndex()) {
                    if (catalog.resolve(id, "fallback.hdi").title ==
                        "Synthetic Game ${index.toString().padStart(5, '0')}") matches++
                    if (index % 1000 == 999) runOnUiThread {
                        status.text = "KAIRO98 CATALOG BENCHMARK\n\nChecked ${index + 1} / 10,000 records"
                    }
                }
                val fullMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                val known = ids[1234]
                val coldStart = SystemClock.elapsedRealtimeNanos()
                val knownTitle = GameCatalog(this).resolve(known, "fallback.hdi").title
                val coldMs = (SystemClock.elapsedRealtimeNanos() - coldStart) / 1_000_000.0
                val warmStart = SystemClock.elapsedRealtimeNanos()
                repeat(1000) { catalog.resolve(known, "fallback.hdi") }
                val warmMs = (SystemClock.elapsedRealtimeNanos() - warmStart) / 1_000_000.0
                val unknown = catalog.resolve("sha256-hdi-v1:${"f".repeat(64)}", "Unknown.hdi").title
                val result = "records=10000 matched=$matches fullMs=$fullMs " +
                    "coldMs=$coldMs coldTitle=$knownTitle warm1000Ms=$warmMs " +
                    "unknownTitle=$unknown"
                File(filesDir, "catalog-benchmark.txt").writeText(result)
                Log.i(TAG, result)
                runOnUiThread { status.text = "KAIRO98 CATALOG BENCHMARK\n\n$result\n\nPress Back to return." }
            } catch (error: Exception) {
                File(filesDir, "catalog-benchmark.txt").writeText(error.stackTraceToString())
                Log.e(TAG, "Benchmark failed", error)
                runOnUiThread { status.text = "Benchmark failed: ${error.message}\n\nPress Back to return." }
            }
        }.start()
    }

    companion object { private const val TAG = "Kairo98CatalogBench" }
}
