package com.danzucker.stitchpad.feature.reports.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for [profitTrend] — the Profit KPI trend arrow's direction must be
 * decided from the SIGNED amount change, not [com.danzucker.stitchpad.feature.reports.domain.model.Kpi.deltaPercent].
 * A percentage assumes a non-negative baseline, which breaks in loss periods: e.g.
 * previous -5000 -> current -10000 (the loss doubled) would compute to +100%, a lying "up"
 * arrow for a period that got objectively worse.
 */
class ProfitTrendTest {

    @Test
    fun previousLossWorsens_isDown() {
        // previous = -5000, current = -10000: deltaAmount = -5000 (more negative == worse).
        assertEquals(ProfitTrend.DOWN, profitTrend(deltaAmount = -10_000.0 - (-5_000.0)))
    }

    @Test
    fun previousLossImproves_isUp() {
        // previous = -5000, current = -2000: deltaAmount = +3000 (loss shrank == better).
        assertEquals(ProfitTrend.UP, profitTrend(deltaAmount = -2_000.0 - (-5_000.0)))
    }

    @Test
    fun profitGrows_isUp() {
        // previous = 10000, current = 15000: deltaAmount = +5000.
        assertEquals(ProfitTrend.UP, profitTrend(deltaAmount = 15_000.0 - 10_000.0))
    }

    @Test
    fun noChange_isFlat() {
        assertEquals(ProfitTrend.FLAT, profitTrend(deltaAmount = 0.0))
    }
}
