package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.Currency
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Formats a major-unit amount using the currency stamped on its record.
 *
 * All order/payment amounts are major units (500.50 means 500 and 50 minor units),
 * not kobo or cents. Zero-decimal currencies round to a whole major unit. Two-decimal
 * currencies omit an all-zero fraction, preserving the existing Nigerian display.
 * No conversion or device-locale currency inference takes place here; only the
 * digit grouping / decimal separator follow [style] (device language by default).
 */
fun formatMoney(
    amount: Double,
    currency: Currency,
    includeSymbol: Boolean = true,
    style: MoneyStyle = MoneyStyle.current(),
): String {
    val scale = if (currency.decimals == 0) 1L else 100L
    val minorUnits = (amount * scale).roundToLong()
    // Work from the signed decimal string instead of abs(Long): abs(Long.MIN_VALUE)
    // overflows and would produce malformed grouping for a saturated negative amount.
    val digits = minorUnits.toString().removePrefix("-").padStart(currency.decimals + 1, '0')
    val integerDigits = if (currency.decimals == 0) digits else digits.dropLast(currency.decimals)
    val fraction = if (currency.decimals == 0) "" else digits.takeLast(currency.decimals)
    val integer = addThousandsSeparator(integerDigits, style.groupSeparator)
    val number = if (fraction.isEmpty() || fraction.all { it == '0' }) {
        integer
    } else {
        "$integer${style.decimalSeparator}$fraction"
    }
    val sign = if (minorUnits < 0) "-" else ""
    val symbol = if (includeSymbol) {
        currency.symbol + if (currency.symbol.last().isLetter()) " " else ""
    } else {
        ""
    }
    return sign + symbol + number
}

/** Compact display keeps one significant decimal even for a zero-decimal currency (1.2K francs). */
fun formatCompactMoney(
    amount: Double,
    currency: Currency,
    style: MoneyStyle = MoneyStyle.current(),
): String {
    val divisor = when {
        abs(amount) >= 1_000_000.0 -> 1_000_000.0
        abs(amount) >= 1_000.0 -> 1_000.0
        else -> return formatMoney(amount, currency, style = style)
    }
    val rounded = (abs(amount) / divisor * 10.0).roundToInt() / 10.0
    val number = if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString().replace('.', style.decimalSeparator)
    }
    val prefix = formatMoney(0.0, currency, style = style).removeSuffix("0")
    val suffix = if (divisor == 1_000_000.0) style.millionSuffix else style.thousandSuffix
    return (if (amount < 0) "-" else "") + prefix + number + suffix
}

/** Compatibility for number-only Nigerian call sites pending their currency migration. */
fun formatPrice(price: Double, style: MoneyStyle = MoneyStyle.current()): String =
    formatMoney(price, Currency.NGN, includeSymbol = false, style = style)

private fun addThousandsSeparator(digits: String, separator: Char): String = buildString {
    digits.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) append(separator)
        append(c)
    }
}.reversed()
