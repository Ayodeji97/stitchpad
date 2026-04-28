package com.danzucker.stitchpad.core.sharing

private const val NIGERIAN_TRUNK_PREFIX = "0"
private const val NIGERIAN_COUNTRY_CODE = "234"

/**
 * Strips formatting and resolves Nigerian local numbers to E.164-style digits.
 *  - "0803 123 4567"   → "2348031234567"
 *  - "+234 803 1234567" → "2348031234567"
 *  - "234-803-123-4567" → "2348031234567"
 *
 * Non-Nigerian numbers (already including a non-234 country code) are returned digits-only;
 * the caller should ensure customer phones are stored with country context.
 */
internal fun normaliseNigerianPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith(NIGERIAN_COUNTRY_CODE) -> digits
        digits.startsWith(NIGERIAN_TRUNK_PREFIX) -> NIGERIAN_COUNTRY_CODE + digits.drop(1)
        else -> digits
    }
}

/**
 * Builds the wa.me URL that works on iOS, Android, and the web — falling back to a
 * browser-based WhatsApp page when the app isn't installed.
 */
internal fun buildWhatsAppUrl(phone: String, message: String): String {
    val normalised = normaliseNigerianPhone(phone)
    val encoded = urlEncode(message)
    return "https://wa.me/$normalised?text=$encoded"
}

// RFC 3986 unreserved set is ASCII-only. Char.isLetterOrDigit() is Unicode-aware
// and would let Yoruba/diacritic name characters (Adérónké, Olúwatóyìn, etc.)
// pass through unencoded — wa.me rejects those, so we restrict to ASCII here
// and percent-encode every non-ASCII byte.
private fun Char.isAsciiUnreserved(): Boolean =
    (this in 'A'..'Z') || (this in 'a'..'z') || (this in '0'..'9') ||
        this == '-' || this == '_' || this == '.' || this == '~'

private fun urlEncode(text: String): String {
    val builder = StringBuilder(text.length)
    for (ch in text) {
        when {
            ch.isAsciiUnreserved() -> builder.append(ch)
            ch == ' ' -> builder.append("%20")
            else -> {
                val bytes = ch.toString().encodeToByteArray()
                for (b in bytes) {
                    builder.append('%')
                    val v = b.toInt() and 0xFF
                    builder.append(v.toString(HEX_RADIX).padStart(2, '0').uppercase())
                }
            }
        }
    }
    return builder.toString()
}

private const val HEX_RADIX = 16
