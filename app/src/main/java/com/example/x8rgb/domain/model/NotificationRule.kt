package com.x8rgb.domain.model

/**
 * Priority: CONTACT > GROUP > APP (matches ZenithOrbitRGB / Aura spec).
 * Lower ordinal = higher priority in Comparable.
 */
enum class RuleType { CONTACT, GROUP, APP }

data class NotificationRule(
    val id: Long              = 0,
    val type: RuleType,
    /** packageName for APP, contact lookup key for CONTACT, group label for GROUP */
    val key: String,
    val displayName: String,
    val color: RgbColor,
    val animationId: Int      = 0,   // index into EffectEngine.EFFECTS
    val isEnabled: Boolean    = true,
)
