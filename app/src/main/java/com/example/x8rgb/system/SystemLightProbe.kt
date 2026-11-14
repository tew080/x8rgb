package com.x8rgb.system

import android.content.Context
import android.provider.Settings

/**
 * Reads system back-light keys. Global settings reads need no permission;
 * writes do (and this device blocks them even for adb shell).
 */
object SystemLightProbe {
    val KEYS = listOf("settings_strip_light_enable", "lamp_effect_brightness", "charging_led_enabled")

    fun snapshot(ctx: Context): String = KEYS.joinToString(" ") { k ->
        "$k=${Settings.Global.getString(ctx.contentResolver, k) ?: "null"}"
    }
}
