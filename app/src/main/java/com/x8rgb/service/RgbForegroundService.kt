package com.x8rgb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.x8rgb.R
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.system.MiuiLightsBinder
import com.x8rgb.system.MotionManager
import com.x8rgb.system.MusicLightManager
import com.x8rgb.system.RingMode
import com.x8rgb.system.RingSwitch
import com.x8rgb.system.SystemLightArbiter
import com.x8rgb.system.SystemLightGuard
import com.x8rgb.util.DiagLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val CHANNEL_ID    = "x8rgb_service"
private const val NOTIF_ID      = 1
private const val TICK_INTERVAL = 50L     // 20 fps
private const val YIELD_POLL_MS = 10L     // check fast while another source holds the ring

@AndroidEntryPoint
class RgbForegroundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var effectEngine: EffectEngine
    @Inject lateinit var motionManager: MotionManager
    @Inject lateinit var musicLightManager: MusicLightManager
    @Inject lateinit var arbiter: SystemLightArbiter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var targetColor: RgbColor = RgbColor.OFF
    @Volatile private var targetAnimationId: Int = EffectEngine.EFFECT_STATIC
    @Volatile private var settings: AppSettingsEntity = AppSettingsEntity()

    private var ownsRing = false
    private var loopN = 0L
    private var lastRgb = -1
    private var lastSentAt = 0L
    private var yielding = false
    private var yieldStartedAt = 0L
    @Volatile private var paused = false          // set by DebugTestRunner while a test drives the ring
    private var debugRunner: DebugTestRunner? = null
    private var tick = 0L
    private var lightObserver: ContentObserver? = null

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
        uninstallLightObserver()
        scope.cancel()
        super.onDestroy()
    }

    private fun installLightObserver() {
        if (lightObserver != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
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
                        yieldStartedAt = SystemClock.elapsedRealtime()
                        lastRgb = -1
                        DiagLog.log("yield", "pause ring, held by $busy")
                    }
                    delay(YIELD_POLL_MS)
                    continue
                }
                if (yielding) {
                    yielding = false
                    lastRgb = -1                       // re-send our colour on this same tick
                    suppressSystemCompetitors()        // the system may have changed its lights meanwhile
                    DiagLog.log("yield", "resume ring after ${SystemClock.elapsedRealtime() - yieldStartedAt} ms")
                }

                val mode = RingSwitch.savedMode(this@RgbForegroundService)
                val shouldLightUp = when (mode) {
                    RingMode.OFF -> false
                    RingMode.ALWAYS -> true
                    RingMode.FACE_DOWN -> motionManager.isFaceDown()
                }
                val master = settings.masterEnabled && shouldLightUp

                if (loopN++ % 40L == 0L) {   // every ~2 s
                    DiagLog.log("loop", "master=${settings.masterEnabled} mode=$mode lightUp=$shouldLightUp " +
                        "lock=${settings.forceHoldEnabled} base=${settings.baseColorPacked} " +
                        "ambient=${settings.ambientEffectId} target=${targetColor.packed}/$targetAnimationId " +
                        "bright=${settings.brightness}")
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
                    suppressSystemCompetitors()
                } else {
                    motionManager.stop()
                }
            }
        }
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
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
