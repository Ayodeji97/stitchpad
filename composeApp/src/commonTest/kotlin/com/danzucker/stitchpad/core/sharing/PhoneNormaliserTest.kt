package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneNormaliserTest {

    // --- normalisePhone ---

    @Test
    fun `normalisePhone strips trunk prefix`() {
        assertEquals("2348031234567", normalisePhone("0803 123 4567", Country.NG))
    }

    @Test
    fun `normalisePhone keeps E164 form`() {
        assertEquals("2348031234567", normalisePhone("+234 803 123 4567", Country.NG))
    }

    @Test
    fun normalisePhone_keepsExistingCountryCode_andStripsFormatting() {
        assertEquals("2348031234567", normalisePhone("+234 803 123 4567", Country.NG))
        assertEquals("2348031234567", normalisePhone("234-803-123-4567", Country.NG))
    }

    @Test
    fun `normalisePhone leaves bare 10-digit input alone`() {
        // Customer flows reuse normalisePhone via buildWhatsAppUrl, where a
        // bare 10-digit input might be a non-Nigerian customer (e.g. US 2125550123).
        // The country-code prefix must NOT be auto-applied here.
        assertEquals("2125550123", normalisePhone("2125550123", Country.NG))
        assertFalse(isValidMobile("2125550123", Country.NG))
    }

    @Test
    fun `applyImpliedCountryCode prefixes bare 10-digit subscriber`() {
        // Used at workshop call site where the UI shows a +234 chip outside the input.
        assertEquals("2348064816695", normalisePhone(applyImpliedCountryCode("8064816695", Country.NG), Country.NG))
        assertTrue(isValidMobile(applyImpliedCountryCode("8064816695", Country.NG), Country.NG))
    }

    @Test
    fun `applyImpliedCountryCode leaves trunk-prefixed input alone`() {
        assertEquals("08031234567", applyImpliedCountryCode("08031234567", Country.NG))
    }

    @Test
    fun `applyImpliedCountryCode leaves already-prefixed input alone`() {
        assertEquals("+2348031234567", applyImpliedCountryCode("+2348031234567", Country.NG))
        assertEquals("2348031234567", applyImpliedCountryCode("2348031234567", Country.NG))
    }

    // --- buildWhatsAppUrl ---

    @Test
    fun buildWhatsAppUrl_passesAsciiUnreservedThrough() {
        val url = buildWhatsAppUrl(
            phone = "+2348031234567",
            message = "Hello-world_OK.tilde~99",
            country = Country.NG,
        )
        assertEquals("https://wa.me/2348031234567?text=Hello-world_OK.tilde~99", url)
    }

    @Test
    fun buildWhatsAppUrl_encodesSpacesAsPercent20() {
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "Hi Ade", country = Country.NG)
        assertTrue(url.endsWith("?text=Hi%20Ade"), "got=$url")
    }

    @Test
    fun buildWhatsAppUrl_percentEncodesYorubaDiacritics() {
        // 'é' = 0xC3 0xA9 in UTF-8 — must come out as %C3%A9, not raw, otherwise
        // wa.me rejects the URL. Char.isLetterOrDigit() (Unicode-aware) used to
        // let this through unencoded.
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "Adé", country = Country.NG)
        assertTrue(url.endsWith("?text=Ad%C3%A9"), "got=$url")
    }

    @Test
    fun buildWhatsAppUrl_percentEncodesNairaSign() {
        // '₦' = 0xE2 0x82 0xA6 in UTF-8.
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "₦100", country = Country.NG)
        assertTrue(url.endsWith("?text=%E2%82%A6100"), "got=$url")
    }

    @Test
    fun buildWhatsAppUrl_percentEncodesSurrogatePairEmoji() {
        // '💍' = U+1F48D, encoded as UTF-8 bytes 0xF0 0x9F 0x92 0x8D.
        // In a Kotlin String it's a surrogate pair (D83D DC8D). Iterating by
        // Char would split them and corrupt the UTF-8 encoding; iterating the
        // whole-string UTF-8 byte array handles it correctly.
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "💍", country = Country.NG)
        assertTrue(url.endsWith("?text=%F0%9F%92%8D"), "got=$url")
    }

    @Test
    fun buildWhatsAppUrl_blankPhoneOpensWhatsAppsOwnPicker() {
        // No number on file — omit the digits entirely so WhatsApp opens its own
        // recipient picker with the text prefilled, instead of a dead wa.me link.
        val url = buildWhatsAppUrl(phone = "", message = "Hi Ade", country = Country.NG)
        assertEquals("https://wa.me/?text=Hi%20Ade", url)
    }

    // --- isValidMobile ---

    @Test
    fun `isValidMobile accepts 13 digits starting with 234`() {
        assertTrue(isValidMobile("2348031234567", Country.NG))
    }

    @Test
    fun `isValidMobile accepts trunk-prefixed input`() {
        assertTrue(isValidMobile("08031234567", Country.NG))
    }

    @Test
    fun `isValidMobile rejects too short`() {
        assertFalse(isValidMobile("0803123", Country.NG))
    }

    @Test
    fun `isValidMobile rejects too long`() {
        assertFalse(isValidMobile("234803123456789", Country.NG))
    }

    @Test
    fun `isValidMobile rejects empty`() {
        assertFalse(isValidMobile("", Country.NG))
    }

    @Test
    fun isValidMobile_acceptsRealMobilePrefixes() {
        listOf(
            "08031234567",   // MTN 80
            "0703 123 4567", // MTN 70
            "08101234567",   // MTN 81
            "09031234567",   // 90
            "09161234567",   // 91
            "07051234567",   // Glo 70
            "+2348141234567",
            "2348171234567",
        ).forEach { assertTrue(isValidMobile(it, Country.NG)) }
    }

    @Test
    fun isValidMobile_rejectsNonMobileAndGarbage() {
        listOf(
            "23400000000000",  // leading 0 after 234 -> not [789]
            "+234 200 000 0000", // 20x is not a mobile lead
            "08231234567",     // second digit 2 -> not [01]
            "0123456789",      // landline-ish, no 234 mobile shape
            "0803123",         // too short
            "080312345678",    // too long (11 subscriber digits)
            "",
        ).forEach { assertFalse(isValidMobile(it, Country.NG)) }
    }

    @Test
    fun `nigeria mobile blocks the old regex accepted stay valid`() {
        listOf("7011111111", "8101111111", "9091111111", "8031234567").forEach {
            assertTrue(isValidMobile("0$it", Country.NG), "0$it")
            assertTrue(isValidMobile("+234$it", Country.NG), "+234$it")
        }
    }

    // --- country-aware cases ---

    @Test
    fun `ghana trunk number normalises`() {
        assertEquals("233244123456", normalisePhone("024 412 3456", Country.GH))
    }

    @Test
    fun `pasted full number is not double prefixed`() {
        assertEquals(
            "233244123456",
            normalisePhone(applyImpliedCountryCode("+233 24 412 3456", Country.GH), Country.GH),
        )
        assertEquals(
            "233244123456",
            normalisePhone(applyImpliedCountryCode("233244123456", Country.GH), Country.GH),
        )
    }

    @Test
    fun `foreign customer number keeps its own code`() {
        assertEquals("2348031234567", normalisePhone("+234 803 123 4567", Country.KE))
        assertEquals("2348031234567", normalisePhone("00234 803 123 4567", Country.KE))
    }

    @Test
    fun `gabon leading zero is part of the number`() {
        assertEquals("24106123456", normalisePhone(applyImpliedCountryCode("06 12 34 56", Country.GA), Country.GA))
        assertTrue(isValidMobile("06123456", Country.GA))
    }

    @Test
    fun `senegal implied chip`() {
        assertTrue(isValidMobile("77 123 45 67", Country.SN))
        assertFalse(isValidMobile("33 123 45 67", Country.SN)) // landline
    }

    @Test
    fun `valid sample per country`() {
        val samples = mapOf(
            Country.NG to "8031234567",
            Country.GH to "244123456",
            Country.KE to "712345678",
            Country.CI to "0701234567",
            Country.SN to "771234567",
            Country.BJ to "0190123456",
            Country.TG to "90123456",
            Country.ML to "76123456",
            Country.BF to "70123456",
            Country.NE to "93123456",
            Country.CM to "671234567",
            Country.GA to "06123456",
            Country.CG to "061234567",
            Country.GN to "621234567",
            Country.CD to "812345678",
        )
        assertEquals(Country.entries.toSet(), samples.keys)
        samples.forEach { (country, national) ->
            assertTrue(isValidMobile(national, country), "${country.iso} bare $national")
            assertTrue(isValidMobile("+${country.dialCode}$national", country), "${country.iso} +dial $national")
        }
    }

    @Test
    fun `whatsapp url uses country`() {
        assertEquals("https://wa.me/254712345678?text=hi", buildWhatsAppUrl("0712 345678", "hi", Country.KE))
        assertEquals("https://wa.me/?text=hi", buildWhatsAppUrl("", "hi", Country.KE))
    }
}
