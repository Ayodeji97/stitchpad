package com.danzucker.stitchpad.feature.freemium.presentation

import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanPriceFormattingTest {

    @Test
    fun nairaWorkshop_usesFamiliarSymbol() {
        assertEquals("₦2,000", formatPlanPrice(2_000.0, Currency.NGN))
    }

    @Test
    fun nonNairaWorkshops_seeTheRealNairaAmountLabelledWithIsoCode() {
        // No FX: the amount is what Paystack charges, labelled so it can't pass for local money.
        assertEquals("NGN 2,000", formatPlanPrice(2_000.0, Currency.GHS))
        assertEquals("NGN 2,000", formatPlanPrice(2_000.0, Currency.XOF))
        assertEquals("NGN 40,000", formatPlanPrice(40_000.0, Currency.KES))
    }

    @Test
    fun tierPriceText_ngn() {
        val pro = paystackPlanPriceText(SubscriptionTier.PRO, Currency.NGN)
        assertEquals(PlanPriceText(monthly = "₦2,000", annual = "₦20,000", annualSaving = "₦4,000"), pro)
        val atelier = paystackPlanPriceText(SubscriptionTier.ATELIER, Currency.NGN)
        assertEquals(PlanPriceText(monthly = "₦4,000", annual = "₦40,000", annualSaving = "₦8,000"), atelier)
    }

    @Test
    fun tierPriceText_xof() {
        val pro = paystackPlanPriceText(SubscriptionTier.PRO, Currency.XOF)
        assertEquals(PlanPriceText(monthly = "NGN 2,000", annual = "NGN 20,000", annualSaving = "NGN 4,000"), pro)
    }

    @Test
    fun tierPriceText_freeHasNoPrice() {
        assertNull(paystackPlanPriceText(SubscriptionTier.FREE, Currency.NGN))
    }

    @Test
    fun upgradeCtaPrice_paystack_isProMonthlyForTheWorkshop() {
        assertEquals("₦2,000", upgradeCtaPrice(isAppleCheckout = false, workshopCurrency = Currency.NGN))
        assertEquals("NGN 2,000", upgradeCtaPrice(isAppleCheckout = false, workshopCurrency = Currency.GHS))
    }

    @Test
    fun upgradeCtaPrice_apple_isNeutral() {
        // Apple charges its storefront price, so no Paystack amount is quoted on iOS.
        assertNull(upgradeCtaPrice(isAppleCheckout = true, workshopCurrency = Currency.NGN))
        assertNull(upgradeCtaPrice(isAppleCheckout = true, workshopCurrency = Currency.XOF))
    }

    @Test
    fun foreignCurrencyNote_onlyOutsideNaira() {
        assertFalse(isBilledInForeignCurrency(Currency.NGN))
        assertTrue(isBilledInForeignCurrency(Currency.GHS))
        assertTrue(isBilledInForeignCurrency(Currency.XOF))
    }
}
