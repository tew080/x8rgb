package com.x8rgb.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.x8rgb.service.RgbNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionStateChecker @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val brightnessController: RingBrightnessController,
) {
    data class PermissionState(
        val notificationListener: Boolean,
        val writeSecureSettings: Boolean,
        val postNotifications: Boolean,
        val shizukuAvailable: Boolean,
        val shizukuGranted: Boolean,
    )

    fun check(): PermissionState = PermissionState(
        notificationListener  = isNotificationListenerEnabled(),
        writeSecureSettings   = brightnessController.hasPermission(),
        postNotifications     = isPostNotificationsEnabled(),
        shizukuAvailable      = ShizukuGrantHelper.isShizukuInstalled(ctx),
        shizukuGranted        = ShizukuGrantHelper.isGranted(ctx),
    )

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    private fun isPostNotificationsEnabled(): Boolean {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        return nm.areNotificationsEnabled()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?: return false
        val cn = ComponentName(ctx, RgbNotificationListener::class.java).flattenToString()
        return flat.split(":").any { it.trim() == cn }
    }
}
