package com.danzucker.stitchpad.core.smartinfra.domain.quota

import kotlinx.coroutines.flow.Flow

/**
 * Snapshot of the fields PlanCard needs from `users/{uid}/usage/smart_drafts`.
 *
 * Either field is null when the underlying doc doesn't exist (no Smart call has
 * been made yet) or the specific field is absent (pre-V1.0 docs). Callers fall
 * back to other sources in that case — user-doc `bonusCoins` for the bonus, the
 * in-process `SmartUsageStore` for the monthly count.
 */
data class SmartUsageSnapshot(
    val bonusBalance: Int?,
    val monthlyCount: Int?,
) {
    companion object {
        val Empty = SmartUsageSnapshot(bonusBalance = null, monthlyCount = null)
    }
}

/**
 * Observes the server-side `users/{uid}/usage/smart_drafts` doc for fields the
 * client needs to keep PlanCard in sync with real Smart consumption.
 *
 * Exposes both `bonusBalance` (First Month chip) and `count` (post-First-Month
 * chip) in a single snapshot so the ViewModel can combine them in one flow.
 */
interface SmartUsageDocSource {
    fun observeSnapshot(userId: String): Flow<SmartUsageSnapshot>
}
