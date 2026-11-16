package com.x8rgb.system

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

private const val TAG             = "ShizukuGrantHelper"
private const val SHIZUKU_PKG     = "moe.shizuku.privileged.api"
private const val WRITE_SECURE    = "android.permission.WRITE_SECURE_SETTINGS"

/**
 * Optional Shizuku integration.
 */
object ShizukuGrantHelper {

    fun isShizukuInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun isGranted(ctx: Context): Boolean {
        if (!isShizukuInstalled(ctx)) return false
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val checkMethod  = shizukuClass.getMethod("checkSelfPermission")
            checkMethod.invoke(null) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.d(TAG, "isGranted reflection failed: ${e.message}")
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            val shizukuClass    = Class.forName("rikka.shizuku.Shizuku")
            val requestMethod   = shizukuClass.getMethod("requestPermission", Int::class.java)
            requestMethod.invoke(null, requestCode)
        } catch (e: Exception) {
            Log.w(TAG, "requestPermission failed: ${e.message}")
        }
    }

    fun grantWriteSecureSettings(packageName: String): Boolean = try {
        val shizukuClass  = Class.forName("rikka.shizuku.Shizuku")
        val newProcessMethod = shizukuClass.getMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        val cmd = arrayOf("sh", "-c", "pm grant $packageName $WRITE_SECURE")
        val process = newProcessMethod.invoke(null, cmd, null, null) as Process
        val exit = process.waitFor()
        exit == 0
    } catch (e: Exception) {
        Log.e(TAG, "grantWriteSecureSettings failed", e)
        false
    }
}
