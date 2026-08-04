package com.danzucker.stitchpad.feature.review.domain

/**
 * Everything [ReviewGate] needs to decide eligibility. installedAt + distinctOpenDays
 * are device-wide (device tenure); ordersCreated + lastPrompt* are per signed-in user.
 */
data class ReviewSignals(
    val installedAtMillis: Long,
    val distinctOpenDays: Int,
    val ordersCreated: Int,
    val lastPromptAtMillis: Long,
    val lastOutcome: ReviewOutcome,
)
