package com.x8rgb.system

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.x8rgb.domain.model.RgbColor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG  = "RingBrightness"
private const val KEY  = "lamp_effect_brightness"

/**
 * Controls HyperOS ring brightness via Settings.Global.
 *
 * WRITE_SECURE_SETTINGS is a privileged permission — must be granted via:
 *   adb shell pm grant com.x8rgb android.permission.WRITE_SECURE_SETTINGS
 *   OR Shizuku (see ShizukuGrantHelper).
 *
 * All write failures are caught silently; callers receive [WriteResult].
 * UI layer should gate brightness slider on [hasPermission] to prevent user confusion.
 */
@Singleton
class RingBrightnessController @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {

    sealed class WriteResult {
        object Success                      : WriteResult()
        object PermissionDenied             : WriteResult()
        data class Failure(val msg: String) : WriteResult()
    }

    fun hasPermission(): Boolean = try {
        // Probe write; value 130 is HyperOS default — safe to re-write
        Settings.Global.putInt(ctx.contentResolver, KEY, read())
        true
    } catch (_: SecurityException) { false }
    catch (_: Exception)           { false }

    fun read(): Int =
        Settings.Global.getInt(ctx.contentResolver, KEY, RgbColor.DEFAULT_BRIGHTNESS)
            .coerceIn(RgbColor.BRIGHTNESS_MIN, RgbColor.BRIGHTNESS_MAX)

    fun write(brightness: Int): WriteResult {
        val clamped = brightness.coerceIn(RgbColor.BRIGHTNESS_MIN, RgbColor.BRIGHTNESS_MAX)
        return try {
            val ok = Settings.Global.putInt(ctx.contentResolver, KEY, clamped)
            if (ok) WriteResult.Success
            else    WriteResult.Failure("Settings.Global.putInt returned false")
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS not granted", e)
            WriteResult.PermissionDenied
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error writing brightness", e)
            WriteResult.Failure(e.message ?: "unknown")
        }
    }
}
