package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.FabricImageRefDto
import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.data.dto.StyleImageRefDto
import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.model.PaymentMethod
import com.danzucker.stitchpad.core.domain.model.PaymentType
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import kotlin.time.Clock

/** Returns a `payments` list that already absorbs any legacy `depositPaid`.
 *
 *  Mirrors the read-side migration so write paths (e.g. recordPayment) don't
 *  silently drop the legacy deposit when they zero out `depositPaid`.
 */
fun migrateLegacyDeposit(
    payments: List<PaymentDto>,
    depositPaid: Double,
    createdAt: Long,
): List<PaymentDto> = if (
    depositPaid <= 0.0 ||
    payments.any { it.id == LEGACY_DEPOSIT_PAYMENT_ID }
) {
    payments
} else {
    listOf(
        PaymentDto(
            id = LEGACY_DEPOSIT_PAYMENT_ID,
            amount = depositPaid,
            method = PaymentMethod.OTHER.name,
            type = PaymentType.DEPOSIT.name,
            recordedAt = createdAt,
            note = null,
        )
    ) + payments
}

private const val LEGACY_DEPOSIT_PAYMENT_ID = "legacy-deposit"

fun OrderDto.toOrder(userId: String): Order {
    val parsedStatus = runCatching { OrderStatus.valueOf(status) }
        .getOrDefault(OrderStatus.PENDING)
    val parsedSubStatus = if (parsedStatus == OrderStatus.IN_PROGRESS) {
        subStatus?.let { runCatching { OrderSubStatus.valueOf(it) }.getOrNull() }
    } else {
        // Force null when not IN_PROGRESS so an inconsistent document can't
        // surface a misleading sub-stage in the UI.
        null
    }
    // Migrate legacy docs: if payments list is empty but the old depositPaid field has a value,
    // synthesise a single payment so the computed Order.depositPaid is non-zero.
    val resolvedPayments = migrateLegacyDeposit(payments, depositPaid, createdAt)
        .map { it.toPayment() }
    return Order(
        id = id,
        userId = userId,
        customerId = customerId,
        customerName = customerName,
        items = items.map { it.toOrderItem() },
        status = parsedStatus,
        subStatus = parsedSubStatus,
        priority = runCatching { OrderPriority.valueOf(priority) }
            .getOrDefault(OrderPriority.NORMAL),
        statusHistory = statusHistory.map { it.toStatusChange() },
        totalPrice = totalPrice,
        payments = resolvedPayments,
        deadline = deadline,
        notes = notes,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Order.toOrderDto(): OrderDto {
    val now = Clock.System.now().toEpochMilliseconds()
    return OrderDto(
        id = id,
        customerId = customerId,
        customerName = customerName,
        status = status.name,
        subStatus = subStatus?.name,
        priority = priority.name,
        totalPrice = totalPrice,
        payments = payments.map { it.toPaymentDto() },
        deadline = deadline,
        notes = notes,
        archivedAt = archivedAt,
        items = items.map { it.toOrderItemDto() },
        statusHistory = statusHistory.map { it.toStatusChangeDto() },
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now,
    )
}

fun PaymentDto.toPayment(): Payment = Payment(
    id = id,
    amount = amount,
    method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.OTHER),
    type = runCatching { PaymentType.valueOf(type) }.getOrDefault(PaymentType.DEPOSIT),
    recordedAt = recordedAt,
    note = note,
)

fun Payment.toPaymentDto(): PaymentDto = PaymentDto(
    id = id,
    amount = amount,
    method = method.name,
    type = type.name,
    recordedAt = recordedAt,
    note = note,
)

fun OrderItemDto.toOrderItem(): OrderItem {
    // Source of truth: the new lists. If empty (pre-PTSP-11 docs), synthesize
    // a 1-element list from the legacy single fields so the rest of the app
    // sees a uniform shape.
    val resolvedStyleImages: List<StyleImageRef> = when {
        styleImages.isNotEmpty() -> styleImages.map { it.toStyleImageRef() }
        !styleId.isNullOrBlank() -> listOf(
            StyleImageRef(source = StyleImageSource.LIBRARY, styleId = styleId),
        )
        !stylePhotoUrl.isNullOrBlank() -> listOf(
            StyleImageRef(
                source = StyleImageSource.UPLOADED,
                photoUrl = stylePhotoUrl,
                photoStoragePath = stylePhotoStoragePath,
            ),
        )
        else -> emptyList()
    }
    val resolvedFabricImages: List<FabricImageRef> = when {
        fabricImages.isNotEmpty() -> fabricImages.map { it.toFabricImageRef() }
        !fabricPhotoUrl.isNullOrBlank() -> listOf(
            FabricImageRef(
                photoUrl = fabricPhotoUrl,
                photoStoragePath = fabricPhotoStoragePath.orEmpty(),
            ),
        )
        else -> emptyList()
    }
    return OrderItem(
        id = id,
        garmentType = parseGarmentType(garmentType),
        customGarmentName = customGarmentName,
        description = description,
        price = price,
        measurementId = measurementId,
        fabricName = fabricName,
        styleImages = resolvedStyleImages,
        fabricImages = resolvedFabricImages,
        // Carry legacy fields forward verbatim so the domain object can be
        // re-written without losing any data — useful in case the document
        // is round-tripped without modification.
        styleId = styleId,
        stylePhotoUrl = stylePhotoUrl,
        stylePhotoStoragePath = stylePhotoStoragePath,
        fabricPhotoUrl = fabricPhotoUrl,
        fabricPhotoStoragePath = fabricPhotoStoragePath,
    )
}

private fun StyleImageRefDto.toStyleImageRef(): StyleImageRef = StyleImageRef(
    source = runCatching { StyleImageSource.valueOf(source) }
        .getOrDefault(StyleImageSource.UPLOADED),
    styleId = styleId,
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
    syncState = parseImageSyncState(syncState),
)

private fun FabricImageRefDto.toFabricImageRef(): FabricImageRef = FabricImageRef(
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
    syncState = parseImageSyncState(syncState),
)

private fun parseImageSyncState(value: String): ImageSyncState =
    runCatching { ImageSyncState.valueOf(value) }.getOrDefault(ImageSyncState.SYNCED)

private fun parseGarmentType(value: String): GarmentType = when (value) {
    "SENATOR_KAFTAN" -> GarmentType.SENATOR
    "BUBA_AND_SKIRT" -> GarmentType.TWO_PIECE
    else -> runCatching { GarmentType.valueOf(value) }.getOrDefault(GarmentType.SHIRT)
}

fun OrderItem.toOrderItemDto(): OrderItemDto {
    // Double-write: write the new lists AND derive the legacy fields from the
    // first element of each (so pre-PTSP-11 app versions still see one image).
    val firstLibraryStyle = styleImages.firstOrNull { it.source == StyleImageSource.LIBRARY }
    val firstUploadedStyle = styleImages.firstOrNull { it.source == StyleImageSource.UPLOADED }
    val firstFabric = fabricImages.firstOrNull()
    return OrderItemDto(
        id = id,
        garmentType = garmentType.name,
        customGarmentName = customGarmentName,
        description = description,
        price = price,
        measurementId = measurementId,
        fabricName = fabricName,
        styleImages = styleImages.map { it.toStyleImageRefDto() },
        fabricImages = fabricImages.map { it.toFabricImageRefDto() },
        // Legacy double-write
        styleId = firstLibraryStyle?.styleId,
        stylePhotoUrl = firstUploadedStyle?.photoUrl,
        stylePhotoStoragePath = firstUploadedStyle?.photoStoragePath,
        fabricPhotoUrl = firstFabric?.photoUrl,
        fabricPhotoStoragePath = firstFabric?.photoStoragePath,
    )
}

private fun StyleImageRef.toStyleImageRefDto(): StyleImageRefDto = StyleImageRefDto(
    source = source.name,
    styleId = styleId,
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
    syncState = syncState.name,
)

private fun FabricImageRef.toFabricImageRefDto(): FabricImageRefDto = FabricImageRefDto(
    photoUrl = photoUrl,
    photoStoragePath = photoStoragePath,
    syncState = syncState.name,
)

fun StatusChangeDto.toStatusChange(): StatusChange = StatusChange(
    status = runCatching { OrderStatus.valueOf(status) }
        .getOrDefault(OrderStatus.PENDING),
    changedAt = changedAt
)

fun StatusChange.toStatusChangeDto(): StatusChangeDto = StatusChangeDto(
    status = status.name,
    changedAt = changedAt
)
