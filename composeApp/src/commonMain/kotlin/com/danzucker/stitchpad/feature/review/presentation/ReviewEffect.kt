package com.danzucker.stitchpad.feature.review.presentation

/** One-shot side effects the host runs (Compose-bound work the controller can't do). */
sealed interface ReviewEffect {
    data class OpenFeedback(val url: String) : ReviewEffect
}
