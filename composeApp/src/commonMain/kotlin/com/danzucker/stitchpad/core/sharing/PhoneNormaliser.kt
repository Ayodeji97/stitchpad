package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.Country

private const val INTERNATIONAL_DIAL_PREFIX = "00"

private fun String.digitsOnly(): String = filter { it.isDigit() }

/** True when [raw] spells its own country code: a leading `+`, or `00` plus a registry dial code. */
private fun hasExplicitCountryCode(raw: String): Boolean {
    val trimmed = raw.trim()
    val afterPrefix = trimmed.digitsOnly().drop(INTERNATIONAL_DIAL_PREFIX.length)
    return trimmed.startsWith("+") ||
        (trimmed.startsWith(INTERNATIONAL_DIAL_PREFIX) && Country.entries.any { afterPrefix.startsWith(it.dialCode) })
}

/**
 * Strips formatting and resolves a phone number to digits-only with its country code, using
 * [country] to interpret local numbers. Rules, in order:
 *  - "+234 803 123 4567" / "00234 803 123 4567" → "2348031234567" (country code already explicit,
 *    any country, so a foreign customer keeps its own code)
 *  - "234-803-123-4567" (starts with [Country.dialCode], full length) → "2348031234567"
 *  - "0803 123 4567" (starts with [Country.trunkPrefix], national length + 1) → "2348031234567"
 *  - anything else is returned digits-only; a country is never guessed for bare numbers.
 *
 * For UIs that *imply* the country via a dial-code chip (e.g. workshop WhatsApp field),
 * prefix the user's bare national input at the call site before calling this — see
 * [applyImpliedCountryCode].
 */
fun normalisePhone(raw: String, country: Country): String {
    val digits = raw.digitsOnly()
    if (hasExplicitCountryCode(raw)) {
        return if (raw.trim().startsWith("+")) digits else digits.drop(INTERNATIONAL_DIAL_PREFIX.length)
    }
    val trunk = country.trunkPrefix
    return when {
        digits.startsWith(country.dialCode) &&
            digits.length == country.dialCode.length + country.nationalLength -> digits
        trunk != null && digits.startsWith(trunk) &&
            digits.length == country.nationalLength + 1 -> country.dialCode + digits.drop(trunk.length)
        else -> digits
    }
}

/**
 * Use only at call sites where the UI explicitly displays the country's dial-code chip outside
 * the input, so a bare national-length input is unambiguously the local portion.
 *  - NG "8031234567" → "2348031234567"
 *  - GA "06 12 34 56" → "24106123456" after normalising (the leading 0 is part of the Gabonese number)
 *  - "0803 1234567" / "+234 803 1234567" → unchanged here, [normalisePhone] handles them.
 *
 * Do NOT use on shared phone fields (e.g. customer contact) where the country is unknown —
 * a bare number could belong to another country and would be silently rewritten.
 */
fun applyImpliedCountryCode(raw: String, country: Country): String {
    if (hasExplicitCountryCode(raw)) return raw
    val digits = raw.digitsOnly()
    val isFullNumber = digits.startsWith(country.dialCode) &&
        digits.length == country.dialCode.length + country.nationalLength
    return if (digits.length == country.nationalLength && !isFullNumber) {
        country.dialCode + digits
    } else {
        raw
    }
}

/**
 * Builds the wa.me URL that works on iOS, Android, and the web — falling back to a
 * browser-based WhatsApp page when the app isn't installed. [country] decides how local
 * (trunk-prefixed) numbers are resolved.
 */
fun buildWhatsAppUrl(phone: String, message: String, country: Country): String {
    val encoded = urlEncode(message)
    val normalised = normalisePhone(phone, country)
    // No usable number — open WhatsApp's own recipient picker with the text
    // prefilled (wa.me without digits) instead of a dead link.
    if (normalised.isBlank()) return "https://wa.me/?text=$encoded"
    return "https://wa.me/$normalised?text=$encoded"
}

/**
 * Returns true iff [raw] (with the implied country code applied) normalises to [country]'s
 * dial code followed by a national number matching [Country.mobilePattern]. The pattern is
 * deliberately not an assignment list — the "Confirm on WhatsApp" round-trip is the backstop
 * for well-formed fakes, since they will not be reachable on WhatsApp.
 */
fun isValidMobile(raw: String, country: Country): Boolean {
    val normalised = normalisePhone(applyImpliedCountryCode(raw, country), country)
    return normalised.startsWith(country.dialCode) &&
        country.mobilePattern.matches(normalised.drop(country.dialCode.length))
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
