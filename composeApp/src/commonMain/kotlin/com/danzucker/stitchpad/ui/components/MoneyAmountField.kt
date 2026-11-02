package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * Major-unit money input. The owning screen supplies the record's currency; changing
 * a device region must never change an existing order's currency or reinterpret its amount.
 */
@Composable
fun MoneyAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: Currency,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val input = rememberSanitizedTextFieldValue(value) { raw ->
        onValueChange(sanitizeMoneyInput(raw, currency))
    }
    OutlinedTextField(
        value = input.value,
        onValueChange = input.onValueChange,
        visualTransformation = MoneyGroupingTransformation,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        prefix = { Text(currency.symbol) },
        isError = isError,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (currency.decimals == 0) KeyboardType.Number else KeyboardType.Decimal,
        ),
        singleLine = true,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = modifier,
    )
}

/** Keeps a dot decimal while permitting formatted amounts pasted from receipts. */
internal fun sanitizeMoneyInput(raw: String, currency: Currency): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val whole = filtered.substringBefore('.')
    if (!filtered.contains('.') || currency.decimals == 0) return whole
    val fraction = filtered.substringAfter('.').filter { it.isDigit() }.take(currency.decimals)
    return "$whole.$fraction"
}

/** Groups only the integer part, preserving a partially typed decimal and its caret. */
internal object MoneyGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val integerLength = original.indexOf('.').takeIf { it >= 0 } ?: original.length
        val formatted = buildString {
            original.forEachIndexed { i, c ->
                if (i > 0 && i < integerLength && (integerLength - i) % 3 == 0) append(',')
                append(c)
            }
        }
        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val commasBefore = (1 until integerLength).count { i ->
                        i < offset && (integerLength - i) % 3 == 0
                    }
                    return offset + commasBefore
                }

                override fun transformedToOriginal(offset: Int): Int =
                    formatted.take(offset).count { it != ',' }.coerceAtMost(original.length)
            },
        )
    }
}
