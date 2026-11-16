package com.x8rgb.system

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings

/**
 * Reads system back-light keys and this app's notification permissions.
 * Global/Secure reads need no permission here; writes to them do, and this device
 * blocks them even for adb shell.
 */
object SystemLightProbe {
    val KEYS = listOf("settings_strip_light_enable", "lamp_effect_brightness", "charging_led_enabled")

    fun snapshot(ctx: Context): String = KEYS.joinToString(" ") { k ->
        "$k=${Settings.Global.getString(ctx.contentResolver, k) ?: "null"}"
    }

    /** Permissions that gate the notification path. */
    fun permissions(ctx: Context): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        val listener = flat.split(":").any { it.startsWith("${ctx.packageName}/") }
        return "notifications=${nm.areNotificationsEnabled()} listenerAccess=$listener"
    }
}
