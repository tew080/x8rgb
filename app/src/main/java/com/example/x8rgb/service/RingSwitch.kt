// X8RGB file: RingSwitch.kt
// Version: v0014
// Single owner of the system "Back light effects" switch (settings_strip_light_enable).
// History:
//   0014: introduced. Values verified on device: 0 disabled, 1 always, 2 face-down.
//
package com.x8rgb.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

private const val SWITCH_KEY = "settings_strip_light_enable"
private const val PREFS      = "rgb_prefs"
private const val PREF_MODE  = "ring_mode"
private const val PERM       = "android.permission.WRITE_SECURE_SETTINGS"

/** Values of settings_strip_light_enable, verified on the test device. */
enum class RingMode(val value: Int) {
    OFF(0),        // back-light effects disabled; this app writes nothing
    ALWAYS(2),     // back-light effects enabled at all times
    FACE_DOWN(1);  // enabled only when the phone is face down (system behaviour)

    companion object {
        fun fromValue(v: Int?): RingMode = values().firstOrNull { it.value == v } ?: ALWAYS
    }
}

object RingSwitch {

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    /** The mode the user chose. Stored in SharedPreferences, so no DB migration is needed. */
    fun savedMode(ctx: Context): RingMode =
        RingMode.fromValue(prefs(ctx).getInt(PREF_MODE, RingMode.ALWAYS.value))

    /** Current system value, or null if it cannot be read. */
    fun systemMode(ctx: Context): RingMode? =
        runCatching { RingMode.fromValue(Settings.Global.getInt(ctx.contentResolver, SWITCH_KEY)) }.getOrNull()

    /**
     * Writes the system switch and remembers the choice.
     * Returns false if WRITE_SECURE_SETTINGS is missing or the write failed.
     */
    fun set(ctx: Context, mode: RingMode): Boolean {
        val ok = runCatching { Settings.Global.putInt(ctx.contentResolver, SWITCH_KEY, mode.value) }
            .getOrDefault(false)
        if (ok) prefs(ctx).edit().putInt(PREF_MODE, mode.value).apply()
        return ok
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
