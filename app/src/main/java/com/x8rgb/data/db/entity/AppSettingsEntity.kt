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
    /** Idle base color (RgbColor.packed). 0 = no base, release when idle. */
    val baseColorPacked: Long          = 0L,
    /** Force-hold locks this color above notifications/music until cleared. */
    val forceHoldEnabled: Boolean      = false,
    val forceHoldColorPacked: Long     = 0xFFFFFF82L,
    /** Always-on effect while idle. EffectEngine.EFFECT_STATIC = solid base only. */
    val ambientEffectId: Int           = 10, // EffectEngine.EFFECT_STATIC
)
