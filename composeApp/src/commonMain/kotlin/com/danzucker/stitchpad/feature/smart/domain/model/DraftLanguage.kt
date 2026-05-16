package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Output language for a draft. Pidgin = Nigerian Pidgin (ISO 639-3 `pcm`).
 */
enum class DraftLanguage(val wireName: String) {
    English("en"),
    Pidgin("pcm"),
}
