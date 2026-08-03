package com.danzucker.stitchpad.feature.review.data

import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals

/**
 * Local, offline-safe persistence for review-prompt signals. Device-wide: install
 * timestamp + distinct open-days. Per-user (keyed by authUid): orders created + last
 * prompt time/outcome. No Firestore — mirrors OnboardingPreferences.
 */
interface ReviewPreferencesStore {
    /** Records now as the install time on first ever call; later calls are no-ops. */
    suspend fun stampInstallIfAbsent(nowMillis: Long)

    /** If [epochDay] differs from the last recorded open-day, bumps the distinct-day count. */
    suspend fun recordOpenDay(epochDay: Long)

    suspend fun incrementOrdersCreated(userId: String)

    suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long)

    /** Aggregates the device + per-user signals for [ReviewGate]. */
    suspend fun loadSignals(userId: String): ReviewSignals

    /** Debug-menu only: clears every review key. Idempotent. */
    suspend fun resetForDebug()
}
