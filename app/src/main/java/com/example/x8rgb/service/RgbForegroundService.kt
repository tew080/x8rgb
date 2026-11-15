// X8RGB file: RgbForegroundService.kt
// Version: v0019
// Owns the ring while a color is active. Supports animations, Quiet Hours, Motion Reactive, and Music Sync.
//
package com.x8rgb.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.x8rgb.R
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.debug.DebugTestRunner
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.engine.FftProcessor
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.system.MiuiLightsBinder
import com.x8rgb.system.MotionManager
import com.x8rgb.system.RingMode
import com.x8rgb.system.RingSwitch
import com.x8rgb.system.SystemLightGuard
import com.x8rgb.system.MusicLightManager
import com.x8rgb.util.DiagLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

private const val CHANNEL_ID    = "x8rgb_service"
private const val NOTIF_ID      = 1
private const val TICK_INTERVAL = 50L     // 20 fps
// No write throttle: HyperOS overwrites the ring between frames; send every tick.

@AndroidEntryPoint
class RgbForegroundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var effectEngine: EffectEngine
    @Inject lateinit var motionManager: MotionManager
    @Inject lateinit var fftProcessor: FftProcessor
    @Inject lateinit var musicLightManager: MusicLightManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var targetColor: RgbColor = RgbColor.OFF
    @Volatile private var targetAnimationId: Int = EffectEngine.EFFECT_STATIC
    @Volatile private var settings: AppSettingsEntity = AppSettingsEntity()
    @Volatile private var testing = false

    @Volatile private var musicColor: RgbColor = RgbColor.OFF
    private var musicSyncJob: Job? = null

    private var lastRgb = -1
    private var lastSentAt = 0L
    private var ownsRing = false
    private var tick = 0L
    private var debugRunner: DebugTestRunner? = null
    private var lightObserver: ContentObserver? = null
    private var suppressedSystem = false

    override fun onCreate() {
        super.onCreate()
        val debug = DebugTestRunner.isEnabled(this)
        if (debug) DiagLog.attach(File(filesDir, "x8rgb_diag.log"))
        
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        
        observeSettings()
        startEffectLoop()
        applySavedRingMode()
        installLightObserver()
        suppressSystemCompetitors()
        
        if (debug) {
            debugRunner = DebugTestRunner(this, ruleRepository) { testing = it }.also { it.register() }
        }
        DiagLog.log("svc", "RGB service started v0019")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_COLOR -> {
                val packed = intent.getLongExtra(EXTRA_COLOR_PACKED, RgbColor.OFF.packed)
                targetColor = RgbColor(packed)
                targetAnimationId = intent.getIntExtra(EXTRA_ANIMATION_ID, EffectEngine.EFFECT_STATIC)
                tick = 0L
                DiagLog.log("svc", "target color set: anim=$targetAnimationId")
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseRing()
        motionManager.stop()
        stopMusicSync()
        uninstallLightObserver()
        // leave charging LED off; user can re-enable in system settings if wanted
        debugRunner?.unregister()
        scope.cancel()
        super.onDestroy()
    }

    private fun applySavedRingMode() {
        if (!RingSwitch.hasPermission(this)) return
        val mode = RingSwitch.savedMode(this)
        RingSwitch.set(this, mode)
    }

    /** Kill competing HyperOS painters while we drive the ring. */
    private fun suppressSystemCompetitors() {
        if (suppressedSystem) return
        val okCharge = SystemLightGuard.suppressChargingLed(this)
        val n = runCatching { musicLightManager.disableAllSystemMusicLights() }.getOrDefault(0)
        suppressedSystem = okCharge || n > 0
        DiagLog.log("sys", "suppress competitors charge=$okCharge musicPkgs=$n snap=${SystemLightGuard.snapshot(this)}")
    }

    private fun installLightObserver() {
        if (lightObserver != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // System rewrote a light key → force next tick to re-send color
                lastRgb = -1
                DiagLog.log("sys", "light key changed; force reassert ${SystemLightGuard.snapshot(this@RgbForegroundService)}")
            }
        }
        lightObserver = obs
        val cr: ContentResolver = contentResolver
        for (k in SystemLightGuard.WATCH_KEYS) {
            cr.registerContentObserver(Settings.Global.getUriFor(k), false, obs)
        }
    }

    private fun uninstallLightObserver() {
        lightObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        lightObserver = null
    }

    private fun startEffectLoop() {
        scope.launch {
            while (isActive) {
                if (testing) { delay(TICK_INTERVAL); continue }
                
                val mode = RingSwitch.savedMode(this@RgbForegroundService)
                val master = settings.masterEnabled && mode != RingMode.OFF && !isQuietHours()
                
                if (master) {
                    // Priority: force-hold > notification > music > motion > base > off
                    if (tick % 40L == 0L) suppressSystemCompetitors() // ~2s @ 20fps
                    when {
                        settings.forceHoldEnabled -> {
                            val c = RgbColor(settings.forceHoldColorPacked)
                            applyColor(if (hasLight(c)) c else RgbColor(255, 255, 255))
                        }
                        targetColor != RgbColor.OFF || targetAnimationId != EffectEngine.EFFECT_STATIC -> {
                            val colorToShow = if (targetAnimationId == EffectEngine.EFFECT_STATIC) {
                                targetColor
                            } else {
                                effectEngine.compute(targetAnimationId, tick, targetColor)
                            }
                            applyColor(colorToShow)
                            tick++
                        }
                        settings.musicSyncEnabled && hasLight(musicColor) -> {
                            applyColor(musicColor)
                        }
                        settings.motionReactiveEnabled -> {
                            val intensity = motionManager.motionIntensity.value
                            if (intensity > 0.05f) {
                                val c = RgbColor((100 * intensity).toInt(), (150 * intensity).toInt(), 255)
                                applyColor(c)
                            } else {
                                applyBaseOrRelease()
                            }
                        }
                        else -> applyBaseOrRelease()
                    }
                } else {
                    releaseRing()
                }
                
                delay(TICK_INTERVAL)
            }
        }
    }

    /** Idle path: base color if set, else hand ring back to system. */
    private fun applyBaseOrRelease() {
        val base = RgbColor(settings.baseColorPacked)
        val effectId = settings.ambientEffectId
        if (!hasLight(base) && effectId == EffectEngine.EFFECT_STATIC) {
            releaseRing()
            return
        }
        // Ambient always-on: animate base (or white if base empty but effect needs a color)
        val seed = if (hasLight(base)) base else RgbColor(255, 255, 255)
        val frame = if (effectId == EffectEngine.EFFECT_STATIC) seed
        else effectEngine.compute(effectId, tick, seed)
        applyColor(frame)
        tick++
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.observe().collectLatest { s -> 
                settings = s ?: AppSettingsEntity()
                
                if (settings.motionReactiveEnabled && settings.masterEnabled) {
                    motionManager.start()
                } else {
                    motionManager.stop()
                }

                if (settings.musicSyncEnabled && settings.masterEnabled) {
                    startMusicSync()
                    // re-kill system music painters each time setting flips on
                    musicLightManager.disableAllSystemMusicLights()
                } else {
                    stopMusicSync()
                }
                if (settings.masterEnabled) suppressSystemCompetitors()
            }
        }
    }

    private fun startMusicSync() {
        if (musicSyncJob != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            DiagLog.log("music", "RECORD_AUDIO permission missing")
            return
        }

        musicSyncJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            
            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    DiagLog.log("music", "AudioRecord init failed")
                    return@launch
                }

                audioRecord.startRecording()
                val readBuffer = ShortArray(1024)
                val floatBuffer = FloatArray(1024)
                
                while (isActive && settings.musicSyncEnabled) {
                    val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            floatBuffer[i] = readBuffer[i] / 32768f
                        }
                        musicColor = fftProcessor.process(floatBuffer.copyOfRange(0, read), sampleRate)
                    }
                    delay(40)
                }
            } catch (e: Exception) {
                DiagLog.log("music", "AudioRecord error: ${e.message}")
            } finally {
                runCatching {
                    audioRecord?.stop()
                    audioRecord?.release()
                }
                musicColor = RgbColor.OFF
            }
        }
    }

    private fun stopMusicSync() {
        musicSyncJob?.cancel()
        musicSyncJob = null
    }

    private fun applyColor(color: RgbColor) {
        val k = settings.brightness / 255f
        val r = (color.r * k).toInt().coerceIn(0, 255)
        val g = (color.g * k).toInt().coerceIn(0, 255)
        val b = (color.b * k).toInt().coerceIn(0, 255)
        val rgb = (r shl 16) or (g shl 8) or b
        
        val now = SystemClock.elapsedRealtime()
        // Always push: system charging/notif/music painters steal the ring otherwise.
        if (MiuiLightsBinder.setColor(rgb)) {
            lastRgb = rgb
            lastSentAt = now
            ownsRing = true
        }
    }

    private fun releaseRing() {
        if (!ownsRing) return
        ownsRing = false
        lastRgb = -1
        tick = 0L
        MiuiLightsBinder.off()
    }

    private fun isQuietHours(): Boolean {
        if (!settings.quietHoursEnabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = settings.quietHoursStart
        val end = settings.quietHoursEnd
        return if (start <= end) hour in start until end
        else hour >= start || hour < end
    }

    private fun hasLight(c: RgbColor): Boolean = (c.r or c.g or c.b) != 0

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RGB Service Status",
            NotificationManager.IMPORTANCE_MIN
        ).apply { 
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("X8RGB")
            .setContentText("การควบคุมไฟ RGB กำลังทำงาน")
            .setSmallIcon(R.drawable.ic_led_ring)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_SET_COLOR   = "com.x8rgb.SET_COLOR"
        const val ACTION_STOP        = "com.x8rgb.STOP"
        const val EXTRA_COLOR_PACKED = "color_packed"
        const val EXTRA_ANIMATION_ID = "animation_id"
    }
}
