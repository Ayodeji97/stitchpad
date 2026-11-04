package com.danzucker.stitchpad.ui.components

import androidx.compose.ui.text.AnnotatedString
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.presentation.moneyInputAfterEdit
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyAmountInputTest {
    @Test
    fun unsupportedPrecisionAndSymbolsDoNotChangeThePreviousAmount() {
        assertEquals("12.34", moneyInputAfterEdit("GH₵1,234.567", "12.34", Currency.GHS))
        assertEquals("1234", moneyInputAfterEdit("1234.56", "1234", Currency.XOF))
        assertEquals("1234.", moneyInputAfterEdit("1234.", "1234", Currency.NGN))
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
