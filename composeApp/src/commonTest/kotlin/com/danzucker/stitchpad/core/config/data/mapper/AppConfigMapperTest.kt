package com.danzucker.stitchpad.core.config.data.mapper

import com.danzucker.stitchpad.core.config.data.dto.AppConfigDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppConfigMapperTest {

    @Test
    fun fullDto_mapsAllFields() {
        val dto = AppConfigDto(
            communityEnabled = true,
            communityInviteUrl = "https://chat.whatsapp.com/ABC123",
            billingEnabled = true,
        )

        val config = dto.toAppConfig()

        assertTrue(config.communityEnabled)
        assertEquals("https://chat.whatsapp.com/ABC123", config.communityInviteUrl)
        assertTrue(config.billingEnabled)
    }

    @Test
    fun emptyDto_mapsToDisabledDefaults() {
        val config = AppConfigDto().toAppConfig()

        assertFalse(config.communityEnabled)
        assertNull(config.communityInviteUrl)
        // Default-false billing: Android upgrades stay gated until the server flips it.
        assertFalse(config.billingEnabled)
    }

    @Test
    fun enabledWithBlankUrl_keepsBlankUrlForCallerToGuard() {
        val config = AppConfigDto(communityEnabled = true, communityInviteUrl = "").toAppConfig()

        assertTrue(config.communityEnabled)
        assertEquals("", config.communityInviteUrl)
    }
}
