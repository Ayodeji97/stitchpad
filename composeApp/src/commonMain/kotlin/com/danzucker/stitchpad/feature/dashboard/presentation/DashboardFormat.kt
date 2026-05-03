package com.danzucker.stitchpad.feature.dashboard.presentation

import kotlin.math.absoluteValue
import kotlin.math.roundToLong

private const val THOUSANDS_GROUP_SIZE = 3

/**
 * Formats a naira amount with thousands separators, e.g. 480000.0 → "480,000".
 * Used on NBA cards and any surface where precision matters more than density.
 */
internal fun formatNaira(amount: Double): String {
    val rounded = amount.roundToLong()
    val negative = rounded < 0
    val digits = rounded.absoluteValue.toString()
    val groups = buildList {
        var index = digits.length
        while (index > THOUSANDS_GROUP_SIZE) {
            add(digits.substring(index - THOUSANDS_GROUP_SIZE, index))
            index -= THOUSANDS_GROUP_SIZE
        }
        add(digits.substring(0, index))
    }.reversed()
    val joined = groups.joinToString(",")
    return if (negative) "-$joined" else joined
}

/**
 * Returns the first whitespace-delimited token of a name with its first letter
 * upper-cased for display. "ade bello" → "Ade", "daniel" → "Daniel", "" → "".
 * Used for greetings, pre-filled customer messages, and any user-facing surface
 * where a tailor's signup-time casing ("ola") shouldn't leak into the UI as-is.
 */
internal fun firstNameOf(fullName: String): String {
    val first = fullName.trim().substringBefore(' ').trim()
    return first.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
}
