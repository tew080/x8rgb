package com.x8rgb.domain.model

data class LedProfile(
    val id: Long            = 0,
    val name: String,
    val colorSequence: List<RgbColor>,
    val speedMs: Int        = 500,
    val effectId: Int       = 0,
    val createdAt: Long     = System.currentTimeMillis(),
)
