package com.khosravi.devin.present.e2e

import android.os.SystemClock

/** Polling waits; the suite never sleeps for a fixed time except where the app itself delays. */
object Waits {

    /** Generous: the emulator used for this suite can take several seconds to draw a new activity. */
    const val DEFAULT_TIMEOUT_MS = 45_000L
    // Each poll walks the accessibility tree, which costs the app's main thread work; keep it gentle.
    private const val POLL_MS = 300L

    /** Polls [condition] until true or [timeoutMs]; returns whether it became true. */
    fun until(timeoutMs: Long = DEFAULT_TIMEOUT_MS, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (runCatching(condition).getOrDefault(false)) return true
            if (SystemClock.uptimeMillis() > end) return false
            SystemClock.sleep(POLL_MS)
        }
    }

    fun require(description: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, condition: () -> Boolean) {
        if (!until(timeoutMs, condition)) throw AssertionError("Timed out after $timeoutMs ms waiting for: $description")
    }

    /** Asserts [condition] stays true for the whole [durationMs] (for "nothing happens" checks). */
    fun holds(description: String, durationMs: Long, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + durationMs
        while (SystemClock.uptimeMillis() < end) {
            if (!condition()) throw AssertionError("Expected to keep holding: $description")
            SystemClock.sleep(POLL_MS)
        }
    }

    /** Only for waiting out a delay the app itself imposes (e.g. a debounce), never for "settling". */
    fun appDelay(ms: Long) = SystemClock.sleep(ms)
}
