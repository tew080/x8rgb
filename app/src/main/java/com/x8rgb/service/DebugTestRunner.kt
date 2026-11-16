package com.x8rgb.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.x8rgb.R
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.domain.model.RuleType
import com.x8rgb.system.MiuiLightsBinder
import com.x8rgb.system.RingMode
import com.x8rgb.system.RingSwitch
import com.x8rgb.system.SystemLightProbe
import com.x8rgb.util.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ACTION_TEST   = "com.x8rgb.TEST"
private const val TEST_CHANNEL  = "x8rgb_test"
private const val TEST_NOTIF_ID = 4242

class DebugTestRunner(
    private val ctx: Context,
    private val rules: RuleRepository,
    private val pause: (Boolean) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var receiver: BroadcastReceiver? = null
    private var observer: ContentObserver? = null

    fun register() {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                DiagLog.clear()
                DiagLog.log("recv", "action=${intent.action} case=${intent.getStringExtra("case")}")
                if (intent.action != ACTION_TEST) return
                val case = intent.getStringExtra("case") ?: return
                runCase(case,
                    intent.getIntExtra("ms", 5000),
                    intent.getStringExtra("pkg"),
                    intent.getIntExtra("v", RingMode.FACE_DOWN.value))
            }
        }
        receiver = rx
        ContextCompat.registerReceiver(ctx, rx, IntentFilter(ACTION_TEST), ContextCompat.RECEIVER_EXPORTED)

        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                DiagLog.log("sys", "${uri?.lastPathSegment} changed: ${SystemLightProbe.snapshot(ctx)}")
            }
        }
        observer = obs
        SystemLightProbe.KEYS.forEach {
            ctx.contentResolver.registerContentObserver(
                android.provider.Settings.Global.getUriFor(it), false, obs)
        }
    }

    fun unregister() {
        runCatching { receiver?.let { ctx.unregisterReceiver(it) } }
        runCatching { observer?.let { ctx.contentResolver.unregisterContentObserver(it) } }
        receiver = null
        observer = null
        scope.cancel()
    }

    private fun runCase(case: String, holdMs: Int, pkg: String?, v: Int) = scope.launch {
        val t0 = SystemClock.elapsedRealtime()
        fun log(msg: String) = DiagLog.log("test", "T+${SystemClock.elapsedRealtime() - t0}ms $msg")
        try {
            log("case=$case")
            when (case) {
                "snapshot" -> {
                    log(SystemLightProbe.snapshot(ctx))
                    log(SystemLightProbe.permissions(ctx))
                    log("savedMode=${RingSwitch.savedMode(ctx)} systemMode=${RingSwitch.systemMode(ctx)}")
                }
                "colors" -> {
                    pause(true)
                    val prevPkg = MiuiLightsBinder.callerPackage
                    pkg?.let { MiuiLightsBinder.callerPackage = it }
                    log("caller=${MiuiLightsBinder.callerPackage}")
                    try {
                        for (c in listOf(0xFF0000, 0x00FF00, 0x0000FF, 0x000000)) {
                            log("setColor %06X -> %b".format(c, MiuiLightsBinder.setColor(c)))
                            delay(2000)
                        }
                    } finally {
                        MiuiLightsBinder.callerPackage = prevPkg
                        pause(false)
                    }
                }
                "notify" -> {
                    log(SystemLightProbe.permissions(ctx))
                    val nm = ctx.getSystemService(NotificationManager::class.java)
                    nm.createNotificationChannel(
                        NotificationChannel(TEST_CHANNEL, "x8rgb test", NotificationManager.IMPORTANCE_DEFAULT))
                    val rule = NotificationRule(
                        type = RuleType.APP, key = ctx.packageName,
                        displayName = "x8rgb test", color = RgbColor(255, 0, 0))
                    runCatching { rules.upsert(rule) }
                        .onSuccess { log("rule created") }
                        .onFailure { log("rule already exists; using it") }
                    delay(500)
                    val n = NotificationCompat.Builder(ctx, TEST_CHANNEL)
                        .setSmallIcon(R.drawable.ic_led_ring)
                        .setContentTitle("x8rgb test")
                        .setContentText("test")
                        .build()
                    nm.notify(TEST_NOTIF_ID, n)
                    log("posted id=$TEST_NOTIF_ID hold=${holdMs}ms")
                    delay(holdMs.toLong())
                    nm.cancel(TEST_NOTIF_ID)
                    log("cancelled")
                    delay(1500)
                }
                "strip" -> {
                    val mode = RingMode.fromValue(v)
                    val ok = RingSwitch.set(ctx, mode)
                    log("set mode=$mode ok=$ok")
                    delay(1500)
                    log("systemMode=${RingSwitch.systemMode(ctx)}")
                }
                else -> log("unknown case")
            }
        } finally {
            log("done ${SystemLightProbe.snapshot(ctx)}")
        }
    }

    companion object {
        fun isEnabled(ctx: Context): Boolean =
            ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
