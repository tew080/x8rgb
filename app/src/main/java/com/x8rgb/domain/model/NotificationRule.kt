package com.x8rgb.domain.model

import com.x8rgb.domain.engine.EffectEngine

enum class RuleType { CONTACT, GROUP, APP }

data class NotificationRule(
    val id: Long              = 0,
    val type: RuleType,
    val key: String,
    val displayName: String,
    val color: RgbColor,
    val animationId: Int      = EffectEngine.EFFECT_STATIC,
    val isEnabled: Boolean    = true,
)
