package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMapperTest {

    @Test
    fun dtoToPayment_mapsAllFields() {
        val dto = PaymentDto(
            id = "p1",
            amount = 40_000.0,
            method = "TRANSFER",
            type = "DEPOSIT",
            recordedAt = 1_700_000_000_000L,
            note = "Bank transfer ref 1234",
        )
        val payment = dto.toPayment()
        assertEquals("p1", payment.id)
        assertEquals(40_000.0, payment.amount)
        assertEquals(PaymentMethod.TRANSFER, payment.method)
        assertEquals(PaymentType.DEPOSIT, payment.type)
        assertEquals(1_700_000_000_000L, payment.recordedAt)
        assertEquals("Bank transfer ref 1234", payment.note)
    }

    @Test
    fun dtoWithUnknownMethod_fallsBackToOther() {
        val dto = PaymentDto(method = "BITCOIN", type = "FINAL")
        val payment = dto.toPayment()
        assertEquals(PaymentMethod.OTHER, payment.method)
        assertEquals(PaymentType.FINAL, payment.type)
    }

    @Test
    fun dtoWithUnknownType_fallsBackToDeposit() {
        val dto = PaymentDto(method = "CASH", type = "GIFT")
        val payment = dto.toPayment()
        assertEquals(PaymentType.DEPOSIT, payment.type)
    }

    @Test
    fun paymentToDto_roundTrips() {
        val payment = Payment(
            id = "p2",
            amount = 25_000.0,
            method = PaymentMethod.CASH,
            type = PaymentType.PROGRESS,
            recordedAt = 1_700_000_100_000L,
            note = null,
        )
        val dto = payment.toPaymentDto()
        assertEquals(payment, dto.toPayment())
    }
}
