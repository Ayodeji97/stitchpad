package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StyleShareFormatterTest {

    private fun style(description: String) = Style(
        id = "s1",
        customerId = "c1",
        description = description,
        photoUrl = "https://example.com/p.jpg",
        photoStoragePath = "styles/s1.jpg",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val attribution = "Shared via StitchPad · getstitchpad.com"

    @Test
    fun free_tier_with_description_appends_attribution() {
        val result = StyleShareFormatter.caption(style("Blue agbada"), SubscriptionTier.FREE)
        assertEquals("Blue agbada\n\n$attribution", result)
    }

    @Test
    fun paid_tier_with_description_has_no_attribution() {
        val result = StyleShareFormatter.caption(style("Blue agbada"), SubscriptionTier.PRO)
        assertEquals("Blue agbada", result)
    }

    @Test
    fun paid_tier_blank_description_is_null() {
        assertNull(StyleShareFormatter.caption(style("   "), SubscriptionTier.ATELIER))
    }

    @Test
    fun free_tier_blank_description_is_attribution_only() {
        val result = StyleShareFormatter.caption(style(""), SubscriptionTier.FREE)
        assertEquals(attribution, result)
    }
}
