package com.x8rgb.system

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks other light sources (e.g. a system notification light) that are using the ring.
 * While any hold is active, the foreground service must not send colours to the ring.
 * Holds are released by the source (notification removed) or by the listener's reconcile check;
 * [MAX_HOLD_MS] is only a last-resort ceiling.
 */
@Singleton
class SystemLightArbiter @Inject constructor() {

    /** reason key -> expiry time (SystemClock.elapsedRealtime) */
    private val holds = ConcurrentHashMap<String, Long>()

    /** Start or refresh a hold. Re-holding a key during its grace period cancels the resume. */
    fun hold(key: String, maxMs: Long = MAX_HOLD_MS) {
        holds[key] = SystemClock.elapsedRealtime() + maxMs
    }

    /**
     * Source finished. Resume after a short grace so a light that re-appears at once does not flicker.
     * Always schedule grace from *now* (do not keep a far-future MAX_HOLD expiry).
     */
    fun release(key: String) {
        val resumeAt = SystemClock.elapsedRealtime() + RESUME_GRACE_MS
        holds.computeIfPresent(key) { _, _ -> resumeAt }
    }

    /** Drop every hold immediately (e.g. the notification listener disconnected). */
    fun clear() {
        holds.clear()
    }

    /** Reasons currently holding the ring (expired holds are dropped). */
    fun activeReasons(): List<String> {
        val now = SystemClock.elapsedRealtime()
        holds.entries.removeIf { it.value <= now }
        return holds.keys.toList()
    }

    /** True if any hold is still active (cheaper than allocating the list). */
    fun isHeld(): Boolean {
        val now = SystemClock.elapsedRealtime()
        holds.entries.removeIf { it.value <= now }
        return holds.isNotEmpty()
    }

    companion object {
        const val MAX_HOLD_MS = 10 * 60_000L
        const val RESUME_GRACE_MS = 80L   // was 150: snappier return to app colour
    }
}
