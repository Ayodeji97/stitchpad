package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderMapperTest {

    @Test
    fun dtoToOrder_mapsSubStatusWhenInProgress() {
        val dto = OrderDto(status = "IN_PROGRESS", subStatus = "FITTING")
        val order = dto.toOrder("u1")
        assertEquals(OrderSubStatus.FITTING, order.subStatus)
    }

    @Test
    fun dtoToOrder_dropsSubStatusWhenStatusIsNotInProgress() {
        // Spec invariant: subStatus is only meaningful for IN_PROGRESS orders.
        // An inconsistent Firestore doc must not surface a misleading stage.
        val dto = OrderDto(status = "READY", subStatus = "FITTING")
        val order = dto.toOrder("u1")
        assertNull(order.subStatus, "subStatus should be cleared when status != IN_PROGRESS")
    }

    @Test
    fun dtoToOrder_unknownSubStatusFallsBackToNull() {
        val dto = OrderDto(status = "IN_PROGRESS", subStatus = "QUILTING")
        val order = dto.toOrder("u1")
        assertNull(order.subStatus)
    }

    @Test
    fun dtoToOrder_synthesisesLegacyDepositWhenPaymentsEmpty() {
        val dto = OrderDto(
            depositPaid = 30_000.0,
            payments = emptyList(),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_999_999L, // proves recordedAt uses createdAt, not updatedAt
        )
        val order = dto.toOrder("u1")
        assertEquals(1, order.payments.size)
        val p = order.payments.single()
        assertEquals(30_000.0, p.amount)
        assertEquals(PaymentMethod.OTHER, p.method)
        assertEquals(PaymentType.DEPOSIT, p.type)
        assertEquals(1_700_000_000_000L, p.recordedAt, "legacy deposit recordedAt should use createdAt")
        assertEquals("legacy-deposit", p.id)
    }

    @Test
    fun dtoToOrder_doesNotSynthesiseWhenPaymentsListIsPopulated() {
        val dto = OrderDto(
            depositPaid = 30_000.0,
            payments = listOf(
                PaymentDto(id = "p1", amount = 20_000.0, method = "CASH", type = "DEPOSIT"),
            ),
        )
        val order = dto.toOrder("u1")
        assertEquals(1, order.payments.size)
        assertEquals("p1", order.payments.single().id)
        assertEquals(20_000.0, order.depositPaid)
    }

    @Test
    fun dtoToOrder_zeroDepositPaidNoSynthesis() {
        val dto = OrderDto(depositPaid = 0.0, payments = emptyList())
        val order = dto.toOrder("u1")
        assertTrue(order.payments.isEmpty())
    }

    @Test
    fun dtoToOrder_archivedAtRoundTrips() {
        val dto = OrderDto(archivedAt = 1_700_000_500_000L)
        val order = dto.toOrder("u1")
        assertEquals(1_700_000_500_000L, order.archivedAt)
    }

    @Test
    fun orderToDto_omitsSubStatusWhenNull() {
        val dtoIn = OrderDto(status = "PENDING", subStatus = null)
        val order = dtoIn.toOrder("u1")
        val dtoOut = order.toOrderDto()
        assertNull(dtoOut.subStatus)
    }

    @Test
    fun orderToDto_persistsPaymentsAsList() {
        val dtoIn = OrderDto(
            payments = listOf(
                PaymentDto(id = "p1", amount = 50_000.0, method = "TRANSFER", type = "DEPOSIT"),
            ),
            totalPrice = 100_000.0,
        )
        val order = dtoIn.toOrder("u1")
        val dtoOut = order.toOrderDto()
        assertEquals(1, dtoOut.payments.size)
        assertEquals("p1", dtoOut.payments.single().id)
    }

    @Test
    fun fabricName_roundTrips_dtoToItemAndBack() {
        val dto = OrderItemDto(
            id = "item1",
            garmentType = "SHIRT",
            description = "Blue shirt",
            price = 30_000.0,
            fabricName = "Royal Lace",
        )
        val item = dto.toOrderItem()
        assertEquals("Royal Lace", item.fabricName)

        val dtoOut = item.toOrderItemDto()
        assertEquals("Royal Lace", dtoOut.fabricName)
    }

    @Test
    fun fabricName_nullPreserved_throughRoundTrip() {
        val dto = OrderItemDto(
            id = "item2",
            garmentType = "SENATOR",
            description = "White senator",
            price = 80_000.0,
            fabricName = null,
        )
        val item = dto.toOrderItem()
        assertNull(item.fabricName)
        assertNull(item.toOrderItemDto().fabricName)
    }

    @Test
    fun migrateLegacyDeposit_synthesisesPaymentWhenListEmptyAndDepositPositive() {
        val migrated = migrateLegacyDeposit(
            payments = emptyList(),
            depositPaid = 10_000.0,
            createdAt = 1_700_000_000_000L,
        )
        assertEquals(1, migrated.size)
        val p = migrated.single()
        assertEquals(10_000.0, p.amount)
        assertEquals(PaymentMethod.OTHER.name, p.method)
        assertEquals(PaymentType.DEPOSIT.name, p.type)
        assertEquals("legacy-deposit", p.id)
    }

    @Test
    fun migrateLegacyDeposit_returnsExistingListWhenPaymentsAlreadyPresent() {
        val existing = listOf(
            PaymentDto(id = "p1", amount = 5_000.0, method = "TRANSFER", type = "PROGRESS"),
        )
        val migrated = migrateLegacyDeposit(
            payments = existing,
            depositPaid = 99_999.0,
            createdAt = 0L,
        )
        assertEquals(existing, migrated)
    }

    @Test
    fun migrateLegacyDeposit_emptyListZeroDeposit_returnsEmpty() {
        val migrated = migrateLegacyDeposit(emptyList(), 0.0, 0L)
        assertTrue(migrated.isEmpty())
    }

    @Test
    fun recordPaymentWritePath_legacyDepositIsPreserved() {
        // Regression for data-loss bug: a legacy doc has depositPaid=10k and
        // payments=[]. When recordPayment writes a new ₦5k progress payment,
        // the legacy ₦10k must be migrated into payments BEFORE depositPaid is
        // zeroed — otherwise the original deposit vanishes.
        val legacyDto = OrderDto(
            depositPaid = 10_000.0,
            payments = emptyList(),
            createdAt = 1_700_000_000_000L,
        )
        val newPayment = PaymentDto(
            id = "p-new",
            amount = 5_000.0,
            method = "TRANSFER",
            type = "PROGRESS",
            recordedAt = 1_700_000_500_000L,
        )

        val migratedPayments = migrateLegacyDeposit(
            payments = legacyDto.payments,
            depositPaid = legacyDto.depositPaid,
            createdAt = legacyDto.createdAt,
        )
        val updatedDto = legacyDto.copy(
            payments = migratedPayments + newPayment,
            depositPaid = 0.0,
        )

        assertEquals(2, updatedDto.payments.size)
        assertEquals(15_000.0, updatedDto.payments.sumOf { it.amount })
        assertEquals(0.0, updatedDto.depositPaid)
        // And the migrated order's computed depositPaid still surfaces the full ₦15k.
        assertEquals(15_000.0, updatedDto.toOrder("u1").depositPaid)
    }
}
