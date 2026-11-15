// X8RGB file: RingSwitch.kt
// Version: v0021
// Single owner of the system "Back light effects" switch (settings_strip_light_enable).
// Values: 0: Off, 1: Always, 2: Face-down (standard HyperOS mapping).
//
package com.x8rgb.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

enum class RingMode(val value: Int) {
    OFF(0),
    ALWAYS(1),
    FACE_DOWN(2);

    companion object {
        fun fromValue(v: Int?): RingMode = entries.firstOrNull { it.value == v } ?: ALWAYS
    }
}

object RingSwitch {
    private const val SWITCH_KEY = "settings_strip_light_enable"
    private const val PREFS      = "rgb_prefs"
    private const val PREF_MODE  = "ring_mode"
    private const val PERM       = "android.permission.WRITE_SECURE_SETTINGS"

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    fun savedMode(ctx: Context): RingMode =
        RingMode.fromValue(prefs(ctx).getInt(PREF_MODE, RingMode.ALWAYS.value))

    fun systemMode(ctx: Context): RingMode? =
        runCatching { RingMode.fromValue(Settings.Global.getInt(ctx.contentResolver, SWITCH_KEY)) }.getOrNull()

    fun set(ctx: Context, mode: RingMode): Boolean {
        val ok = try {
            Settings.Global.putInt(ctx.contentResolver, SWITCH_KEY, mode.value)
        } catch (e: SecurityException) {
            false
        }
        if (ok) prefs(ctx).edit().putInt(PREF_MODE, mode.value).apply()
        return ok
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
