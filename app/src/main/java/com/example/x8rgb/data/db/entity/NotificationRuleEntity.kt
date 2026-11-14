package com.x8rgb.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.domain.model.RuleType

@Entity(
    tableName = "notification_rules",
    indices = [Index(value = ["type", "key"], unique = true)]
)
data class NotificationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // RuleType.name()
    val key: String,
    val displayName: String,
    val colorPacked: Long,     // RgbColor.packed
    val animationId: Int = 0,
    val isEnabled: Boolean = true,
) {
    fun toDomain() = NotificationRule(
        id          = id,
        type        = RuleType.valueOf(type),
        key         = key,
        displayName = displayName,
        color       = RgbColor(colorPacked),
        animationId = animationId,
        isEnabled   = isEnabled,
    )

    companion object {
        fun fromDomain(d: NotificationRule) = NotificationRuleEntity(
            id          = d.id,
            type        = d.type.name,
            key         = d.key,
            displayName = d.displayName,
            colorPacked = d.color.packed,
            animationId = d.animationId,
            isEnabled   = d.isEnabled,
        )
    }
}
