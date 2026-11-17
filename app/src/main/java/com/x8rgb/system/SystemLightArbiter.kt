package com.x8rgb.system

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks other light sources (e.g. a system notification light) that are using the ring.
 * While any hold is active, the foreground service must not send colours to the ring.
 */
@Singleton
class SystemLightArbiter @Inject constructor() {

    /** reason key -> expiry time (SystemClock.elapsedRealtime) */
    private val holds = ConcurrentHashMap<String, Long>()

    /** Start or refresh a hold. Expires after [maxMs] even if never released (safety net). */
    fun hold(key: String, maxMs: Long = MAX_HOLD_MS) {
        holds[key] = SystemClock.elapsedRealtime() + maxMs
    }

    /** Source finished. Keep yielding for a short grace period so the ring does not flicker. */
    fun release(key: String) {
        val resumeAt = SystemClock.elapsedRealtime() + RESUME_GRACE_MS
        holds.computeIfPresent(key) { _, _ -> resumeAt }
    }

    /** Reasons currently holding the ring (expired holds are dropped). */
    fun activeReasons(): List<String> {
        val now = SystemClock.elapsedRealtime()
        holds.entries.removeIf { it.value <= now }
        return holds.keys.toList()
    }

    companion object {
        const val MAX_HOLD_MS = 15_000L
        const val RESUME_GRACE_MS = 400L
    }
}
