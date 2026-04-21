package com.danzucker.stitchpad.core.sharing

import kotlin.math.abs
import kotlin.math.roundToLong

fun formatPrice(price: Double): String {
    // Round to whole kobo first, then format. Prevents truncation bugs (1.999 must
    // become "2.00", not "1.99") and avoids Double.toString's scientific notation.
    val totalKobo = (price * 100.0).roundToLong()
    val absoluteKobo = abs(totalKobo)
    val wholeNaira = absoluteKobo / 100L
    val kobo = (absoluteKobo % 100L).toInt()
    val sign = if (totalKobo < 0L) "-" else ""
    val nairaFormatted = addThousandsSeparator(wholeNaira.toString())
    return if (kobo == 0) {
        sign + nairaFormatted
    } else {
        sign + nairaFormatted + "." + kobo.toString().padStart(2, '0')
    }
}

private fun addThousandsSeparator(digits: String): String = buildString {
    digits.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) append(',')
        append(c)
    }
}.reversed()
