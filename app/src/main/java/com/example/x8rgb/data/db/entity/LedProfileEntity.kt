package com.x8rgb.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.x8rgb.domain.model.LedProfile
import com.x8rgb.domain.model.RgbColor

@Entity(tableName = "led_profiles")
data class LedProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated RgbColor.packed longs — no Room TypeConverter needed, stays primitive */
    val colorSequencePacked: String,
    val speedMs: Int = 500,
    val effectId: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toDomain() = LedProfile(
        id            = id,
        name          = name,
        colorSequence = colorSequencePacked
            .split(",")
            .filter { it.isNotBlank() }
            .map { RgbColor(it.trim().toLong()) },
        speedMs       = speedMs,
        effectId      = effectId,
        createdAt     = createdAt,
    )

    companion object {
        fun fromDomain(d: LedProfile) = LedProfileEntity(
            id                   = d.id,
            name                 = d.name,
            colorSequencePacked  = d.colorSequence.joinToString(",") { it.packed.toString() },
            speedMs              = d.speedMs,
            effectId             = d.effectId,
            createdAt            = d.createdAt,
        )
    }
}
