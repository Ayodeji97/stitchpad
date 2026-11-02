package com.danzucker.stitchpad.ui.components

import androidx.compose.ui.text.AnnotatedString
import com.danzucker.stitchpad.core.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyAmountInputTest {
    @Test
    fun pasteKeepsCurrencyPrecisionAndRejectsUnsupportedSymbols() {
        assertEquals("1234.56", sanitizeMoneyInput("GH₵1,234.567", Currency.GHS))
        assertEquals("1234", sanitizeMoneyInput("F CFA 1,234.56", Currency.XOF))
        assertEquals("1234.", sanitizeMoneyInput("1234.", Currency.NGN))
    }

    @Test
    fun groupingDoesNotMoveFractionDigitsIntoThousands() {
        val transformed = MoneyGroupingTransformation.filter(AnnotatedString("12345.67"))
        assertEquals("12,345.67", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(5, transformed.offsetMapping.transformedToOriginal(6))
        assertEquals(8, transformed.offsetMapping.transformedToOriginal(9))
        assertEquals(9, transformed.offsetMapping.originalToTransformed(8))
    }
}
