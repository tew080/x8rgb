package com.x8rgb.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row settings table (id always = 1). */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int            = 1,
    val brightness: Int                = 130,     // 80–255
    val quietHoursEnabled: Boolean     = false,
    val quietHoursStart: Int           = 22,      // hour 0–23
    val quietHoursEnd: Int             = 7,
    val masterEnabled: Boolean         = true,
    val musicSyncEnabled: Boolean      = false,
    val motionReactiveEnabled: Boolean = false,
)
