package com.x8rgb.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.engine.PriorityResolver
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.system.SystemLightArbiter
import com.x8rgb.util.DiagLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SERVICE_CHANNEL = "x8rgb_service"

@AndroidEntryPoint
class RgbNotificationListener : NotificationListenerService() {

    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var priorityResolver: PriorityResolver
    @Inject lateinit var arbiter: SystemLightArbiter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeStack = LinkedHashMap<String, NotificationRule>()

    @Volatile private var cachedRules: List<NotificationRule> = emptyList()
    @Volatile private var masterEnabled: Boolean = true
    private var rulesJob: Job? = null
    private var settingsJob: Job? = null

    override fun onListenerConnected() {
        DiagLog.log("notif", "listener connected")
        rulesJob?.cancel()
        rulesJob = scope.launch {
            ruleRepository.getEnabledRules().collect { cachedRules = it }
        }
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepository.observe().collect { masterEnabled = it?.masterEnabled ?: true }
        }
    }

    override fun onListenerDisconnected() {
        DiagLog.log("notif", "listener disconnected")
        rulesJob?.cancel()
        settingsJob?.cancel()
        requestRebind(ComponentName(this, RgbNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        if (notif.channelId == SERVICE_CHANNEL) return
        if (pkg == packageName) return

        // A notification that asks for a light, and that no rule of ours claims, is painted by the system.
        // Yield the ring to it until the notification is removed.
        val key = contactKey(notif)
        val rule = priorityResolver.resolve(pkg, key, notif.group, cachedRules)
        if (rule == null && (notif.flags and Notification.FLAG_SHOW_LIGHTS) != 0) {
            arbiter.hold(sysKey(sbn))
            DiagLog.log("yield", "system light from pkg=$pkg key=${sbn.key}")
        }

        if (!masterEnabled) {
            DiagLog.log("notif", "posted pkg=$pkg ignored: master disabled")
            return
        }

        DiagLog.log("notif", "posted pkg=$pkg ch=${notif.channelId} rules=${cachedRules.size} -> ${rule?.displayName ?: "NO MATCH"}")
        if (rule == null) return

        activeStack[sbn.key] = rule
        updateRing()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        arbiter.release(sysKey(sbn))
        if (activeStack.remove(sbn.key) != null) {
            updateRing()
        }
    }

    private fun sysKey(sbn: StatusBarNotification) = "notif:${sbn.key}"

    private fun updateRing() {
        val bestRule = activeStack.values.sortedBy { it.type.ordinal }.firstOrNull()
        if (bestRule != null) {
            sendColor(bestRule.color, bestRule.animationId)
        } else {
            sendColor(RgbColor.OFF, EffectEngine.EFFECT_STATIC)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun contactKey(n: Notification): String? {
        val extras = n.extras ?: return null
        return extras.getStringArrayList("android.people.list")?.firstOrNull()
    }

    private fun sendColor(color: RgbColor, animationId: Int) {
        val intent = Intent(this, RgbForegroundService::class.java).apply {
            action = RgbForegroundService.ACTION_SET_COLOR
            putExtra(RgbForegroundService.EXTRA_COLOR_PACKED, color.packed)
            putExtra(RgbForegroundService.EXTRA_ANIMATION_ID, animationId)
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            Log.e("RGB", "Failed to start service", e)
        }
    }
}