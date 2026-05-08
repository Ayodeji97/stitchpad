package com.danzucker.stitchpad.core.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneNormaliserTest {

    // --- normaliseNigerianPhone ---

    @Test
    fun `normaliseNigerianPhone strips trunk prefix`() {
        assertEquals("2348031234567", normaliseNigerianPhone("0803 123 4567"))
    }

    @Test
    fun `normaliseNigerianPhone keeps E164 form`() {
        assertEquals("2348031234567", normaliseNigerianPhone("+234 803 123 4567"))
    }

    @Test
    fun normaliseNigerianPhone_keepsExistingCountryCode_andStripsFormatting() {
        assertEquals("2348031234567", normaliseNigerianPhone("+234 803 123 4567"))
        assertEquals("2348031234567", normaliseNigerianPhone("234-803-123-4567"))
    }

    @Test
    fun `normaliseNigerianPhone leaves bare 10-digit input alone`() {
        // Customer flows reuse normaliseNigerianPhone via buildWhatsAppUrl, where a
        // bare 10-digit input might be a non-Nigerian customer (e.g. US 2125550123).
        // The country-code prefix must NOT be auto-applied here.
        assertEquals("2125550123", normaliseNigerianPhone("2125550123"))
        assertFalse(validateNigerianMobileE164("2125550123"))
    }

    @Test
    fun `applyImpliedNigerianCountryCode prefixes bare 10-digit subscriber`() {
        // Used at workshop call site where the UI shows a +234 chip outside the input.
        assertEquals("2348064816695", normaliseNigerianPhone(applyImpliedNigerianCountryCode("8064816695")))
        assertTrue(validateNigerianMobileE164(applyImpliedNigerianCountryCode("8064816695")))
    }

    @Test
    fun `applyImpliedNigerianCountryCode leaves trunk-prefixed input alone`() {
        assertEquals("08031234567", applyImpliedNigerianCountryCode("08031234567"))
    }

    @Test
    fun `applyImpliedNigerianCountryCode leaves already-prefixed input alone`() {
        assertEquals("+2348031234567", applyImpliedNigerianCountryCode("+2348031234567"))
        assertEquals("2348031234567", applyImpliedNigerianCountryCode("2348031234567"))
    }

    // --- buildWhatsAppUrl ---

    @Test
    fun buildWhatsAppUrl_passesAsciiUnreservedThrough() {
        val url = buildWhatsAppUrl(
            phone = "+2348031234567",
            message = "Hello-world_OK.tilde~99"
        )
        assertEquals("https://wa.me/2348031234567?text=Hello-world_OK.tilde~99", url)
    }

    @Test
    fun buildWhatsAppUrl_encodesSpacesAsPercent20() {
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "Hi Ade")
        assertTrue(url.endsWith("?text=Hi%20Ade"), "got=$url")
    }

    @Test
    fun buildWhatsAppUrl_percentEncodesYorubaDiacritics() {
        // 'é' = 0xC3 0xA9 in UTF-8 — must come out as %C3%A9, not raw, otherwise
        // wa.me rejects the URL. Char.isLetterOrDigit() (Unicode-aware) used to
        // let this through unencoded.
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "Adé")
        assertTrue(url.endsWith("?text=Ad%C3%A9"), "got=$url")
    }

    @Test
    fun buildWhatsAppUrl_percentEncodesNairaSign() {
        // '₦' = 0xE2 0x82 0xA6 in UTF-8.
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "₦100")
        assertTrue(url.endsWith("?text=%E2%82%A6100"), "got=$url")
    }

    @Test
    fun buildWhatsAppUrl_percentEncodesSurrogatePairEmoji() {
        // '💍' = U+1F48D, encoded as UTF-8 bytes 0xF0 0x9F 0x92 0x8D.
        // In a Kotlin String it's a surrogate pair (D83D DC8D). Iterating by
        // Char would split them and corrupt the UTF-8 encoding; iterating the
        // whole-string UTF-8 byte array handles it correctly.
        val url = buildWhatsAppUrl(phone = "+2348031234567", message = "💍")
        assertTrue(url.endsWith("?text=%F0%9F%92%8D"), "got=$url")
    }

    // --- validateNigerianMobileE164 ---

    @Test
    fun `validateNigerianMobileE164 accepts 13 digits starting with 234`() {
        assertTrue(validateNigerianMobileE164("2348031234567"))
    }

    @Test
    fun `validateNigerianMobileE164 accepts trunk-prefixed input`() {
        assertTrue(validateNigerianMobileE164("08031234567"))
    }

    @Test
    fun `validateNigerianMobileE164 rejects too short`() {
        assertFalse(validateNigerianMobileE164("0803123"))
    }

    @Test
    fun `validateNigerianMobileE164 rejects too long`() {
        assertFalse(validateNigerianMobileE164("234803123456789"))
    }

    @Test
    fun `validateNigerianMobileE164 rejects empty`() {
        assertFalse(validateNigerianMobileE164(""))
    }
}
