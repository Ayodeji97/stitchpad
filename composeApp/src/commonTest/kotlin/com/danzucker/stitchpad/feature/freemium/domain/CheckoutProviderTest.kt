package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckoutProviderTest {

    @Test
    fun iosAlwaysUsesApple() {
        assertEquals(CheckoutProvider.APPLE, checkoutProviderFor(isIos = true, country = Country.GH))
        assertEquals(CheckoutProvider.APPLE, checkoutProviderFor(isIos = true, country = Country.NG))
    }

    @Test
    fun androidNigeriaUsesPaystack() {
        assertEquals(CheckoutProvider.PAYSTACK, checkoutProviderFor(isIos = false, country = Country.NG))
    }

    @Test
    fun androidOutsideNigeriaUsesPlay() {
        assertEquals(CheckoutProvider.PLAY, checkoutProviderFor(isIos = false, country = Country.GH))
        assertEquals(CheckoutProvider.PLAY, checkoutProviderFor(isIos = false, country = Country.SN))
    }
}
