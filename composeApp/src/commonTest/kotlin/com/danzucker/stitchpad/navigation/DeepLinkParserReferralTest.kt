package com.danzucker.stitchpad.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserReferralTest {

    // ── parseReferral ────────────────────────────────────────────────────────

    @Test
    fun universalLink_pathSegment_extractsCode() {
        assertEquals("ABCD1234", DeepLinkParser.parseReferral("https://link.getstitchpad.com/r/ABCD1234"))
    }

    @Test
    fun universalLink_pathSegment_lowercased_isNormalizedToUppercase() {
        assertEquals("ABCD1234", DeepLinkParser.parseReferral("https://link.getstitchpad.com/r/abcd1234"))
    }

    @Test
    fun universalLink_pathSegment_withTrailingQuery_extractsCode() {
        assertEquals(
            "XYZ99",
            DeepLinkParser.parseReferral("https://link.getstitchpad.com/r/XYZ99?utm_source=wa"),
        )
    }

    @Test
    fun customScheme_pathSegment_extractsCode() {
        assertEquals("ABCD1234", DeepLinkParser.parseReferral("stitchpad://r/ABCD1234"))
    }

    @Test
    fun refQueryParam_extractsCode() {
        assertEquals("CODE12", DeepLinkParser.parseReferral("https://link.getstitchpad.com/r?ref=CODE12"))
    }

    @Test
    fun codeQueryParam_extractsCode() {
        assertEquals("CODE12", DeepLinkParser.parseReferral("https://link.getstitchpad.com/r?code=code12"))
    }

    @Test
    fun bareBase_withoutCode_isNull() {
        assertNull(DeepLinkParser.parseReferral("https://link.getstitchpad.com/r"))
    }

    @Test
    fun unrelatedPath_isNotReferral() {
        assertNull(DeepLinkParser.parseReferral("https://link.getstitchpad.com/claim?code=ABC"))
    }

    @Test
    fun prefixLookalikePath_doesNotMatch() {
        // "/rooms" must not be treated as the "/r" route.
        assertNull(DeepLinkParser.parseReferral("https://link.getstitchpad.com/rooms/ABCD1234"))
    }

    @Test
    fun nullUrl_isNull() {
        assertNull(DeepLinkParser.parseReferral(null))
    }

    @Test
    fun codeWithIllegalChars_isRejected() {
        // '/' and '.' are path-injection risks and outside the code charset.
        assertNull(DeepLinkParser.parseReferral("https://link.getstitchpad.com/r?ref=bad.code"))
    }

    // ── parseInstallReferrerCode ─────────────────────────────────────────────

    @Test
    fun installReferrer_refOnly_extractsCode() {
        assertEquals("ABCD1234", DeepLinkParser.parseInstallReferrerCode("ref=ABCD1234"))
    }

    @Test
    fun installReferrer_withUtmParams_extractsRef() {
        assertEquals(
            "ABCD1234",
            DeepLinkParser.parseInstallReferrerCode("utm_source=whatsapp&ref=abcd1234"),
        )
    }

    @Test
    fun installReferrer_organicNoRef_isNull() {
        assertNull(DeepLinkParser.parseInstallReferrerCode("utm_source=google-play&utm_medium=organic"))
    }

    @Test
    fun installReferrer_empty_isNull() {
        assertNull(DeepLinkParser.parseInstallReferrerCode(""))
        assertNull(DeepLinkParser.parseInstallReferrerCode(null))
    }

    // ── normalizeReferralCode ────────────────────────────────────────────────

    @Test
    fun normalize_stripsSpacesAndHyphens_uppercases() {
        assertEquals("ABCD1234", DeepLinkParser.normalizeReferralCode(" abcd-1234 "))
    }

    @Test
    fun normalize_rejectsSymbols() {
        assertNull(DeepLinkParser.normalizeReferralCode("ABC/123"))
    }

    @Test
    fun normalize_rejectsEmptyAfterStripping() {
        assertNull(DeepLinkParser.normalizeReferralCode("   "))
    }
}
