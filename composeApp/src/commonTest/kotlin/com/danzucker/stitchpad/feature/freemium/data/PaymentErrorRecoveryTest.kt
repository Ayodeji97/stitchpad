package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.feature.freemium.domain.PaymentError
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GitLive on iOS can surface the callable HttpsError as a generic exception with
 * the canonical code lost, so the server's message markers are how we recover the
 * intended PaymentError instead of dumping everything into NETWORK.
 */
class PaymentErrorRecoveryTest {

    @Test
    fun invalidPlanMarkerRecoversInvalidPlan() {
        assertEquals(PaymentError.INVALID_PLAN, recoverPaymentError("invalid_plan", PaymentError.NETWORK))
    }

    @Test
    fun providerUnavailableMarkerRecoversProviderUnavailable() {
        assertEquals(
            PaymentError.PROVIDER_UNAVAILABLE,
            recoverPaymentError("payment_provider_unavailable", PaymentError.NETWORK),
        )
    }

    @Test
    fun markerMatchesAsSubstring() {
        assertEquals(
            PaymentError.INVALID_PLAN,
            recoverPaymentError("FIRFunctionsErrorDomain: invalid_plan (3)", PaymentError.UNKNOWN),
        )
    }

    @Test
    fun missingEmailMarkerRecoversMissingEmail() {
        assertEquals(PaymentError.MISSING_EMAIL, recoverPaymentError("missing_email", PaymentError.NETWORK))
    }

    @Test
    fun nullMessageReturnsFallback() {
        assertEquals(PaymentError.NETWORK, recoverPaymentError(null, PaymentError.NETWORK))
    }

    @Test
    fun unknownMessageReturnsFallback() {
        assertEquals(PaymentError.NETWORK, recoverPaymentError("connection reset by peer", PaymentError.NETWORK))
    }
}
