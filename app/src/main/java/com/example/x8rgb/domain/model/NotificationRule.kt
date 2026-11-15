package com.x8rgb.domain.model

import com.x8rgb.domain.engine.EffectEngine

/**
 * Priority: CONTACT > GROUP > APP.
 * Lower ordinal = higher priority.
 */
enum class RuleType { CONTACT, GROUP, APP }

data class NotificationRule(
    val id: Long              = 0,
    val type: RuleType,
    /** packageName for APP, contact lookup key for CONTACT, group label for GROUP */
    val key: String,
    val displayName: String,
    val color: RgbColor,
    val animationId: Int      = EffectEngine.EFFECT_STATIC,
    val isEnabled: Boolean    = true,
)
