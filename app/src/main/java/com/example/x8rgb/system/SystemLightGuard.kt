package com.x8rgb.system

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.x8rgb.util.DiagLog

/**
 * Suppresses HyperOS sources that overwrite the ring while we own it.
 * Requires WRITE_SECURE_SETTINGS for writes; reads always safe.
 *
 * Keys verified on HyperOS test devices:
 *   charging_led_enabled        0/1
 *   settings_strip_light_enable 0/1/2 (owned by RingSwitch)
 *   lamp_effect_brightness      80..255
 */
object SystemLightGuard {
    private const val TAG = "SystemLightGuard"
    private const val CHARGING_LED = "charging_led_enabled"

    val WATCH_KEYS = listOf(
        CHARGING_LED,
        "settings_strip_light_enable",
        "lamp_effect_brightness",
    )

    fun snapshot(ctx: Context): String =
        WATCH_KEYS.joinToString(" ") { k ->
            "$k=${Settings.Global.getString(ctx.contentResolver, k) ?: "null"}"
        }

    /**
     * Best-effort: turn off charging LED so red/yellow/green status does not paint the ring.
     * Returns false if WRITE_SECURE_SETTINGS missing or write failed.
     */
    fun suppressChargingLed(ctx: Context): Boolean {
        return try {
            val ok = Settings.Global.putInt(ctx.contentResolver, CHARGING_LED, 0)
            DiagLog.log("sys", "suppress charging_led ok=$ok")
            ok
        } catch (e: SecurityException) {
            DiagLog.log("sys", "suppress charging_led denied")
            false
        } catch (t: Throwable) {
            Log.w(TAG, "suppressChargingLed", t)
            false
        }
    }

    fun restoreChargingLed(ctx: Context, enabled: Boolean = true): Boolean {
        return try {
            Settings.Global.putInt(ctx.contentResolver, CHARGING_LED, if (enabled) 1 else 0)
        } catch (_: Throwable) {
            false
        }
    }
}
