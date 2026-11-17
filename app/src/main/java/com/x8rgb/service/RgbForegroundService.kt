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
import android.provider.Settings
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.x8rgb.R
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.engine.FftProcessor
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.system.MiuiLightsBinder
import com.x8rgb.system.MotionManager
import com.x8rgb.system.RingMode
import com.x8rgb.system.RingSwitch
import com.x8rgb.system.SystemLightArbiter
import com.x8rgb.system.SystemLightGuard
import com.x8rgb.system.MusicLightManager
import com.x8rgb.util.DiagLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs

private const val CHANNEL_ID    = "x8rgb_service"
private const val NOTIF_ID      = 1
private const val TICK_INTERVAL = 50L     // 20 fps

@AndroidEntryPoint
class RgbForegroundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var effectEngine: EffectEngine
    @Inject lateinit var motionManager: MotionManager
    @Inject lateinit var fftProcessor: FftProcessor
    @Inject lateinit var musicLightManager: MusicLightManager
    @Inject lateinit var arbiter: SystemLightArbiter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var targetColor: RgbColor = RgbColor.OFF
    @Volatile private var targetAnimationId: Int = EffectEngine.EFFECT_STATIC
    @Volatile private var settings: AppSettingsEntity = AppSettingsEntity()
    
    @Volatile private var musicColor: RgbColor = RgbColor.OFF
    private var musicSyncJob: Job? = null

    private var ownsRing = false
    private var loopN = 0L
    private var lastRgb = -1
    private var lastSentAt = 0L
    @Volatile private var paused = false          // set by DebugTestRunner while a test drives the ring
    private var debugRunner: DebugTestRunner? = null
    private var tick = 0L
    private var lightObserver: ContentObserver? = null
    private var yielding = false

    override fun onCreate() {
        super.onCreate()
        DiagLog.attach(File(filesDir, "x8rgb_diag.log"))
        
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        
        observeSettings()
        startEffectLoop()
        installLightObserver()

        if (DebugTestRunner.isEnabled(this)) {
            debugRunner = DebugTestRunner(this, ruleRepository) { paused = it }.also { it.register() }
        }

        DiagLog.log("svc", "RGB service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_COLOR -> {
                val packed = intent.getLongExtra(EXTRA_COLOR_PACKED, RgbColor.OFF.packed)
                targetColor = RgbColor(packed)
                targetAnimationId = intent.getIntExtra(EXTRA_ANIMATION_ID, EffectEngine.EFFECT_STATIC)
                tick = 0L
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
        debugRunner?.unregister()
        releaseRing()
        motionManager.stop()
        stopMusicSync()
        uninstallLightObserver()
        scope.cancel()
        super.onDestroy()
    }

    private fun installLightObserver() {
        if (lightObserver != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // System changed light settings, re-sync our local knowledge if needed
                scope.launch { suppressSystemCompetitors() }
            }
        }
        lightObserver = obs
        for (k in SystemLightGuard.WATCH_KEYS) {
            contentResolver.registerContentObserver(Settings.Global.getUriFor(k), false, obs)
        }
    }

    private fun uninstallLightObserver() {
        lightObserver?.let { contentResolver.unregisterContentObserver(it) }
        lightObserver = null
    }

    private fun suppressSystemCompetitors() {
        SystemLightGuard.suppressChargingLed(this)
        musicLightManager.disableAllSystemMusicLights()
    }

    private fun startEffectLoop() {
        scope.launch {
            while (isActive) {
                if (paused) { delay(TICK_INTERVAL); continue }

                // Another source (system notification light, etc.) owns the ring: send nothing.
                val busy = arbiter.activeReasons()
                if (busy.isNotEmpty()) {
                    if (!yielding) {
                        yielding = true
                        lastRgb = -1
                        DiagLog.log("yield", "pause ring, held by $busy")
                    }
                    delay(TICK_INTERVAL)
                    continue
                }
                if (yielding) {
                    yielding = false
                    lastRgb = -1           // force a re-send of our colour
                    DiagLog.log("yield", "resume ring")
                }
                val mode = RingSwitch.savedMode(this@RgbForegroundService)
                
                // Logic for "Face-down" (เปิดเมื่อคว่ำจอ)
                // We consider "Face-down" active if Z-axis acceleration is negative (approx -9.8 m/s^2)
                val isFaceDown = motionManager.isFaceDown()
                
                val shouldLightUp = when (mode) {
                    RingMode.OFF -> false
                    RingMode.ALWAYS -> true
                    RingMode.FACE_DOWN -> isFaceDown
                }

                val master = settings.masterEnabled && shouldLightUp && !isQuietHours()

                if (loopN++ % 40L == 0L) {   // every ~2 s
                    DiagLog.log("loop", "master=${settings.masterEnabled} mode=$mode lightUp=$shouldLightUp " +
                        "quiet=${isQuietHours()} force=${settings.forceHoldEnabled} base=${settings.baseColorPacked} " +
                        "ambient=${settings.ambientEffectId} target=${targetColor.packed}/$targetAnimationId " +
                        "bright=${settings.brightness} music=${musicColor.packed}")
                }
                
                if (master) {
                    if (tick % 40L == 0L) suppressSystemCompetitors()
                    
                    when {
                        settings.forceHoldEnabled -> {
                            // Lock: fixed colour, optionally with an effect (EFFECT_STATIC = no effect)
                            val lockColor = RgbColor(settings.forceHoldColorPacked)
                            val fx = effectEngine.sanitize(settings.forceHoldEffectId)
                            val frame = if (fx == EffectEngine.EFFECT_STATIC) lockColor
                                        else effectEngine.compute(fx, tick, lockColor)
                            applyColor(frame)
                            tick++
                        }
                        targetColor != RgbColor.OFF || targetAnimationId != EffectEngine.EFFECT_STATIC -> {
                            val frame = if (targetAnimationId == EffectEngine.EFFECT_STATIC) targetColor
                                        else effectEngine.compute(targetAnimationId, tick, targetColor)
                            applyColor(frame)
                            tick++
                        }
                        settings.musicSyncEnabled && musicColor != RgbColor.OFF -> {
                            applyColor(musicColor)
                        }
                        settings.motionReactiveEnabled && motionManager.motionIntensity.value > 0.05f -> {
                            val intensity = motionManager.motionIntensity.value
                            applyColor(RgbColor((100 * intensity).toInt(), (150 * intensity).toInt(), 255))
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

    private fun applyBaseOrRelease() {
        val base = RgbColor(settings.baseColorPacked)
        val effectId = settings.ambientEffectId
        if (base == RgbColor.OFF && effectId == EffectEngine.EFFECT_STATIC) {
            releaseRing()
            return
        }
        val seed = if (base != RgbColor.OFF) base else RgbColor(255, 255, 255)
        val frame = if (effectId == EffectEngine.EFFECT_STATIC) seed
                    else effectEngine.compute(effectId, tick, seed)
        applyColor(frame)
        tick++
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.observe().collectLatest { s -> 
                settings = s ?: AppSettingsEntity()
                if (settings.masterEnabled) {
                    motionManager.start()
                    if (settings.musicSyncEnabled) startMusicSync() else stopMusicSync()
                    suppressSystemCompetitors()
                } else {
                    motionManager.stop()
                    stopMusicSync()
                }
            }
        }
    }

    private fun startMusicSync() {
        if (musicSyncJob != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        musicSyncJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            
            try {
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val readBuffer = ShortArray(1024)
                    val floatBuffer = FloatArray(1024)
                    while (isActive && settings.musicSyncEnabled) {
                        val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                        if (read > 0) {
                            for (i in 0 until read) floatBuffer[i] = readBuffer[i] / 32768f
                            musicColor = fftProcessor.process(floatBuffer.copyOfRange(0, read), sampleRate)
                        }
                        delay(40)
                    }
                }
            } catch (e: Exception) {
                Log.e("RGB", "Music sync error", e)
            } finally {
                runCatching { audioRecord.stop(); audioRecord.release() }
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
        val rgb = ((color.r * k).toInt() shl 16) or ((color.g * k).toInt() shl 8) or (color.b * k).toInt()
        // Don't hammer the lights service with identical values; re-send at most once a second as a keep-alive.
        val now = SystemClock.elapsedRealtime()
        if (rgb == lastRgb && now - lastSentAt < 1000L) return
        val ok = MiuiLightsBinder.setColor(rgb)
        if (ok) { ownsRing = true; lastRgb = rgb; lastSentAt = now }
        if (loopN % 40L == 1L) DiagLog.log("apply", "rgb=%06X ok=$ok owns=$ownsRing".format(rgb))
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
        return if (start <= end) hour in start until end else hour >= start || hour < end
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("X8RGB")
            .setContentText(getString(R.string.service_notif_text))
            .setSmallIcon(R.drawable.ic_led_ring)
            .setOngoing(true)
            .build()

    companion object {
        const val ACTION_SET_COLOR   = "com.x8rgb.SET_COLOR"
        const val ACTION_STOP        = "com.x8rgb.STOP"
        const val EXTRA_COLOR_PACKED = "color_packed"
        const val EXTRA_ANIMATION_ID = "animation_id"
    }
}