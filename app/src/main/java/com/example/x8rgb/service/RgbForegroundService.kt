package com.x8rgb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RuleType
import com.x8rgb.system.MiuiLightsBinder
import com.x8rgb.system.SystemLightProbe
import com.x8rgb.util.DiagLog
import android.util.Log
import androidx.core.app.NotificationCompat
import com.x8rgb.R
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.model.RgbColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Calendar
import javax.inject.Inject

private const val TAG           = "RgbForegroundService"
private const val CHANNEL_ID    = "x8rgb_service"
private const val NOTIF_ID      = 1
private const val TICK_INTERVAL = 100L   // ms; 10 fps for effect engine
private const val REASSERT_MS   = 1000L  // system lights effect overwrites our color; re-send
private const val MIN_SEND_GAP  = 100L   // throttle changes (effects animate at 10 fps)
private const val ACTION_TEST   = "com.x8rgb.TEST"  // debug builds only: adb broadcast
private const val TEST_CHANNEL  = "x8rgb_test"
private const val TEST_NOTIF_ID = 4242

@AndroidEntryPoint
class RgbForegroundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var effectEngine: EffectEngine
    @Inject lateinit var ruleRepository: RuleRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentColor: RgbColor = RgbColor.OFF
    private var settings: AppSettingsEntity = AppSettingsEntity()
    private var tick: Long = 0L
    private var lastRgb: Int = -1
    private var lastSentAt: Long = 0L
    private var reassertCount = 0
    @Volatile private var testing = false   // pauses the effect loop during a test case
    private var dumpReceiver: BroadcastReceiver? = null
    private var settingsObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        observeSettings()
        startEffectLoop()
        registerDiagnostics()
        DiagLog.log("svc", "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_COLOR -> {
                val packed = intent.getLongExtra(EXTRA_COLOR_PACKED, RgbColor.OFF.packed)
                currentColor = RgbColor(packed)
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // START_STICKY: system will restart service after kill (HyperOS may still kill it)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Synchronous binder call: must run before scope.cancel() or it is dropped
        MiuiLightsBinder.off()
        unregisterDiagnostics()
        DiagLog.log("svc", "destroyed")
        scope.cancel()
        super.onDestroy()
    }

    // ── Effect tick loop ──────────────────────────────────────────────────────

    private fun startEffectLoop() {
        scope.launch {
            while (isActive) {
                if (testing) { delay(TICK_INTERVAL); continue }
                if (settings.masterEnabled && !isQuietHours()) {
                    val effectColor = if (settings.musicSyncEnabled) {
                        currentColor   // music sync sets currentColor via FFT
                    } else {
                        // Animate active effect over base color
                        effectEngine.compute(0, tick, currentColor)
                    }
                    applyColor(effectColor)
                    tick++
                } else {
                    applyColor(RgbColor.OFF)
                }
                delay(TICK_INTERVAL)
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.observe().collect { s ->
                settings = s ?: AppSettingsEntity()
                // Brightness is folded into the color on the next tick
            }
        }
    }

    // ── HyperOS color application ─────────────────────────────────────────────

    /** Writes color through miui.lights.ILightsManager; see MiuiLightsBinder. */
    private fun applyColor(color: RgbColor) {
        val k = settings.brightness / 255f          // user slider scales the color
        val rgb = ((color.r * k).toInt() shl 16) or
                  ((color.g * k).toInt() shl 8)  or
                   (color.b * k).toInt()
        val now = SystemClock.elapsedRealtime()
        val changed = rgb != lastRgb
        val due = now - lastSentAt >= REASSERT_MS
        if (!changed && !due) return
        if (changed && now - lastSentAt < MIN_SEND_GAP) return
        if (!changed) {
            reassertCount++                         // system may have overwritten us
            DiagLog.log("color", "reassert rgb=%06X".format(rgb))
        } else {
            DiagLog.log("color", "rgb=%06X".format(rgb))
        }
        lastSentAt = now
        if (MiuiLightsBinder.setColor(rgb)) lastRgb = rgb
        else Log.w(TAG, "ILightsManager.setColor rejected")
    }

    // ── Quiet hours ───────────────────────────────────────────────────────────

    private fun isQuietHours(): Boolean {
        if (!settings.quietHoursEnabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = settings.quietHoursStart
        val end   = settings.quietHoursEnd
        return if (start <= end) hour in start until end
        else   hour >= start || hour < end   // spans midnight
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RGB Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("x8RGB active")
            .setSmallIcon(R.drawable.ic_led_ring)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    // ── Diagnostics (logcat tag X8RGB) ─────────────────────────────────────────

    private fun registerDiagnostics() {
        DiagLog.log("sys", "start ${SystemLightProbe.snapshot(this)}")
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                DiagLog.log("sys", "${uri?.lastPathSegment} changed: ${SystemLightProbe.snapshot(this@RgbForegroundService)}")
            }
        }
        settingsObserver = obs
        SystemLightProbe.KEYS.forEach { contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, obs) }
        // Test receiver: debuggable builds only (exported, so restricted).
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val rx = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_TEST) return
                val case = intent.getStringExtra("case") ?: return
                DiagLog.clear()
                runTest(case, intent.getIntExtra("ms", 5000))
            }
        }
        dumpReceiver = rx
        ContextCompat.registerReceiver(this, rx, IntentFilter(ACTION_TEST), ContextCompat.RECEIVER_EXPORTED)
    }

    /**
     * Cases (adb broadcast -a com.x8rgb.TEST --es case NAME [--ei ms N] -p com.x8rgb):
     *  snapshot : log system light keys once
     *  colors   : red, green, blue, off; 2 s each; logs setColor result per step
     *  notify   : APP rule for own package (red), post notification, hold ms, cancel, observe fallback
     */
    private fun runTest(case: String, holdMs: Int) = scope.launch {
        val t0 = SystemClock.elapsedRealtime()
        fun log(msg: String) = DiagLog.log("test", "T+${SystemClock.elapsedRealtime() - t0}ms $msg")
        testing = true
        try {
            log("case=$case")
            when (case) {
                "snapshot" -> log(SystemLightProbe.snapshot(this@RgbForegroundService))
                "colors" -> for (c in listOf(0xFF0000, 0x00FF00, 0x0000FF, 0x000000)) {
                    log("setColor %06X -> %b".format(c, MiuiLightsBinder.setColor(c)))
                    delay(2000)
                }
                "notify" -> {
                    val nm = getSystemService(NotificationManager::class.java)
                    if (!nm.areNotificationsEnabled()) log("notifications DISABLED for app (grant POST_NOTIFICATIONS)")
                    nm.createNotificationChannel(NotificationChannel(TEST_CHANNEL, "x8rgb test", NotificationManager.IMPORTANCE_DEFAULT))
                    val rule = NotificationRule(type = RuleType.APP, key = packageName,
                        displayName = "x8rgb test", color = RgbColor(255, 0, 0))
                    runCatching { ruleRepository.upsert(rule) }
                        .onFailure { log("rule upsert failed: ${it.javaClass.simpleName}") }
                    delay(500)                       // listener refreshes its rule cache
                    val n = NotificationCompat.Builder(this@RgbForegroundService, TEST_CHANNEL)
                        .setSmallIcon(R.drawable.ic_led_ring)
                        .setContentTitle("x8rgb test")
                        .setContentText("test")
                        .build()
                    nm.notify(TEST_NOTIF_ID, n)
                    log("posted notification id=$TEST_NOTIF_ID hold=${holdMs}ms")
                    delay(holdMs.toLong())
                    nm.cancel(TEST_NOTIF_ID)
                    log("cancelled notification")
                    delay(1500)                      // observe fallback after removal
                }
                else -> log("unknown case")
            }
        } finally {
            testing = false
            log("done ${SystemLightProbe.snapshot(this@RgbForegroundService)}")
        }
    }

    private fun unregisterDiagnostics() {
        runCatching { settingsObserver?.let { contentResolver.unregisterContentObserver(it) } }
        runCatching { dumpReceiver?.let { unregisterReceiver(it) } }
        settingsObserver = null
        dumpReceiver = null
    }

    companion object {
        const val ACTION_SET_COLOR   = "com.x8rgb.SET_COLOR"
        const val ACTION_STOP        = "com.x8rgb.STOP"
        const val EXTRA_COLOR_PACKED = "color_packed"
    }
}
