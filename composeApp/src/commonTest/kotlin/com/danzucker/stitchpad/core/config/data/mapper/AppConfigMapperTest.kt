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
            minSupportedBuildAndroid = 420,
            minSupportedBuildIos = 421,
            updateUrlAndroid = "https://play.google.com/store/apps/details?id=com.danzucker.stitchpad",
            updateUrlIos = "https://apps.apple.com/app/id123456789",
            forceUpdateMessage = "Please update to keep using StitchPad.",
            maintenanceMode = true,
            maintenanceMessage = "Back in a few minutes.",
        )

        val config = dto.toAppConfig()

        assertTrue(config.communityEnabled)
        assertEquals("https://chat.whatsapp.com/ABC123", config.communityInviteUrl)
        assertTrue(config.billingEnabled)
        assertEquals(420, config.minSupportedBuildAndroid)
        assertEquals(421, config.minSupportedBuildIos)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.danzucker.stitchpad",
            config.updateUrlAndroid,
        )
        assertEquals("https://apps.apple.com/app/id123456789", config.updateUrlIos)
        assertEquals("Please update to keep using StitchPad.", config.forceUpdateMessage)
        assertTrue(config.maintenanceMode)
        assertEquals("Back in a few minutes.", config.maintenanceMessage)
    }

    @Test
    fun emptyDto_mapsToDisabledDefaults() {
        val config = AppConfigDto().toAppConfig()

        assertFalse(config.communityEnabled)
        assertNull(config.communityInviteUrl)
        // Default-false billing: Android upgrades stay gated until the server flips it.
        assertFalse(config.billingEnabled)
        // Break-glass controls fail open: no floor, no maintenance lock, no copy.
        assertNull(config.minSupportedBuildAndroid)
        assertNull(config.minSupportedBuildIos)
        assertNull(config.updateUrlAndroid)
        assertNull(config.updateUrlIos)
        assertNull(config.forceUpdateMessage)
        assertFalse(config.maintenanceMode)
        assertNull(config.maintenanceMessage)
    }

    @Test
    fun enabledWithBlankUrl_keepsBlankUrlForCallerToGuard() {
        val config = AppConfigDto(communityEnabled = true, communityInviteUrl = "").toAppConfig()

        assertTrue(config.communityEnabled)
        assertEquals("", config.communityInviteUrl)
    }
}
