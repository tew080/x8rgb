package com.x8rgb.domain.engine

import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.domain.model.RuleType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which color to display for an incoming notification.
 *
 * Priority order (architecture spec):
 *   CONTACT > GROUP > APP
 *
 * Edge cases handled:
 * - Multiple CONTACT rules for same contact key: first match wins (stable sort by id).
 * - null contactKey (redacted lock screen): skip CONTACT tier entirely.
 * - No matching rule at any tier: returns null (caller decides fallback / OFF).
 */
@Singleton
class PriorityResolver @Inject constructor() {

    /**
     * @param packageName  Source app package (always available).
     * @param contactKey   Contacts lookup key; null when OS redacts lock screen content.
     * @param groupKey     Notification group key; null if not set.
     * @param rules        Pre-fetched enabled rules (caller caches, no DB hit on hot path).
     */
    fun resolve(
        packageName: String,
        contactKey: String?,
        groupKey: String?,
        rules: List<NotificationRule>,
    ): NotificationRule? {
        // Tier 1 — CONTACT
        if (contactKey != null) {
            rules.firstOrNull { it.type == RuleType.CONTACT && it.key == contactKey }
                ?.let { return it }
        }

        // Tier 2 — GROUP
        if (groupKey != null) {
            rules.firstOrNull { it.type == RuleType.GROUP && it.key == groupKey }
                ?.let { return it }
        }

        // Tier 3 — APP
        return rules.firstOrNull { it.type == RuleType.APP && it.key == packageName }
    }
}
