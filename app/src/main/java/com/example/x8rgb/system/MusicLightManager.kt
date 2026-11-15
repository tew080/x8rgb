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

/**
 * Bridges HyperOS 3 built-in music-sync via AudioManager hidden parameters.
 *
 * Response format: "pkg1=true;pkg2=false;..."
 * These are MIUI/HyperOS-specific non-standard AudioManager parameters.
 * They silently no-op on AOSP ROMs — safe to call regardless of device.
 */
@Singleton
class MusicLightManager @Inject constructor(
    @ApplicationContext ctx: Context,
) {
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Returns map of packageName → isEnabled from HyperOS state string. */
    fun getState(): Map<String, Boolean> = try {
        val raw = audioManager.getParameters(GET_STATE_KEY) ?: return emptyMap()
        // Raw: "pkg1=true;pkg2=false;"
        raw.split(";")
            .filter { it.contains("=") }
            .associate { entry ->
                val (k, v) = entry.split("=", limit = 2)
                k.trim() to (v.trim() == "true")
            }
    } catch (e: Exception) {
        Log.w(TAG, "getState failed — non-HyperOS device?", e)
        emptyMap()
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        try {
            audioManager.setParameters("$SET_STATE_KEY$packageName:$enabled")
        } catch (e: Exception) {
            Log.w(TAG, "setParameters failed for $packageName", e)
        }
    }

    /**
     * Disables HyperOS music-light for every package currently enabled.
     * Call when app music-sync turns ON so system effects do not stack on the ring.
     * Returns number of packages turned off.
     */
    fun disableAllSystemMusicLights(): Int {
        val state = getState()
        var n = 0
        for ((pkg, enabled) in state) {
            if (enabled) {
                setPackageEnabled(pkg, false)
                n++
            }
        }
        Log.i(TAG, "disabled system music-light for $n packages")
        return n
    }
}
