package com.x8rgb.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

enum class RingMode(val id: String, val systemValue: Int) {
    OFF("off", 0),
    ALWAYS("always", 2),
    FACE_DOWN("face_down", 2);

    companion object {
        fun fromId(id: String?): RingMode =
            entries.firstOrNull { it.id == id } ?: ALWAYS

        fun fromSystemValue(v: Int?): RingMode? =
            entries.firstOrNull { it.systemValue == v }
    }
}

private const val FACE_DOWN_SYSTEM_VALUE = 1  // ตัวอย่างเท่านั้น ต้องยืนยันก่อนใช้

object RingSwitch {
    private const val TAG = "RingSwitch"
    private const val SWITCH_KEY = "settings_strip_light_enable"
    private const val PREFS      = "rgb_prefs"
    private const val PREF_MODE  = "ring_mode"
    private const val PERM       = "android.permission.WRITE_SECURE_SETTINGS"

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    private const val PREF_MODE_ID = "ring_mode_id"   // ใช้ key ใหม่ เพราะ key เดิมเก็บเป็น Int

    fun savedMode(ctx: Context): RingMode =
        RingMode.fromId(prefs(ctx).getString(PREF_MODE_ID, null))

    fun systemMode(ctx: Context): RingMode? =
        runCatching {
            RingMode.fromSystemValue(Settings.Global.getInt(ctx.contentResolver, SWITCH_KEY))
        }.getOrNull()

    fun set(ctx: Context, mode: RingMode): Boolean {
        val ok = try {
            Settings.Global.putInt(ctx.contentResolver, SWITCH_KEY, mode.systemValue) &&
                Settings.Global.getInt(ctx.contentResolver, SWITCH_KEY, -1) == mode.systemValue
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set system setting $SWITCH_KEY", e)
            false
        }
        prefs(ctx).edit().putString(PREF_MODE_ID, mode.id).apply()
        return ok
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}