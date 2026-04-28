package com.danzucker.stitchpad.core.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneNormaliserTest {

    @Test
    fun normaliseNigerianPhone_stripsTrunkZeroAndPrependsCountryCode() {
        assertEquals("2348031234567", normaliseNigerianPhone("0803 123 4567"))
    }

    @Test
    fun normaliseNigerianPhone_keepsExistingCountryCode_andStripsFormatting() {
        assertEquals("2348031234567", normaliseNigerianPhone("+234 803 123 4567"))
        assertEquals("2348031234567", normaliseNigerianPhone("234-803-123-4567"))
    }

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
}
