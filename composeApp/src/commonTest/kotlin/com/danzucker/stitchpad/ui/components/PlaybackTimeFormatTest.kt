package com.danzucker.stitchpad.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTimeFormatTest {

    @Test
    fun zeroSeconds_formatsAsZeroZero() {
        assertEquals("0:00", formatPlaybackTime(0.0))
    }

    @Test
    fun subMinute_formatsWithoutRounding() {
        // 59.9 must not round up to 1:00 — truncate, don't round.
        assertEquals("0:59", formatPlaybackTime(59.9))
    }

    @Test
    fun exactMinute_rollsToMinutes() {
        assertEquals("1:00", formatPlaybackTime(60.0))
    }

    @Test
    fun tenMinutes_formatsMinutesAndPaddedSeconds() {
        assertEquals("10:42", formatPlaybackTime(642.0))
    }

    @Test
    fun overAnHour_rollsIntoMinutes() {
        // No hour segment by design: 1h 1m 5s shows as 61:05.
        assertEquals("61:05", formatPlaybackTime(3665.0))
    }

    @Test
    fun nullSeconds_showsPlaceholder() {
        assertEquals("--:--", formatPlaybackTime(null))
    }

    @Test
    fun nanSeconds_showsPlaceholder() {
        assertEquals("--:--", formatPlaybackTime(Double.NaN))
    }

    @Test
    fun negativeSeconds_showsPlaceholder() {
        assertEquals("--:--", formatPlaybackTime(-1.0))
    }
}
