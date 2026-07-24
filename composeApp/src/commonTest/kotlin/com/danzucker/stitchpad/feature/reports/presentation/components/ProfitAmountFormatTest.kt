package com.danzucker.stitchpad.feature.reports.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for [formatProfitAmount] — the Reports Profit tile's headline amount
 * must read a loss AS a loss ("−₦8,000"), not as a positive-looking figure ("₦-8,000"),
 * matching [com.danzucker.stitchpad.feature.order.presentation.detail.components.OrderCostsCard]'s
 * per-order `ProfitBand` formatting.
 */
class ProfitAmountFormatTest {

    @Test
    fun positiveProfit_formatsWithoutSign() {
        assertEquals("₦12,000", formatProfitAmount(12_000.0))
    }

    @Test
    fun negativeProfit_formatsWithLeadingMinusAndMagnitudeOnly() {
        assertEquals("−₦8,000", formatProfitAmount(-8_000.0))
    }

    @Test
    fun zeroProfit_formatsWithoutSign() {
        assertEquals("₦0", formatProfitAmount(0.0))
    }
}
