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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SERVICE_CHANNEL = "x8rgb_service"
private const val RECONCILE_IDLE_MS = 1000L
private const val RECONCILE_HELD_MS = 200L   // while system holds ring: detect removal fast

@AndroidEntryPoint
class RgbNotificationListener : NotificationListenerService() {

    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var priorityResolver: PriorityResolver
    @Inject lateinit var arbiter: SystemLightArbiter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeStack = LinkedHashMap<String, NotificationRule>()

    /** Keys of notifications that currently hold the ring for the system light. */
    private val systemLit = HashSet<String>()

    @Volatile private var cachedRules: List<NotificationRule> = emptyList()
    @Volatile private var masterEnabled: Boolean = true
    private var rulesJob: Job? = null
    private var settingsJob: Job? = null
    private var reconcileJob: Job? = null

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
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            while (isActive) {
                delay(if (systemLit.isEmpty()) RECONCILE_IDLE_MS else RECONCILE_HELD_MS)
                reconcileSystemLit()
            }
        }
        // Catch-up: shade may already contain light notifications when we connect.
        scope.launch {
            delay(100)
            seedSystemLitFromShade()
        }
    }

    override fun onListenerDisconnected() {
        DiagLog.log("notif", "listener disconnected")
        rulesJob?.cancel()
        settingsJob?.cancel()
        reconcileJob?.cancel()
        releaseAllSystemLit("listener disconnected")
        requestRebind(ComponentName(this, RgbNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        if (notif.channelId == SERVICE_CHANNEL) return
        if (pkg == packageName) return

        val key = contactKey(notif)
        val rule = priorityResolver.resolve(pkg, key, notif.group, cachedRules)

        // A notification that asks for a light and that none of our rules claims is painted by the system:
        // yield the ring to it. Anything else must not keep a stale hold.
        if (rule == null && (notif.flags and Notification.FLAG_SHOW_LIGHTS) != 0) {
            holdSystemLit(sbn.key, pkg)
        } else {
            releaseSystemLit(sbn.key, "rule matched or no light")
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
        releaseSystemLit(sbn.key, "removed")
        if (activeStack.remove(sbn.key) != null) {
            updateRing()
        }
    }

    private fun holdSystemLit(key: String, pkg: String) {
        systemLit += key
        arbiter.hold(sysKey(key))
        DiagLog.log("yield", "system light from pkg=$pkg key=$key")
    }

    private fun releaseSystemLit(key: String, why: String) {
        if (systemLit.remove(key)) {
            arbiter.release(sysKey(key))
            DiagLog.log("yield", "release key=$key ($why)")
        }
    }

    private fun releaseAllSystemLit(why: String) {
        if (systemLit.isEmpty()) return
        systemLit.clear()
        arbiter.clear()
        DiagLog.log("yield", "release all ($why)")
    }

    /**
     * Safety net: if a notification that holds the ring is no longer in the shade,
     * release it even when the removal callback was missed.
     */
    private fun reconcileSystemLit() {
        if (systemLit.isEmpty()) return
        val present: Set<String> = try {
            activeNotifications?.map { it.key }?.toSet() ?: return
        } catch (e: Exception) {
            return
        }
        for (key in systemLit.filter { it !in present }.toList()) {
            releaseSystemLit(key, "not in shade any more")
        }
    }

    /** On (re)connect: any existing SHOW_LIGHTS notifs without our rule should hold the ring. */
    private fun seedSystemLitFromShade() {
        val list = try { activeNotifications ?: return } catch (_: Exception) { return }
        for (sbn in list) {
            val pkg = sbn.packageName ?: continue
            val notif = sbn.notification ?: continue
            if (pkg == packageName) continue
            if (notif.channelId == SERVICE_CHANNEL) continue
            if ((notif.flags and Notification.FLAG_SHOW_LIGHTS) == 0) continue
            val key = contactKey(notif)
            val rule = priorityResolver.resolve(pkg, key, notif.group, cachedRules)
            if (rule == null) holdSystemLit(sbn.key, pkg)
        }
    }

    private fun sysKey(key: String) = "notif:$key"

    private fun updateRing() {
        val bestRule = activeStack.values.sortedBy { it.type.ordinal }.firstOrNull()
        if (bestRule != null) {
            sendColor(bestRule.color, bestRule.animationId)
        } else {
            sendColor(RgbColor.OFF, EffectEngine.EFFECT_STATIC)
        }
    }

    override fun onDestroy() {
        releaseAllSystemLit("listener destroyed")
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
