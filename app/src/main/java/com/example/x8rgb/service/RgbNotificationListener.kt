package com.x8rgb.service

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.domain.engine.PriorityResolver
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val TAG = "RgbNotifListener"

/**
 * Monitors notification events and resolves LED color via PriorityResolver.
 *
 * Lock-screen redaction handling:
 *   Android replaces Notification.extras with empty Bundle when
 *   "Show sensitive content" is OFF. We detect this by checking
 *   whether EXTRA_TITLE is null — if so, we skip CONTACT tier
 *   but still attempt GROUP and APP tiers.
 *
 * Stack model (Last-One-Wins + Fallback):
 *   Active notifications tracked in [activeStack] (key = sbn.key).
 *   On post: push to stack, resolve top → send color.
 *   On remove: pop from stack, resolve new top (or OFF if empty).
 */
@AndroidEntryPoint
class RgbNotificationListener : NotificationListenerService() {

    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var priorityResolver: PriorityResolver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Maintains ordered list of active SBN keys → resolved rule */
    private val activeStack = LinkedHashMap<String, NotificationRule?>()

    private var cachedRules: List<NotificationRule> = emptyList()

    override fun onListenerConnected() {
        Log.d(TAG, "Listener connected")
        // Warm-load rules cache
        scope.launch {
            ruleRepository.getEnabledRules().collect { cachedRules = it }
        }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Listener disconnected — requesting rebind")
        requestRebind(ComponentName(this, RgbNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg         = sbn.packageName ?: return
        val extras      = sbn.notification?.extras ?: Bundle.EMPTY
        val contactKey  = extractContactKey(sbn)   // null if redacted
        val groupKey    = sbn.notification?.group

        val rule = priorityResolver.resolve(pkg, contactKey, groupKey, cachedRules)
        activeStack[sbn.key] = rule

        scope.launch {
            sendColorToService(rule?.color ?: RgbColor.OFF)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        activeStack.remove(sbn.key)
        // Fallback: show color of most recently posted still-active notification
        val fallbackColor = activeStack.values.lastOrNull()?.color ?: RgbColor.OFF
        scope.launch { sendColorToService(fallbackColor) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Attempts to extract Contacts lookup key from notification extras.
     * Returns null on redacted lock-screen or missing contact info.
     */
    private fun extractContactKey(sbn: StatusBarNotification): String? {
        val extras = sbn.notification?.extras ?: return null
        // Primary: title is null = lock-screen redaction
        if (extras.getString(Notification.EXTRA_TITLE) == null) return null
        // Secondary: extras may carry a contacts URI in some messaging apps
        return extras.getString("android.people.list")
            ?: extras.getString(Notification.EXTRA_TITLE)   // fallback: use display name as key
    }

    private fun sendColorToService(color: RgbColor) {
        val intent = Intent(this, RgbForegroundService::class.java).apply {
            action = RgbForegroundService.ACTION_SET_COLOR
            putExtra(RgbForegroundService.EXTRA_COLOR_PACKED, color.packed)
        }
        startService(intent)
    }

    // Needed for onListenerDisconnected rebind call
    private fun ComponentName(ctx: android.content.Context, cls: Class<*>) =
        android.content.ComponentName(ctx, cls)
}
