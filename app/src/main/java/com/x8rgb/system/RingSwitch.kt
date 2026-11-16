package com.x8rgb.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

enum class RingMode(val value: Int) {
    OFF(0),
    ALWAYS(1),
    FACE_DOWN(2);

    companion object {
        fun fromValue(v: Int?): RingMode = entries.firstOrNull { it.value == v } ?: ALWAYS
    }
}

object RingSwitch {
    private const val TAG = "RingSwitch"
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

    /**
     * Sets the ring mode. Note: Changing system settings requires WRITE_SECURE_SETTINGS.
     */
    fun set(ctx: Context, mode: RingMode): Boolean {
        Log.d(TAG, "Setting ring mode to: $mode (${mode.value})")
        val ok = try {
            // HyperOS key: 0=Off, 1=Always, 2=Face-down
            Settings.Global.putInt(ctx.contentResolver, SWITCH_KEY, mode.value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set system setting $SWITCH_KEY", e)
            false
        }
        
        // Always save to local prefs so the app remembers the user's choice even if system write fails
        prefs(ctx).edit().putInt(PREF_MODE, mode.value).apply()
        return ok
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
