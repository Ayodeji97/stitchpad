package com.danzucker.stitchpad.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Shared motion language for StitchPad — calm & professional: subtle, quick,
 * restrained, never bouncy.
 *
 * Durations are not redefined here; they reference [DesignTokens] so there is a
 * single source of truth. This object only adds the easing curve and the
 * animation specs built on top of those durations. Keeping it separate from
 * [DesignTokens] preserves that file as a dependency-light value bag (no
 * compose-animation imports).
 */
object StitchPadMotion {

    /**
     * Standard decelerate curve (Material "fast-out, slow-in"). Both control
     * points stay within 0..1, so it never overshoots — no bounce.
     */
    val EaseStandard: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /** Press-feedback scale spec — quick (150ms), standard ease. */
    fun <T> press(): FiniteAnimationSpec<T> =
        tween(durationMillis = DesignTokens.durationQuick, easing = EaseStandard)

    /** Content cross-fade (e.g. label ⇄ progress) — quick, in place. */
    fun <T> contentFade(): FiniteAnimationSpec<T> =
        tween(durationMillis = DesignTokens.durationQuick, easing = EaseStandard)

    /** Color transitions (e.g. enabled ⇄ disabled tint) — standard 300ms. */
    fun <T> colorShift(): AnimationSpec<T> =
        tween(durationMillis = DesignTokens.durationTransition, easing = EaseStandard)

    /** Scale a control shrinks to while pressed. */
    const val PRESSED_SCALE: Float = 0.97f

    /** Resting (unpressed) scale. */
    const val RESTING_SCALE: Float = 1.0f
}
