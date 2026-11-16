package com.x8rgb.domain.engine

import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RuleType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriorityResolver @Inject constructor() {
    fun resolve(
        packageName: String,
        contactKey: String?,
        groupKey: String?,
        rules: List<NotificationRule>,
    ): NotificationRule? {
        if (contactKey != null) {
            rules.firstOrNull { it.type == RuleType.CONTACT && it.key == contactKey }
                ?.let { return it }
        }
        if (groupKey != null) {
            rules.firstOrNull { it.type == RuleType.GROUP && it.key == groupKey }
                ?.let { return it }
        }
        return rules.firstOrNull { it.type == RuleType.APP && it.key == packageName }
    }
}
