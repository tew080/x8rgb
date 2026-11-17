package com.x8rgb.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row settings table (id always = 1). */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int            = 1,
    val brightness: Int                = 130,     // 80–255
    val masterEnabled: Boolean         = true,
    /** Idle base colour (RgbColor.packed). 0 = no base, release when idle. */
    val baseColorPacked: Long          = 0L,
    /** Lock: this colour overrides notifications until turned off. */
    val forceHoldEnabled: Boolean      = false,
    val forceHoldColorPacked: Long     = 0xFFFFFF82L,
    /** Effect while locked. EffectEngine.EFFECT_STATIC = solid colour only. */
    @ColumnInfo(defaultValue = "10")
    val forceHoldEffectId: Int         = 10,
    /** Always-on effect while idle. EffectEngine.EFFECT_STATIC = solid base only. */
    val ambientEffectId: Int           = 10,
)
