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

    @Test
    fun sanitizeDuration_keepsValidDuration() {
        assertEquals(195.0, sanitizeDuration(195.0))
    }

    @Test
    fun sanitizeDuration_nullStaysNull() {
        assertEquals(null, sanitizeDuration(null))
    }

    @Test
    fun sanitizeDuration_nanCollapsesToNull() {
        assertEquals(null, sanitizeDuration(Double.NaN))
    }

    @Test
    fun sanitizeDuration_infiniteCollapsesToNull() {
        assertEquals(null, sanitizeDuration(Double.POSITIVE_INFINITY))
    }

    @Test
    fun sanitizeDuration_negativeCollapsesToNull() {
        assertEquals(null, sanitizeDuration(-1.0))
    }

    @Test
    fun sanitizeDuration_zeroCollapsesToNull() {
        assertEquals(null, sanitizeDuration(0.0))
    }
}
