package com.x8rgb.domain.model

data class LedProfile(
    val id: Long            = 0,
    val name: String,
    /** Serialised as JSON string in Room — list of RgbColor.packed values */
    val colorSequence: List<RgbColor>,
    val speedMs: Int        = 500,    // ms per step
    val effectId: Int       = 0,
    val createdAt: Long     = System.currentTimeMillis(),
)
