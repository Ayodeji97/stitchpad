package com.danzucker.stitchpad.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeekSettleTest {

    @Test
    fun playerWithinToleranceOfTarget_settles() {
        assertTrue(isSeekSettled(actualSeconds = 29.5, targetSeconds = 30.0, pollTicksElapsed = 1))
    }

    @Test
    fun playerExactlyAtToleranceBoundary_settles() {
        assertTrue(isSeekSettled(actualSeconds = 29.0, targetSeconds = 30.0, pollTicksElapsed = 1))
    }

    @Test
    fun playerOvershootWithinTolerance_settles() {
        // Settling is symmetric: the player landing slightly past the target also counts.
        assertTrue(isSeekSettled(actualSeconds = 30.8, targetSeconds = 30.0, pollTicksElapsed = 1))
    }

    @Test
    fun playerFarFromTargetEarly_doesNotSettle() {
        assertFalse(isSeekSettled(actualSeconds = 5.0, targetSeconds = 30.0, pollTicksElapsed = 2))
    }

    @Test
    fun playerFarFromTargetAfterMaxTicks_settlesAnyway() {
        // Timeout guard: a seek that never converges (dead stream) must not pin the
        // scrubber to the target forever.
        assertTrue(isSeekSettled(actualSeconds = 5.0, targetSeconds = 30.0, pollTicksElapsed = 6))
    }

    @Test
    fun tickJustBeforeTimeout_doesNotSettle() {
        assertFalse(isSeekSettled(actualSeconds = 5.0, targetSeconds = 30.0, pollTicksElapsed = 5))
    }
}
