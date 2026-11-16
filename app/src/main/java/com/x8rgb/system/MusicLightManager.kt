package com.x8rgb.system

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG           = "MusicLightManager"
private const val GET_STATE_KEY = "Get_MusicLight_Package_State="
private const val SET_STATE_KEY = "Set_MusicLight_Package_State="

@Singleton
class MusicLightManager @Inject constructor(
    @ApplicationContext ctx: Context,
) {
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getState(): Map<String, Boolean> = try {
        val raw = audioManager.getParameters(GET_STATE_KEY) ?: return emptyMap()
        raw.split(";")
            .filter { it.contains("=") }
            .associate { entry ->
                val (k, v) = entry.split("=", limit = 2)
                k.trim() to (v.trim() == "true")
            }
    } catch (e: Exception) {
        Log.w(TAG, "getState failed", e)
        emptyMap()
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        try {
            audioManager.setParameters("$SET_STATE_KEY$packageName:$enabled")
        } catch (e: Exception) {
            Log.w(TAG, "setParameters failed for $packageName", e)
        }
    }

    fun disableAllSystemMusicLights(): Int {
        val state = getState()
        var n = 0
        for ((pkg, enabled) in state) {
            if (enabled) {
                setPackageEnabled(pkg, false)
                n++
            }
        }
        return n
    }
}
