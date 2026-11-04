@file:Suppress("ReturnCount") // Explicit guards reject ambiguous monetary input before any value conversion.

package com.danzucker.stitchpad.core.presentation

import com.danzucker.stitchpad.core.domain.model.Currency
import kotlin.math.floor
import kotlin.math.round

// Exact integer limit of the Double model used by persisted amounts, measured in minor units.
private const val MAX_EXACT_MINOR_UNITS = 9_007_199_254_740_991L
private const val DOUBLE_RELATIVE_EPSILON = 2.220446049250313e-16
private const val MAX_NOISE_IN_MINOR_UNITS = 0.000001

/**
 * Accept an edit only when its major-unit value is unambiguous and supported by [currency].
 * A valid thousands-grouped paste is equivalent to the ungrouped number; malformed grouping,
 * signs, currency labels, exponent syntax and extra precision keep [previous] unchanged.
 * Preserve partial trailing decimals and leading zeroes so the IME can edit naturally.
 */
fun moneyInputAfterEdit(raw: String, previous: String, currency: Currency): String {
    val trimmed = moneyInputDecimalSeparator(raw.trim(), currency)
    val normalized = if (',' in trimmed) {
        // Single-comma three-digit suffixes are ambiguous decimal/grouped input.
        val groups = trimmed.count { it == ',' }
        val unambiguousGroups = '.' in trimmed || groups > 1
        if (!unambiguousGroups || !Regex("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]*)?").matches(trimmed)) {
            return previous
        }
        trimmed.replace(",", "")
    } else {
        trimmed
    }
    if (normalized.isEmpty()) return ""
    if (normalized == "." && currency.decimals > 0) return normalized
    return if (moneyInputValue(normalized, currency) != null) normalized else previous
}

/** Equivalent, same-length decimal separator change, safe for retaining IME selection offsets. */
fun moneyInputDecimalSeparator(raw: String, currency: Currency): String {
    if (currency.decimals == 0 || '.' in raw || raw.count { it == ',' } != 1) return raw
    val fraction = raw.substringAfter(',')
    val whole = raw.substringBefore(',')
    return if (whole.all { it in '0'..'9' } && fraction.all { it in '0'..'9' } &&
        fraction.length <= currency.decimals
    ) {
        raw.replace(',', '.')
    } else {
        raw
    }
}

/** Parse canonical major units without stripping signs/separators or rounding precision away. */
fun moneyInputValue(input: String, currency: Currency): Double? {
    val units = moneyInputMinorUnits(input, currency) ?: return null
    val amount = units.toDouble() / moneyScale(currency)
    // Necessary near Double's exact-integer limit: distinct decimal texts must not
    // collapse to a value that formats/prefills as a different monetary amount.
    if (moneyInputMinorUnits(moneyInputForAmount(amount), currency) != units ||
        round(amount * moneyScale(currency)).toLong() != units
    ) {
        return null
    }
    return amount
}

private fun moneyInputMinorUnits(input: String, currency: Currency): Long? {
    val pattern = if (currency.decimals == 0) "[0-9]+" else "[0-9]*\\.[0-9]{0,${currency.decimals}}|[0-9]+"
    if (!Regex(pattern).matches(input) || input.none { it in '0'..'9' }) return null
    val whole = input.substringBefore('.').ifEmpty { "0" }
    val fraction = input.substringAfter('.', "").padEnd(currency.decimals, '0')
    val units = (whole + fraction).trimStart('0').ifEmpty { "0" }.toLongOrNull() ?: return null
    return units.takeIf { it <= MAX_EXACT_MINOR_UNITS }
}

/**
 * Normalize only binary arithmetic noise, never a real unsupported fraction.
 * The exact canonical roundtrip handles larger valid amounts without a fixed epsilon;
 * the fallback tolerance follows magnitude and remains far below a meaningful minor unit.
 */
fun moneyAmountAtPrecision(amount: Double, currency: Currency): Double? {
    if (!amount.isFinite() || amount < 0.0) return null
    val scale = moneyScale(currency)
    val normalized = round(amount * scale) / scale
    val magnitudeTolerance = maxOf(kotlin.math.abs(amount), kotlin.math.abs(normalized)) *
        DOUBLE_RELATIVE_EPSILON * 2.0
    val tolerance = magnitudeTolerance.coerceAtMost(MAX_NOISE_IN_MINOR_UNITS / scale)
    return if (amount == normalized || kotlin.math.abs(amount - normalized) <= tolerance) {
        moneyInputValue(moneyInputForAmount(normalized), currency)
    } else {
        null
    }
}

/** Prefill an existing amount without integer truncation, currency relabeling or hidden rounding. */
fun moneyInputForAmount(amount: Double): String {
    if (amount == 0.0) return "0"
    val text = amount.toString()
    if (!amount.isFinite()) return text
    val parts = text.lowercase().split('e')
    val plain = if (parts.size == 1) {
        text
    } else {
        val negative = parts[0].startsWith('-')
        val mantissa = parts[0].removePrefix("-")
        val digits = mantissa.replace(".", "")
        val point = mantissa.substringBefore('.').length + parts[1].toInt()
        val unsigned = when {
            point <= 0 -> "0." + "0".repeat(-point) + digits
            point >= digits.length -> digits + "0".repeat(point - digits.length)
            else -> digits.take(point) + "." + digits.drop(point)
        }
        (if (negative) "-" else "") + unsigned
    }
    return if ('.' in plain) plain.trimEnd('0').trimEnd('.') else plain
}

/** Cap in currency minor units; never turns a decimal amount into concatenated integer digits. */
fun capMoneyInput(input: String, maximum: Double, currency: Currency): String {
    val amount = moneyInputValue(input, currency) ?: return input
    if (!maximum.isFinite() || maximum <= 0.0 || amount <= maximum) return input
    val scaled = maximum * moneyScale(currency)
    val canonicalMaximum = moneyAmountAtPrecision(maximum, currency)
    // Correct binary arithmetic noise only; real unsupported fractions round DOWN at the cap.
    val units = if (canonicalMaximum != null) round(canonicalMaximum * moneyScale(currency)) else floor(scaled)
    return moneyInputForAmount(units / moneyScale(currency))
}

private fun moneyScale(currency: Currency): Double = if (currency.decimals == 0) 1.0 else 100.0
