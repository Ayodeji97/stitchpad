package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StyleCollectionLimitsTest {
    @Test fun free_hasNoFolders_flatCaps() {
        val insp = StyleCollectionLimits.forInspiration(SubscriptionTier.FREE)
        assertFalse(insp.foldersEnabled); assertEquals(10, insp.flatCap)
        assertEquals(5, StyleCollectionLimits.forCustomer(SubscriptionTier.FREE).flatCap)
    }
    @Test fun pro_inspiration_10folders_5each() {
        val l = StyleCollectionLimits.forInspiration(SubscriptionTier.PRO)
        assertTrue(l.foldersEnabled); assertEquals(10, l.maxFolders); assertEquals(5, l.maxImagesPerFolder)
    }
    @Test fun atelier_inspiration_20folders_10each() {
        val l = StyleCollectionLimits.forInspiration(SubscriptionTier.ATELIER)
        assertEquals(20, l.maxFolders); assertEquals(10, l.maxImagesPerFolder)
    }
    @Test fun pro_customer_5folders_3each() {
        val l = StyleCollectionLimits.forCustomer(SubscriptionTier.PRO)
        assertEquals(5, l.maxFolders); assertEquals(3, l.maxImagesPerFolder)
    }
    @Test fun atelier_customer_5folders_5each() {
        val l = StyleCollectionLimits.forCustomer(SubscriptionTier.ATELIER)
        assertEquals(5, l.maxFolders); assertEquals(5, l.maxImagesPerFolder)
    }
}
