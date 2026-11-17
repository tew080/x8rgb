package com.x8rgb.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
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
import kotlinx.coroutines.CancellationException
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
import javax.inject.Inject

private const val CHANNEL_ID    = "x8rgb_service"
private const val NOTIF_ID      = 1
private const val TICK_INTERVAL = 50L     // 20 fps
private const val YIELD_POLL_MS = 10L     // check fast while another source holds the ring
private const val MUSIC_HOLD    = "music"  // arbiter key: music playing, the phone's music light owns the ring
private const val KEEP_ALIVE_MS = 1000L   // re-send same colour while stable
private const val POST_RESUME_MS = 200L   // after yield: reassert aggressively
private const val OBSERVER_DEBOUNCE_MS = 250L

@AndroidEntryPoint
class RgbForegroundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var effectEngine: EffectEngine
    @Inject lateinit var motionManager: MotionManager
    @Inject lateinit var musicLightManager: MusicLightManager
    @Inject lateinit var arbiter: SystemLightArbiter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var effectJob: Job? = null
    private var settingsJob: Job? = null
    private var observerJob: Job? = null

    @Volatile private var targetColor: RgbColor = RgbColor.OFF
    @Volatile private var targetAnimationId: Int = EffectEngine.EFFECT_STATIC
    @Volatile private var settings: AppSettingsEntity = AppSettingsEntity()

    private var ownsRing = false
    private var loopN = 0L
    private var lastRgb = -1
    private var lastSentAt = 0L
    private var yielding = false
    private var yieldStartedAt = 0L
    /** Until this elapsedRealtime, identical rgb is still re-sent (post-yield ownership). */
    private var forceUntil = 0L
    private var musicHeld = false
    private var pollN = 0L
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            syncMusicHold()
        }
    }
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
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)

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
                lastRgb = -1  // new target must paint even if rgb matches keep-alive cache
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
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        debugRunner?.unregister()
        releaseRing()
        motionManager.stop()
        uninstallLightObserver()
        effectJob?.cancel()
        settingsJob?.cancel()
        observerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun installLightObserver() {
        if (lightObserver != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Debounce: system may fire several keys in a burst.
                observerJob?.cancel()
                observerJob = scope.launch {
                    delay(OBSERVER_DEBOUNCE_MS)
                    suppressSystemCompetitors()
                    lastRgb = -1   // system may have painted over us
                }
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
        observerJob?.cancel()
        observerJob = null
    }

    private fun suppressSystemCompetitors() {
        SystemLightGuard.suppressChargingLed(this)
        musicLightManager.disableAllSystemMusicLights()
    }

    private fun startEffectLoop() {
        effectJob?.cancel()
        effectJob = scope.launch {
            while (isActive) try {
                if (paused) { delay(TICK_INTERVAL); continue }
                if (pollN++ % 20L == 0L) syncMusicHold()   // about once a second: safety net for missed callbacks

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
                    forceUntil = SystemClock.elapsedRealtime() + POST_RESUME_MS * 5
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never let one bad tick stop the loop: log it and carry on.
                DiagLog.log("err", "effect loop: $e")
                delay(TICK_INTERVAL)
            }
        }
    }

    /** Music playing system-wide means the phone's music light paints the ring: yield while it plays. */
    private fun syncMusicHold() {
        val playing = audioManager.isMusicActive
        if (playing) {
            if (!musicHeld) DiagLog.log("yield", "music playing, yield ring")
            musicHeld = true
            arbiter.hold(MUSIC_HOLD)   // re-hold each call so a long song never lets the hold expire
        } else if (musicHeld) {
            musicHeld = false
            arbiter.release(MUSIC_HOLD)
            DiagLog.log("yield", "music stopped, release ring")
        }
    }

    private fun applyBaseOrRelease() {
        // Ambient effects supply their own colours; no user-picked base colour.
        val effectId = effectEngine.sanitize(settings.ambientEffectId)
        if (effectId == EffectEngine.EFFECT_STATIC) {
            releaseRing()
            return
        }
        val frame = effectEngine.compute(effectId, tick, RgbColor(255, 255, 255))
        applyColor(frame)
        tick++
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepository.observe().collectLatest { s ->
                settings = s ?: AppSettingsEntity()
                if (settings.masterEnabled) {
                    motionManager.start()
                    suppressSystemCompetitors()
                    lastRgb = -1
                } else {
                    motionManager.stop()
                    releaseRing()
                }
            }
        }
    }

    private fun applyColor(color: RgbColor) {
        val k = settings.brightness / 255f
        val rgb = ((color.r * k).toInt() shl 16) or ((color.g * k).toInt() shl 8) or (color.b * k).toInt()
        val now = SystemClock.elapsedRealtime()
        val force = now < forceUntil
        val gap = if (force) POST_RESUME_MS else KEEP_ALIVE_MS
        // Skip only when stable and same colour; always paint while forceUntil is active.
        if (!force && rgb == lastRgb && now - lastSentAt < gap) return
        val ok = MiuiLightsBinder.setColor(rgb) || MiuiLightsBinder.setColor(rgb) // one retry
        if (ok) {
            ownsRing = true
            lastRgb = rgb
            lastSentAt = now
        } else {
            lastRgb = -1  // next tick retries; binder may be recovering
            if (loopN % 20L == 0L) DiagLog.log("apply", "FAIL rgb=%06X".format(rgb))
        }
        if (force || loopN % 40L == 1L) {
            DiagLog.log("apply", "rgb=%06X ok=$ok force=$force owns=$ownsRing".format(rgb))
        }
    }

    private fun releaseRing() {
        if (!ownsRing) return
        ownsRing = false
        lastRgb = -1
        forceUntil = 0L
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
