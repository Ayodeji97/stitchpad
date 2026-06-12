package com.danzucker.stitchpad.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserTest {

    @Test
    fun customScheme_withoutQuery_isUpgradeWithEmptyPreselect() {
        assertEquals(UpgradePreselect(null, null), DeepLinkParser.parseUpgrade("stitchpad://upgrade"))
    }

    @Test
    fun customScheme_withQuery_parsesTierAndCadence() {
        assertEquals(
            UpgradePreselect("pro", "monthly"),
            DeepLinkParser.parseUpgrade("stitchpad://upgrade?tier=pro&cadence=monthly"),
        )
    }

    @Test
    fun universalLink_withQuery_parsesTierAndCadence() {
        assertEquals(
            UpgradePreselect("atelier", "annual"),
            DeepLinkParser.parseUpgrade("https://link.getstitchpad.com/upgrade?tier=atelier&cadence=annual"),
        )
    }

    @Test
    fun universalLink_withoutQuery_isUpgradeWithEmptyPreselect() {
        assertEquals(
            UpgradePreselect(null, null),
            DeepLinkParser.parseUpgrade("https://link.getstitchpad.com/upgrade"),
        )
    }

    @Test
    fun unrelatedHttpsPath_isNotAnUpgradeLink() {
        assertNull(DeepLinkParser.parseUpgrade("https://link.getstitchpad.com/something-else"))
    }

    @Test
    fun lookalikePath_doesNotMatchByPrefix() {
        // "/upgradezzz" must NOT be treated as the upgrade route.
        assertNull(DeepLinkParser.parseUpgrade("https://link.getstitchpad.com/upgradezzz"))
    }

    @Test
    fun otherCustomScheme_isNotAnUpgradeLink() {
        assertNull(DeepLinkParser.parseUpgrade("stitchpad://something"))
    }

    @Test
    fun wrongHost_isNotAnUpgradeLink() {
        assertNull(DeepLinkParser.parseUpgrade("https://getstitchpad.com/upgrade"))
    }

    @Test
    fun nullUrl_isNotAnUpgradeLink() {
        assertNull(DeepLinkParser.parseUpgrade(null))
    }
}
