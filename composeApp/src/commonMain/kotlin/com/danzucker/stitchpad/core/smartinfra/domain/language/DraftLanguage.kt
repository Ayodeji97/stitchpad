package com.danzucker.stitchpad.core.smartinfra.domain.language

/**
 * Output language for a Smart Suggestions generation. Pidgin = Nigerian
 * Pidgin (ISO 639-3 `pcm`).
 *
 * Shared across all Smart features (Draft Message, Post Caption,
 * Referral Message, Referral Bio). Wire names match the server's
 * `Language` type in functions/src/smart/types.ts.
 */
enum class DraftLanguage(val wireName: String) {
    English("en"),
    Pidgin("pcm"),
}
