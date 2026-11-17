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

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            return (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }
    }

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
                    intent.getIntExtra("v", RingMode.FACE_DOWN.systemValue))
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
                "probe" -> {
                    // Look at the phone: does any value of the 3rd/4th int light only one ring/segment?
                    pause(true)
                    try {
                        for (a in 0..7) {
                            log("arg3=$a arg4=0 -> %b".format(MiuiLightsBinder.setColorRaw(0xFF0000, a, 0)))
                            delay(1500)
                            MiuiLightsBinder.off()
                            delay(300)
                        }
                        for (b in 1..8) {
                            log("arg3=3 arg4=$b -> %b".format(MiuiLightsBinder.setColorRaw(0x00FF00, 3, b)))
                            delay(1500)
                            MiuiLightsBinder.off()
                            delay(300)
                        }
                    } finally {
                        MiuiLightsBinder.off()
                        pause(false)
                    }
                }
                "txscan" -> {
                    // Same payload, different transaction codes. Watch the ring.
                    pause(true)
                    try {
                        for (code in 1..10) {
                            log("tx=$code -> %b".format(MiuiLightsBinder.setColorRaw(0xFF0000, 3, 0, code)))
                            delay(1500)
                            MiuiLightsBinder.off()
                            delay(300)
                        }
                    } finally {
                        MiuiLightsBinder.off()
                        pause(false)
                    }
                }
                "shapes" -> {
                    // For tx 1..4 try several argument layouts. A layout with no exception = matches the real signature.
                    pause(true)
                    try {
                        val rgb = 0xFFFFFF
                        val argb = (0xFF000000L or rgb.toLong()).toInt()
                        val me = MiuiLightsBinder.callerPackage
                        val shapes = listOf(
                            "A  rgb,pkg,3,0"  to listOf<Any>(rgb, me, 3, 0),
                            "Aa argb,pkg,3,0" to listOf<Any>(argb, me, 3, 0),
                            "B  rgb,pkg,3"    to listOf<Any>(rgb, me, 3),
                            "Ba argb,pkg,3"   to listOf<Any>(argb, me, 3),
                            "C  rgb,pkg"      to listOf<Any>(rgb, me),
                            "D  rgb"          to listOf<Any>(rgb),
                            "E  pkg,rgb"      to listOf<Any>(me, rgb),
                        )
                        for (code in 1..4) for ((name, args) in shapes) {
                            log("tx=$code $name -> ${MiuiLightsBinder.rawTransact(code, args)}")
                            delay(1300)
                            MiuiLightsBinder.off()
                            delay(200)
                        }
                    } finally {
                        MiuiLightsBinder.off()
                        pause(false)
                    }
                }
                "matrix" -> {
                    // One-shot guided test: for each SYSTEM ring mode, flash R/G/B straight through the binder.
                    // Watch the ring and note which "PHASE" lines are lit. Original system mode is restored at the end.
                    pause(true)
                    val key = "settings_strip_light_enable"
                    val resolver = ctx.contentResolver
                    val original = runCatching { android.provider.Settings.Global.getInt(resolver, key) }.getOrNull()
                    val pm = ctx.getSystemService(android.os.PowerManager::class.java)
                    try {
                        for (sys in listOf(1, 2, 0)) {
                            val wrote = runCatching { android.provider.Settings.Global.putInt(resolver, key, sys) }
                            log("=== system mode=$sys write=${wrote.getOrNull() ?: wrote.exceptionOrNull()?.javaClass?.simpleName} " +
                                "readBack=${runCatching { android.provider.Settings.Global.getInt(resolver, key) }.getOrNull()}")
                            delay(800)
                            for ((name, c) in listOf("RED" to 0xFF0000, "GREEN" to 0x00FF00, "BLUE" to 0x0000FF)) {
                                val ok = MiuiLightsBinder.setColor(c)
                                log("PHASE sys=$sys $name ok=$ok screenOn=${pm.isInteractive}")
                                delay(2000)
                                MiuiLightsBinder.off()
                                delay(400)
                            }
                        }
                    } finally {
                        MiuiLightsBinder.off()
                        original?.let { runCatching { android.provider.Settings.Global.putInt(resolver, key, it) } }
                        log("restored system mode=$original now=${runCatching { android.provider.Settings.Global.getInt(resolver, key) }.getOrNull()}")
                        pause(false)
                    }
                }
                "rate" -> {
                    // Does the ring survive rapid calls? Watch which PHASE lines are lit.
                    pause(true)
                    try {
                        val cols = listOf(0xFF0000, 0x00FF00, 0x0000FF)
                        for (interval in listOf(50L, 100L, 250L, 500L)) {
                            log("=== PHASE changing colour every ${interval}ms (3s)")
                            val end = SystemClock.elapsedRealtime() + 3000
                            var i = 0
                            while (SystemClock.elapsedRealtime() < end) {
                                MiuiLightsBinder.setColor(cols[i++ % 3])
                                delay(interval)
                            }
                            MiuiLightsBinder.off(); delay(800)
                        }
                        for (interval in listOf(50L, 500L)) {
                            log("=== PHASE same colour (white) re-sent every ${interval}ms (3s)")
                            val end = SystemClock.elapsedRealtime() + 3000
                            while (SystemClock.elapsedRealtime() < end) {
                                MiuiLightsBinder.setColor(0xFFFFFF)
                                delay(interval)
                            }
                            MiuiLightsBinder.off(); delay(800)
                        }
                    } finally {
                        MiuiLightsBinder.off()
                        pause(false)
                    }
                }
            }
        } catch (e: Exception) {
            log("ERROR: $e")
        }
    }
}
