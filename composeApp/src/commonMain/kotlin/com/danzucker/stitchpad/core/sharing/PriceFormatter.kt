package com.danzucker.stitchpad.core.sharing

fun formatPrice(price: Double): String {
    val long = price.toLong()
    if (price == long.toDouble()) return addThousandsSeparator(long.toString())
    val parts = price.toString().split(".")
    val decimal = (parts.getOrElse(1) { "00" } + "00").take(2)
    return addThousandsSeparator(parts[0]) + "." + decimal
}

private fun addThousandsSeparator(intPart: String): String {
    val negative = intPart.startsWith("-")
    val digits = if (negative) intPart.drop(1) else intPart
    val result = buildString {
        digits.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) append(',')
            append(c)
        }
    }.reversed()
    return if (negative) "-$result" else result
}
