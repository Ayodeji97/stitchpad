package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * Outlined text field for Naira amounts. Input is digits-only (caller receives
 * only the filtered digit string); display inserts thousands separators on the fly.
 */
@Composable
fun NairaAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }) },
        visualTransformation = ThousandsSeparatorTransformation,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        leadingIcon = leadingIcon,
        isError = isError,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier,
    )
}

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
