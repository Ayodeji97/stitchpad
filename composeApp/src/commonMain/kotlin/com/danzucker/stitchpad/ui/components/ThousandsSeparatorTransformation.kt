package com.danzucker.stitchpad.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Integer grouping retained for existing non-money callers until their own migration. */
object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formatted = buildString {
            original.reversed().forEachIndexed { i, c ->
                if (i > 0 && i % 3 == 0) append(',')
                append(c)
            }
        }.reversed()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                val totalLength = original.length
                var commasBeforePos = 0
                for (i in 0 until offset) {
                    val distFromRight = totalLength - 1 - i
                    if (distFromRight > 0 && distFromRight % 3 == 0) commasBeforePos++
                }
                return offset + commasBeforePos
            }

            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = 0
                var transformedOffset = 0
                for (i in formatted.indices) {
                    if (transformedOffset >= offset) break
                    transformedOffset++
                    if (formatted[i] != ',') originalOffset++
                }
                return originalOffset.coerceAtMost(original.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
