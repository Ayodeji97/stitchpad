package com.danzucker.stitchpad.feature.dashboard.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderCodeTest {

    @Test
    fun derivesUppercasedLastFourCharsPrefixedWithOrd() {
        assertEquals("ORD-C3D4", orderCodeFor("abc123-e5f6c3d4"))
    }

    @Test
    fun uppercasesLowercaseHexTail() {
        assertEquals("ORD-AB12", orderCodeFor("order-ab12"))
    }

    @Test
    fun shortIdShorterThanFourCharsUsesWholeId() {
        assertEquals("ORD-AB", orderCodeFor("ab"))
    }

    @Test
    fun emptyIdProducesBarePrefix() {
        assertEquals("ORD-", orderCodeFor(""))
    }
}
