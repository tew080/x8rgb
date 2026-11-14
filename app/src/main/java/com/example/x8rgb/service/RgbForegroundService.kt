// X8RGB file: RgbForegroundService.kt
// Version: v0014
// Owns the ring while a color is active. Release code only; debug cases live in debug/DebugTestRunner.kt.
// History:
//   0009: no idle animation; single OFF on release; ownership rule.
//   0012: caller package override (debug only, moved out in 0014).
//   0014: ring mode (OFF / ALWAYS / FACE_DOWN) applied to the system switch; debug code separated.
//
package com.x8rgb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.x8rgb.R
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.debug.DebugTestRunner
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.system.MiuiLightsBinder
import com.x8rgb.system.RingMode
import com.x8rgb.system.RingSwitch
import com.x8rgb.util.DiagLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

private const val CHANNEL_ID    = "x8rgb_service"
private const val NOTIF_ID      = 1
private const val TICK_INTERVAL = 100L    // ms; loop period
private const val REASSERT_MS   = 1000L   // re-send held color: system may overwrite it
private const val MIN_SEND_GAP  = 100L    // ms; minimum gap between color changes

/**
 * Owns the ring while a color is active.
 *
 * Ownership: this service writes to the ring only while it has a color to show.
 * When idle it writes nothing, except one OFF when it hands the ring back.
 *
 * Ring mode: the system switch (settings_strip_light_enable) is set from the saved mode.
 * RingMode.OFF stops this service from writing colors.
 */
@AndroidEntryPoint
class RgbForegroundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ruleRepository: RuleRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var currentColor: RgbColor = RgbColor.OFF
    @Volatile private var settings: AppSettingsEntity = AppSettingsEntity()
    @Volatile private var testing = false     // paused by a debug case

    private var lastRgb = -1
    private var lastSentAt = 0L
    private var ownsRing = false              // true only after we successfully wrote a color
    private var reassertCount = 0
    private var debugRunner: DebugTestRunner? = null

    override fun onCreate() {
        super.onCreate()
        val debug = DebugTestRunner.isEnabled(this)
        if (debug) DiagLog.attach(File(filesDir, "x8rgb_diag.log"))
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        observeSettings()
        startEffectLoop()
        applySavedRingMode()
        if (debug) {
            debugRunner = DebugTestRunner(this, ruleRepository) { debugPause(it) }.also { it.register() }
            DiagLog.log("svc", "debug build; test runner registered")
        }
        DiagLog.log("svc", "created pid=${Process.myPid()}")
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
        // START_STICKY: system restarts the service after a kill
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseRing()                          // synchronous; must run before scope.cancel()
        debugRunner?.unregister()
        DiagLog.log("svc", "destroyed")
        scope.cancel()
        super.onDestroy()
    }

    /** Debug hook (called only by DebugTestRunner): pause the loop and forget the last value. */
    fun debugPause(paused: Boolean) {
        testing = paused
        if (!paused) lastRgb = -1
    }

    // ── Ring mode ─────────────────────────────────────────────────────────────

    private fun applySavedRingMode() {
        if (!RingSwitch.hasPermission(this)) {
            DiagLog.log("sys", "WRITE_SECURE_SETTINGS missing; system switch not changed")
            return
        }
        val mode = RingSwitch.savedMode(this)
        DiagLog.log("sys", "apply mode=$mode ok=${RingSwitch.set(this, mode)}")
    }

    // ── Loop ──────────────────────────────────────────────────────────────────

    private fun startEffectLoop() {
        scope.launch {
            while (isActive) {
                if (testing) { delay(TICK_INTERVAL); continue }
                val mode = RingSwitch.savedMode(this@RgbForegroundService)
                val want = settings.masterEnabled && mode != RingMode.OFF &&
                        !isQuietHours() && hasLight(currentColor)
                if (want) applyColor(currentColor) else releaseRing()
                delay(TICK_INTERVAL)
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.observe().collect { s -> settings = s ?: AppSettingsEntity() }
        }
    }

    // ── Ring output ───────────────────────────────────────────────────────────

    private fun hasLight(c: RgbColor): Boolean = (c.r or c.g or c.b) != 0

    /** Writes the color through miui.lights.ILightsManager (see MiuiLightsBinder). */
    private fun applyColor(color: RgbColor) {
        val k = settings.brightness / 255f           // user slider scales the color
        val rgb = ((color.r * k).toInt() shl 16) or
                ((color.g * k).toInt() shl 8) or
                (color.b * k).toInt()
        val now = SystemClock.elapsedRealtime()
        val changed = rgb != lastRgb
        val due = now - lastSentAt >= REASSERT_MS
        if (!changed && !due) return
        if (changed && now - lastSentAt < MIN_SEND_GAP) return
        if (changed) {
            DiagLog.log("color", "rgb=%06X".format(rgb))
        } else {
            reassertCount++
            DiagLog.log("color", "reassert rgb=%06X count=%d".format(rgb, reassertCount))
        }
        lastSentAt = now
        if (MiuiLightsBinder.setColor(rgb)) {
            lastRgb = rgb
            ownsRing = true
        } else {
            DiagLog.log("color", "rejected rgb=%06X".format(rgb))
        }
    }

    /** Hands the ring back: one OFF write if we were the last writer, nothing otherwise. */
    private fun releaseRing() {
        if (!ownsRing) return
        ownsRing = false
        lastRgb = -1
        DiagLog.log("color", "release -> off")
        MiuiLightsBinder.off()
    }

    // ── Quiet hours ───────────────────────────────────────────────────────────

    private fun isQuietHours(): Boolean {
        if (!settings.quietHoursEnabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = settings.quietHoursStart
        val end = settings.quietHoursEnd
        return if (start <= end) hour in start until end
        else hour >= start || hour < end      // spans midnight
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

    companion object {
        const val ACTION_SET_COLOR   = "com.x8rgb.SET_COLOR"
        const val ACTION_STOP        = "com.x8rgb.STOP"
        const val EXTRA_COLOR_PACKED = "color_packed"
    }
}