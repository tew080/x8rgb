package com.x8rgb.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.domain.engine.PriorityResolver
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.util.DiagLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG        = "RgbNotifListener"
private const val PEOPLE_KEY = "android.people.list"
// Our own foreground-service notification lives on this channel. Ignoring it stops the
// app from matching its own APP rule and holding the ring on a permanent color.
private const val SERVICE_CHANNEL = "x8rgb_service"

/**
 * Maps notifications to ring colors via PriorityResolver (CONTACT > GROUP > APP).
 *
 * Only matched notifications touch the ring. Unmatched ones are ignored, so an
 * ordinary notification never turns the ring off.
 *
 * Contact key: taken from EXTRA_PEOPLE_LIST (a stable identifier). The title is
 * NOT used as a key: it is a display name and changes or is redacted.
 * Lock-screen redaction (EXTRA_TITLE == null) disables the CONTACT tier only.
 */
@AndroidEntryPoint
class RgbNotificationListener : NotificationListenerService() {

    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var priorityResolver: PriorityResolver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Matched notifications only: sbn.key -> rule, insertion order = recency. */
    private val activeStack = LinkedHashMap<String, NotificationRule>()

    @Volatile private var cachedRules: List<NotificationRule> = emptyList()
    private var rulesJob: Job? = null

    override fun onListenerConnected() {
        DiagLog.log("notif", "listener connected build=0011")
        rulesJob?.cancel()
        rulesJob = scope.launch {
            ruleRepository.getEnabledRules().collect { cachedRules = it }
        }
    }

    override fun onListenerDisconnected() {
        DiagLog.log("notif", "listener disconnected; rebind requested")
        rulesJob?.cancel()
        requestRebind(ComponentName(this, RgbNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg   = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        // Detail for our own notifications only (ids and channels, never text).
        if (pkg == packageName) {
            DiagLog.log("notif", "post self id=${sbn.id} ch=${notif.channelId} ongoing=${sbn.isOngoing} key=${sbn.key.hashCode()}")
        }
        if (notif.channelId == SERVICE_CHANNEL) return     // our own service notification
        val key   = contactKey(notif)
        val rule  = priorityResolver.resolve(pkg, key, notif.group, cachedRules)
            ?: return                              // no rule: leave the ring alone
        DiagLog.log("notif", "match pkg=$pkg id=${sbn.id} ch=${notif.channelId} type=${rule.type} contact=${key != null}")
        activeStack.remove(sbn.key)                // re-post moves it to the top
        activeStack[sbn.key] = rule
        sendColor(rule.color)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) {
            DiagLog.log("notif", "removed self id=${sbn.id} key=${sbn.key.hashCode()} tracked=${activeStack.containsKey(sbn.key)}")
        }
        if (activeStack.remove(sbn.key) == null) return
        // Fall back to the most recent still-active matched notification, else off.
        val next = activeStack.values.lastOrNull()?.color ?: RgbColor.OFF
        DiagLog.log("notif", "removed; stack=${activeStack.size} fallback=${if (next == RgbColor.OFF) "off" else "rule"}")
        sendColor(next)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun contactKey(n: Notification): String? {
        val extras = n.extras ?: return null
        // Lock-screen redaction strips the title; skip the CONTACT tier then.
        if (extras.getCharSequence(Notification.EXTRA_TITLE) == null) return null
        return extras.getStringArrayList(PEOPLE_KEY)?.firstOrNull()
    }

    private fun sendColor(color: RgbColor) {
        val intent = Intent(this, RgbForegroundService::class.java).apply {
            action = RgbForegroundService.ACTION_SET_COLOR
            putExtra(RgbForegroundService.EXTRA_COLOR_PACKED, color.packed)
        }
        try {
            startService(intent)
        } catch (e: IllegalStateException) {
            // Background-start restriction; the service keeps the last color it received.
            DiagLog.log("notif", "startService blocked: ${e.message}")
        }
    }
}