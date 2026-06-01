package com.danzucker.stitchpad.core.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsAppConfirmCodeTest {

    @Test
    fun defaultWhatsAppConfirmCode_isAlwaysFourDigits() {
        repeat(500) {
            val code = defaultWhatsAppConfirmCode()
            assertEquals(WHATSAPP_CONFIRM_CODE_LENGTH, code.length)
            assertTrue(code.all { it.isDigit() })
        }
    }
}
