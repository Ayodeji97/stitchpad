package com.danzucker.stitchpad.core.sharing

private const val NIGERIAN_TRUNK_PREFIX = "0"
private const val NIGERIAN_COUNTRY_CODE = "234"
private const val EXPECTED_NIGERIAN_E164_LENGTH = 13
private const val NIGERIAN_SUBSCRIBER_LENGTH = 10

/**
 * Strips formatting and resolves Nigerian local numbers to E.164-style digits.
 *  - "0803 123 4567"   → "2348031234567"
 *  - "+234 803 1234567" → "2348031234567"
 *  - "234-803-123-4567" → "2348031234567"
 *
 * Non-Nigerian numbers (already including a non-234 country code) are returned digits-only;
 * the caller should ensure customer phones are stored with country context.
 *
 * For UIs that *imply* a Nigerian country code via a +234 chip (e.g. workshop WhatsApp field),
 * prefix the user's bare subscriber input with the country code at the call site before
 * calling this — see [applyImpliedNigerianCountryCode].
 */
fun normaliseNigerianPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith(NIGERIAN_COUNTRY_CODE) -> digits
        digits.startsWith(NIGERIAN_TRUNK_PREFIX) -> NIGERIAN_COUNTRY_CODE + digits.drop(1)
        else -> digits
    }
}

/**
 * Use only at call sites where the UI explicitly displays a Nigerian (+234) country-code chip
 * outside the input, so a bare 10-digit input is unambiguously the local subscriber portion.
 *  - "8031234567" → "2348031234567"
 *  - "0803 1234567" / "+234 803 1234567" → unchanged here, normaliseNigerianPhone handles them.
 *
 * Do NOT use on shared phone fields (e.g. customer contact) where the country is unknown —
 * a bare 10-digit US number would be silently rewritten as Nigerian.
 */
fun applyImpliedNigerianCountryCode(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length == NIGERIAN_SUBSCRIBER_LENGTH &&
        !digits.startsWith(NIGERIAN_COUNTRY_CODE) &&
        !digits.startsWith(NIGERIAN_TRUNK_PREFIX)
    ) {
        NIGERIAN_COUNTRY_CODE + raw
    } else {
        raw
    }
}

/**
 * Builds the wa.me URL that works on iOS, Android, and the web — falling back to a
 * browser-based WhatsApp page when the app isn't installed.
 */
fun buildWhatsAppUrl(phone: String, message: String): String {
    val normalised = normaliseNigerianPhone(phone)
    val encoded = urlEncode(message)
    return "https://wa.me/$normalised?text=$encoded"
}

/**
 * Returns true iff [raw] normalises to a Nigerian mobile number in E.164 form
 * (13 digits total: country code 234 + 10-digit subscriber number).
 */
fun validateNigerianMobileE164(raw: String): Boolean {
    val normalised = normaliseNigerianPhone(raw)
    return normalised.length == EXPECTED_NIGERIAN_E164_LENGTH && normalised.startsWith(NIGERIAN_COUNTRY_CODE)
}

// RFC 3986 unreserved is ASCII-only: A-Z a-z 0-9 - _ . ~. Anything else gets
// percent-encoded as UTF-8 bytes. Encoding the whole string to UTF-8 once and
// iterating bytes (rather than Kotlin Chars) correctly handles surrogate pairs
// — emoji and other non-BMP characters would otherwise split into unpaired
// surrogates whose per-Char encodeToByteArray() yields invalid UTF-8.
private fun isAsciiUnreservedByte(b: Int): Boolean =
    (b in 'A'.code..'Z'.code) || (b in 'a'.code..'z'.code) || (b in '0'.code..'9'.code) ||
        b == '-'.code || b == '_'.code || b == '.'.code || b == '~'.code

private fun urlEncode(text: String): String {
    val bytes = text.encodeToByteArray()
    val builder = StringBuilder(bytes.size)
    for (byte in bytes) {
        val v = byte.toInt() and 0xFF
        when {
            isAsciiUnreservedByte(v) -> builder.append(v.toChar())
            v == ' '.code -> builder.append("%20")
            else -> {
                builder.append('%')
                builder.append(v.toString(HEX_RADIX).padStart(2, '0').uppercase())
            }
        }
    }
    return builder.toString()
}

private const val HEX_RADIX = 16
