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
    fun compactMoneyKeepsMeaningfulFractionalThousandsForZeroDecimalCurrencies() {
        assertEquals("F CFA 1.2K", formatCompactMoney(1200.0, Currency.XOF))
        assertEquals("GH₵1.5M", formatCompactMoney(1500000.0, Currency.GHS))
        assertEquals("-KSh 1.2K", formatCompactMoney(-1200.0, Currency.KES))
    }

    @Test
    fun numericOnlyCompatibilityPreservesNigerianFormatting() {
        assertEquals("5,000", formatMoney(5000.0, Currency.NGN, includeSymbol = false))
        assertEquals("5,000.50", formatPrice(5000.5))
    }

    // --- Per-language display conventions (device / app language) ---

    private val nnbsp = '\u202F'
    private val nbsp = '\u00A0'

    @Test
    fun englishStyleIsTheHistoricalFormat() {
        val en = MoneyStyle.ENGLISH
        assertEquals("₦100,000", formatMoney(100_000.0, Currency.NGN, style = en))
        assertEquals("₦1,234.50", formatMoney(1234.5, Currency.NGN, style = en))
        assertEquals("₦310K", formatCompactMoney(310_000.0, Currency.NGN, style = en))
        assertEquals("GH₵1.5M", formatCompactMoney(1_500_000.0, Currency.GHS, style = en))
        assertEquals("5,000.50", formatPrice(5000.5, style = en))
    }

    @Test
    fun frenchStyleUsesNarrowSpaceGroupingAndDecimalComma() {
        val fr = MoneyStyle.FRENCH
        assertEquals("₦100${nnbsp}000", formatMoney(100_000.0, Currency.NGN, style = fr))
        assertEquals("₦1${nnbsp}234,50", formatMoney(1234.5, Currency.NGN, style = fr))
        assertEquals("F CFA 1${nnbsp}235", formatMoney(1234.5, Currency.XOF, style = fr))
        assertEquals("-GH₵0,50", formatMoney(-0.5, Currency.GHS, style = fr))
        assertEquals("₦1${nnbsp}000${nnbsp}000", formatMoney(1_000_000.0, Currency.NGN, style = fr))
        assertEquals("5${nnbsp}000,50", formatPrice(5000.5, style = fr))
    }

    @Test
    fun frenchCompactUsesLowercaseKAndDecimalComma() {
        val fr = MoneyStyle.FRENCH
        assertEquals("₦310${nbsp}k", formatCompactMoney(310_000.0, Currency.NGN, style = fr))
        assertEquals("F CFA 1,2${nbsp}k", formatCompactMoney(1200.0, Currency.XOF, style = fr))
        assertEquals("GH₵1,5${nbsp}M", formatCompactMoney(1_500_000.0, Currency.GHS, style = fr))
        assertEquals("-KSh 1,2${nbsp}k", formatCompactMoney(-1200.0, Currency.KES, style = fr))
        assertEquals("₦999", formatCompactMoney(999.0, Currency.NGN, style = fr))
    }

    @Test
    fun styleFollowsLanguageCode() {
        assertEquals(MoneyStyle.FRENCH, MoneyStyle.forLanguage("fr"))
        assertEquals(MoneyStyle.FRENCH, MoneyStyle.forLanguage("fr-CI"))
        assertEquals(MoneyStyle.ENGLISH, MoneyStyle.forLanguage("en"))
        assertEquals(MoneyStyle.ENGLISH, MoneyStyle.forLanguage(""))
    }
}
