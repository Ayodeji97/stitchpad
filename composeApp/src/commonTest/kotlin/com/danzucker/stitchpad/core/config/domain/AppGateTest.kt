package com.danzucker.stitchpad.core.config.domain

import com.danzucker.stitchpad.core.config.domain.model.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppGateTest {

    @Test
    fun disabledConfig_isAllowed() {
        val decision = AppGate.evaluate(AppConfig.Disabled, isIos = false, currentBuild = 100)

        assertEquals(AppGateDecision.Allowed, decision)
    }

    @Test
    fun buildBelowAndroidFloor_forcesUpdateWithAndroidUrl() {
        val config = AppConfig.Disabled.copy(
            minSupportedBuildAndroid = 420,
            updateUrlAndroid = "market://android",
            updateUrlIos = "https://ios",
            forceUpdateMessage = "Update now",
        )

        val decision = AppGate.evaluate(config, isIos = false, currentBuild = 419)

        val forceUpdate = assertIs<AppGateDecision.ForceUpdate>(decision)
        assertEquals("Update now", forceUpdate.message)
        assertEquals("market://android", forceUpdate.updateUrl)
    }

    @Test
    fun buildBelowIosFloor_forcesUpdateWithIosUrl() {
        val config = AppConfig.Disabled.copy(
            minSupportedBuildIos = 421,
            updateUrlAndroid = "market://android",
            updateUrlIos = "https://ios",
        )

        val decision = AppGate.evaluate(config, isIos = true, currentBuild = 420)

        val forceUpdate = assertIs<AppGateDecision.ForceUpdate>(decision)
        assertEquals("https://ios", forceUpdate.updateUrl)
    }

    @Test
    fun blankUpdateUrl_normalisesToNull() {
        val config = AppConfig.Disabled.copy(
            minSupportedBuildAndroid = 420,
            updateUrlAndroid = "   ",
        )

        val decision = AppGate.evaluate(config, isIos = false, currentBuild = 1)

        val forceUpdate = assertIs<AppGateDecision.ForceUpdate>(decision)
        assertEquals(null, forceUpdate.updateUrl)
    }

    @Test
    fun buildEqualToFloor_isAllowed() {
        val config = AppConfig.Disabled.copy(minSupportedBuildAndroid = 420)

        val decision = AppGate.evaluate(config, isIos = false, currentBuild = 420)

        assertEquals(AppGateDecision.Allowed, decision)
    }

    @Test
    fun buildAboveFloor_isAllowed() {
        val config = AppConfig.Disabled.copy(minSupportedBuildAndroid = 420)

        val decision = AppGate.evaluate(config, isIos = false, currentBuild = 999)

        assertEquals(AppGateDecision.Allowed, decision)
    }

    @Test
    fun nullBuildWithFloorSet_failsOpen() {
        val config = AppConfig.Disabled.copy(minSupportedBuildAndroid = 420)

        val decision = AppGate.evaluate(config, isIos = false, currentBuild = null)

        assertEquals(AppGateDecision.Allowed, decision)
    }

    @Test
    fun androidFloorIgnoredOnIos_crossPlatformIsolation() {
        // A floor set only for Android must never gate an iOS build.
        val config = AppConfig.Disabled.copy(minSupportedBuildAndroid = 9999)

        val decision = AppGate.evaluate(config, isIos = true, currentBuild = 1)

        assertEquals(AppGateDecision.Allowed, decision)
    }

    @Test
    fun maintenanceMode_showsMaintenance() {
        val config = AppConfig.Disabled.copy(
            maintenanceMode = true,
            maintenanceMessage = "Back soon",
        )

        val decision = AppGate.evaluate(config, isIos = false, currentBuild = 100)

        val maintenance = assertIs<AppGateDecision.Maintenance>(decision)
        assertEquals("Back soon", maintenance.message)
    }

    @Test
    fun forceUpdateTakesPrecedenceOverMaintenance() {
        val config = AppConfig.Disabled.copy(
            minSupportedBuildAndroid = 420,
            maintenanceMode = true,
        )

        val decision = AppGate.evaluate(config, isIos = false, currentBuild = 100)

        assertTrue(decision is AppGateDecision.ForceUpdate)
    }
}
