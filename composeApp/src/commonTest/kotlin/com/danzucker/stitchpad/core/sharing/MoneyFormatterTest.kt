package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatterTest {
    @Test
    fun formatsMajorUnitsWithEachMarketCurrency() {
        assertEquals("₦1,234.50", formatMoney(1234.5, Currency.NGN))
        assertEquals("GH₵1,234.50", formatMoney(1234.5, Currency.GHS))
        assertEquals("KSh 1,234.50", formatMoney(1234.5, Currency.KES))
        assertEquals("FC 1,234.50", formatMoney(1234.5, Currency.CDF))
    }

    @Test
    fun currenciesWithoutMinorUnitsRoundWithoutInventingCents() {
        assertEquals("F CFA 1,235", formatMoney(1234.5, Currency.XOF))
        assertEquals("FCFA 1,235", formatMoney(1234.5, Currency.XAF))
        assertEquals("FG 1,235", formatMoney(1234.5, Currency.GNF))
    }

    @Test
    fun roundsBeforeFormattingAndDoesNotLoseNegativeFractionSign() {
        assertEquals("GH₵2", formatMoney(1.999, Currency.GHS))
        assertEquals("-GH₵0.50", formatMoney(-0.5, Currency.GHS))
        assertEquals("-F CFA 500", formatMoney(-500.1, Currency.XOF))
        assertEquals("₦0", formatMoney(-0.001, Currency.NGN))
    }

    @Test
    fun numericOnlyCompatibilityPreservesNigerianFormatting() {
        assertEquals("5,000", formatMoney(5000.0, Currency.NGN, includeSymbol = false))
        assertEquals("5,000.50", formatPrice(5000.5))
    }
}
