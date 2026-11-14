package com.x8rgb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.x8rgb.R
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.system.RingBrightnessController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Calendar
import javax.inject.Inject

private const val TAG           = "RgbForegroundService"
private const val CHANNEL_ID    = "x8rgb_service"
private const val NOTIF_ID      = 1
private const val TICK_INTERVAL = 100L   // ms; 10 fps for effect engine

@AndroidEntryPoint
class RgbForegroundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var effectEngine: EffectEngine
    @Inject lateinit var brightnessController: RingBrightnessController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentColor: RgbColor = RgbColor.OFF
    private var settings: AppSettingsEntity = AppSettingsEntity()
    private var tick: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        observeSettings()
        startEffectLoop()
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
        scope.cancel()
        // Turn off ring on clean shutdown
        scope.launch { applyColor(RgbColor.OFF) }
        super.onDestroy()
    }

    // ── Effect tick loop ──────────────────────────────────────────────────────

    private fun startEffectLoop() {
        scope.launch {
            while (isActive) {
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
                // Apply brightness whenever settings change
                brightnessController.write(settings.brightness)
            }
        }
    }

    // ── HyperOS color application ─────────────────────────────────────────────

    /**
     * Currently applies brightness; direct RGB per-notification handled by
     * the system via NotificationListenerService intent broadcast.
     * Phase 2 will add direct AW21024 Binder path as optional upgrade.
     */
    private fun applyColor(color: RgbColor) {
        val result = brightnessController.write(color.brightness)
        if (result is RingBrightnessController.WriteResult.PermissionDenied) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted — brightness write skipped")
        }
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

    companion object {
        const val ACTION_SET_COLOR   = "com.x8rgb.SET_COLOR"
        const val ACTION_STOP        = "com.x8rgb.STOP"
        const val EXTRA_COLOR_PACKED = "color_packed"
    }
}
