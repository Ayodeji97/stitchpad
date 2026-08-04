package com.danzucker.stitchpad.feature.review.presentation

import com.danzucker.stitchpad.feature.review.data.ReviewPreferencesStore
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import com.danzucker.stitchpad.feature.review.domain.ReviewSignals

class FakeReviewPreferences(
    var installedAtMillis: Long = 0L,
    var distinctOpenDays: Int = 0,
    var ordersByUser: MutableMap<String, Int> = mutableMapOf(),
    var lastPromptByUser: MutableMap<String, Long> = mutableMapOf(),
    var outcomeByUser: MutableMap<String, ReviewOutcome> = mutableMapOf(),
) : ReviewPreferencesStore {
    override suspend fun stampInstallIfAbsent(nowMillis: Long) {
        if (installedAtMillis == 0L) installedAtMillis = nowMillis
    }
    override suspend fun recordOpenDay(epochDay: Long) { distinctOpenDays += 1 }
    override suspend fun incrementOrdersCreated(userId: String) {
        ordersByUser[userId] = (ordersByUser[userId] ?: 0) + 1
    }
    override suspend fun recordPrompt(userId: String, outcome: ReviewOutcome, nowMillis: Long) {
        lastPromptByUser[userId] = nowMillis
        outcomeByUser[userId] = outcome
    }
    override suspend fun loadSignals(userId: String) = ReviewSignals(
        installedAtMillis = installedAtMillis,
        distinctOpenDays = distinctOpenDays,
        ordersCreated = ordersByUser[userId] ?: 0,
        lastPromptAtMillis = lastPromptByUser[userId] ?: 0L,
        lastOutcome = outcomeByUser[userId] ?: ReviewOutcome.NONE,
    )
    override suspend fun resetForDebug() {}
}
