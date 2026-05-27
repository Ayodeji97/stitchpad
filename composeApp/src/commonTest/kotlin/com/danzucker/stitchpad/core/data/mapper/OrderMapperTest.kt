package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
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
    fun quantity_roundTrips_dtoToItemAndBack() {
        val dto = OrderItemDto(
            id = "item-quantity",
            garmentType = "AGBADA",
            description = "Family asoebi",
            price = 30_000.0,
            quantity = 4,
        )

        val item = dto.toOrderItem()

        assertEquals(4, item.quantity)
        assertEquals(4, item.toOrderItemDto().quantity)
    }

    @Test
    fun quantity_defaultsToOneForLegacyOrInvalidDtos() {
        assertEquals(1, OrderItemDto(quantity = 0).toOrderItem().quantity)
        assertEquals(1, OrderItemDto(quantity = -3).toOrderItem().quantity)
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
    fun orderItem_roundTrips_stylePhotoUrlAndPath_throughDto() {
        // PTSP-11: style images are now stored as a list; an UPLOADED ref round-trips
        // through the new styleImages list, not the legacy single-field path.
        val item = OrderItem(
            id = "item-1",
            garmentType = GarmentType.SHIRT,
            description = "Test",
            price = 100.0,
            styleImages = listOf(
                StyleImageRef(
                    source = StyleImageSource.UPLOADED,
                    photoUrl = "https://example.com/style.jpg",
                    photoStoragePath = "users/u1/orders/o1/styles/item-1.jpg",
                ),
            ),
        )

        val roundTripped = item.toOrderItemDto().toOrderItem()

        assertEquals(1, roundTripped.styleImages.size)
        assertEquals(StyleImageSource.UPLOADED, roundTripped.styleImages[0].source)
        assertEquals("https://example.com/style.jpg", roundTripped.styleImages[0].photoUrl)
        assertEquals("users/u1/orders/o1/styles/item-1.jpg", roundTripped.styleImages[0].photoStoragePath)
        // Legacy double-write: stylePhotoUrl on DTO should be populated from the list
        assertEquals("https://example.com/style.jpg", roundTripped.toOrderItemDto().stylePhotoUrl)
    }

    @Test
    fun orderItem_roundTrips_nullStylePhotoFields_throughDto() {
        val item = OrderItem(
            id = "item-1",
            garmentType = GarmentType.SHIRT,
            description = "Test",
            price = 100.0,
        )

        val roundTripped = item.toOrderItemDto().toOrderItem()

        assertNull(roundTripped.stylePhotoUrl)
        assertNull(roundTripped.stylePhotoStoragePath)
        assertTrue(roundTripped.styleImages.isEmpty())
    }

    @Test
    fun `OrderItem round-trips empty styleImages and fabricImages through DTO`() {
        val item = OrderItem(
            id = "i1",
            garmentType = GarmentType.SHIRT,
            description = "Test",
            price = 100.0,
        )

        val roundTripped = item.toOrderItemDto().toOrderItem()

        assertTrue(roundTripped.styleImages.isEmpty())
        assertTrue(roundTripped.fabricImages.isEmpty())
    }

    @Test
    fun `OrderItem round-trips multi-image styleImages through DTO`() {
        val item = OrderItem(
            id = "i1",
            garmentType = GarmentType.SHIRT,
            description = "Test",
            price = 100.0,
            styleImages = listOf(
                StyleImageRef(source = StyleImageSource.LIBRARY, styleId = "s1"),
                StyleImageRef(
                    source = StyleImageSource.UPLOADED,
                    photoUrl = "https://example.com/u.jpg",
                    photoStoragePath = "users/u1/orders/o1/styles/i1-1.jpg",
                ),
            ),
        )

        val roundTripped = item.toOrderItemDto().toOrderItem()

        assertEquals(2, roundTripped.styleImages.size)
        assertEquals(StyleImageSource.LIBRARY, roundTripped.styleImages[0].source)
        assertEquals("s1", roundTripped.styleImages[0].styleId)
        assertEquals(StyleImageSource.UPLOADED, roundTripped.styleImages[1].source)
        assertEquals("https://example.com/u.jpg", roundTripped.styleImages[1].photoUrl)
    }

    @Test
    fun `OrderItem round-trips multi-image fabricImages through DTO`() {
        val item = OrderItem(
            id = "i1",
            garmentType = GarmentType.SHIRT,
            description = "Test",
            price = 100.0,
            fabricImages = listOf(
                FabricImageRef("https://example.com/f1.jpg", "users/u1/orders/o1/fabrics/i1-0.jpg"),
                FabricImageRef("https://example.com/f2.jpg", "users/u1/orders/o1/fabrics/i1-1.jpg"),
            ),
        )

        val roundTripped = item.toOrderItemDto().toOrderItem()

        assertEquals(2, roundTripped.fabricImages.size)
        assertEquals("https://example.com/f1.jpg", roundTripped.fabricImages[0].photoUrl)
    }

    @Test
    fun `legacy styleId in DTO migrates to a single LIBRARY StyleImageRef`() {
        // Simulate a pre-PTSP-11 Firestore doc: only legacy single fields populated.
        val dto = OrderItemDto(
            id = "i1",
            garmentType = "SHIRT",
            description = "Test",
            price = 100.0,
            styleId = "legacy-style-1",
        )

        val item = dto.toOrderItem()

        assertEquals(1, item.styleImages.size)
        assertEquals(StyleImageSource.LIBRARY, item.styleImages[0].source)
        assertEquals("legacy-style-1", item.styleImages[0].styleId)
    }

    @Test
    fun `legacy stylePhotoUrl in DTO migrates to a single UPLOADED StyleImageRef`() {
        val dto = OrderItemDto(
            id = "i1",
            garmentType = "SHIRT",
            description = "Test",
            price = 100.0,
            stylePhotoUrl = "https://example.com/legacy.jpg",
            stylePhotoStoragePath = "users/u1/orders/o1/styles/i1.jpg",
        )

        val item = dto.toOrderItem()

        assertEquals(1, item.styleImages.size)
        assertEquals(StyleImageSource.UPLOADED, item.styleImages[0].source)
        assertEquals("https://example.com/legacy.jpg", item.styleImages[0].photoUrl)
    }

    @Test
    fun `legacy fabricPhotoUrl in DTO migrates to a single FabricImageRef`() {
        val dto = OrderItemDto(
            id = "i1",
            garmentType = "SHIRT",
            description = "Test",
            price = 100.0,
            fabricPhotoUrl = "https://example.com/fabric.jpg",
            fabricPhotoStoragePath = "users/u1/orders/o1/fabrics/i1.jpg",
        )

        val item = dto.toOrderItem()

        assertEquals(1, item.fabricImages.size)
        assertEquals("https://example.com/fabric.jpg", item.fabricImages[0].photoUrl)
    }

    @Test
    fun `OrderItem double-writes legacy fields from multi-image lists`() {
        val item = OrderItem(
            id = "i1",
            garmentType = GarmentType.SHIRT,
            description = "Test",
            price = 100.0,
            styleImages = listOf(
                StyleImageRef(source = StyleImageSource.LIBRARY, styleId = "s1"),
                StyleImageRef(
                    source = StyleImageSource.UPLOADED,
                    photoUrl = "https://example.com/u.jpg",
                    photoStoragePath = "p1",
                ),
            ),
            fabricImages = listOf(
                FabricImageRef("https://example.com/f.jpg", "fp1"),
            ),
        )

        val dto = item.toOrderItemDto()

        // Legacy single fields should be derived from the lists for backward read
        assertEquals("s1", dto.styleId)
        assertEquals("https://example.com/u.jpg", dto.stylePhotoUrl)
        assertEquals("https://example.com/f.jpg", dto.fabricPhotoUrl)
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
