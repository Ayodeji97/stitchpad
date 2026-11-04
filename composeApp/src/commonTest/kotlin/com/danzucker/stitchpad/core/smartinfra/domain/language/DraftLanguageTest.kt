package com.danzucker.stitchpad.core.smartinfra.domain.language

import kotlin.test.Test
import kotlin.test.assertEquals

class DraftLanguageTest {
    @Test
    fun frenchRegionalLocalesUseFrench() {
        listOf("fr", "fr-CI", "fr_SN", "FR-ca").forEach {
            assertEquals(DraftLanguage.French, DraftLanguage.fromLocale(it))
        }
    }

    @Test
    fun unsupportedDeviceLanguagesFallBackToEnglish() {
        listOf("", "en-NG", "pcm", "es", "af").forEach {
            assertEquals(DraftLanguage.English, DraftLanguage.fromLocale(it))
        }
    }
}
