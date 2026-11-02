package com.danzucker.stitchpad.core.presentation

import com.danzucker.stitchpad.core.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyInputTest {
    @Test
    fun decimalTypingAndPastingKeepMajorUnitsInsteadOfConcatenatingDigits() {
        assertEquals("12.", moneyInputAfterEdit("12.", "12", Currency.GHS))
        assertEquals("12.34", moneyInputAfterEdit("12,34", "12", Currency.GHS))
        assertEquals("1234.56", moneyInputAfterEdit("1,234.56", "", Currency.NGN))
        assertEquals("1234567", moneyInputAfterEdit("1,234,567", "", Currency.NGN))
        assertEquals(12.34, moneyInputValue("12.34", Currency.GHS))
        assertEquals(0.05, moneyInputValue(".05", Currency.NGN))
        assertNull(moneyInputValue(".", Currency.NGN))
    }

    @Test
    fun malformedOrAmbiguousPastesAndExtraPrecisionNeverBecomeDifferentAmounts() {
        listOf("-12", "+12", "1e3", "12..34", "12.345", "12,345", "12,3,4", "1.234,56", "NGN12")
            .forEach { raw -> assertEquals("12.34", moneyInputAfterEdit(raw, "12.34", Currency.GHS), raw) }
        assertEquals("12.34", moneyInputAfterEdit("9".repeat(400), "12.34", Currency.GHS))
        assertNull(moneyInputValue("-12", Currency.GHS))
        assertNull(moneyInputValue("12.345", Currency.GHS))
    }

    @Test
    fun zeroDecimalCurrenciesRejectBothKeyboardDecimalSeparators() {
        listOf(Currency.XOF, Currency.XAF, Currency.GNF).forEach { currency ->
            listOf("12.", "12.34", "12,34", "12.0").forEach { raw ->
                assertEquals("12", moneyInputAfterEdit(raw, "12", currency))
            }
            assertEquals("1234", moneyInputAfterEdit("1234", "12", currency))
            assertNull(moneyInputValue("12.34", currency))
        }
    }

    @Test
    fun capsPreserveFractionalBalancesAndDoNotExceedUnsupportedFractionalMaximum() {
        assertEquals("12.34", capMoneyInput("99.99", 12.34, Currency.GHS))
        assertEquals("12.34", capMoneyInput("12.34", 12.34, Currency.NGN))
        assertEquals("12.34", capMoneyInput("99.99", 12.345, Currency.GHS))
        assertEquals("12", capMoneyInput("99", 12.9, Currency.XOF))
        assertEquals("0.29", capMoneyInput("1", 0.30 - 0.01, Currency.NGN))
    }

    @Test
    fun prefillPreservesExistingFractionsAndExpandsScientificNotationWithoutRounding() {
        assertEquals("12.34", moneyInputForAmount(12.34))
        assertEquals("12.345", moneyInputForAmount(12.345))
        assertEquals("10000000", moneyInputForAmount(10_000_000.0))
        assertEquals("0.000001", moneyInputForAmount(0.000001))
        assertEquals("0", moneyInputForAmount(-0.0))
    }

    @Test
    fun equivalentKeyboardSeparatorChangeKeepsSelectionOffsetsStable() {
        assertEquals("12.34", moneyInputDecimalSeparator("12,34", Currency.GHS))
        assertEquals("12,34", moneyInputDecimalSeparator("12,34", Currency.XOF))
        assertEquals("1,234.56", moneyInputDecimalSeparator("1,234.56", Currency.GHS))
        assertEquals("12,345", moneyInputDecimalSeparator("12,345", Currency.GHS))
    }
}
