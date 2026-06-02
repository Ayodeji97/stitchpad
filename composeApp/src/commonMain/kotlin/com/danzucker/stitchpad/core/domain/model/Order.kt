package com.danzucker.stitchpad.core.domain.model

enum class OrderStatus {
    PENDING,
    IN_PROGRESS,
    READY,
    DELIVERED
}

enum class OrderPriority {
    NORMAL,
    URGENT,
    RUSH
}

/**
 * Sub-stages within IN_PROGRESS that match how tailors narrate work
 * (cutting → sewing → fitting). Only meaningful when [Order.status] is
 * [OrderStatus.IN_PROGRESS]; null otherwise.
 */
enum class OrderSubStatus {
    CUTTING,
    SEWING,
    FITTING,
}

data class OrderItem(
    val id: String,
    val garmentType: GarmentType,
    val customGarmentName: String? = null, // set only when garmentType == OTHER
    val description: String,
    val price: Double,
    val measurementId: String? = null,
    val fabricName: String? = null,
    // PTSP-11 multi-image
    val styleImages: List<StyleImageRef> = emptyList(),
    val fabricImages: List<FabricImageRef> = emptyList(),
    // Legacy single fields — kept on domain for the double-write path in OrderMapper.
    // Read-time: ignored if `styleImages`/`fabricImages` are non-empty; otherwise
    // the mapper synthesizes a 1-element list from these. Write-time: derived
    // from the lists (first element of each) so older app versions can still
    // render something. Removable in mid-2027.
    val styleId: String? = null,
    val stylePhotoUrl: String? = null,
    val stylePhotoStoragePath: String? = null,
    val fabricPhotoUrl: String? = null,
    val fabricPhotoStoragePath: String? = null,
)

enum class StyleImageSource { LIBRARY, UPLOADED }

enum class ImageSyncState { SYNCED, PENDING, FAILED }

data class StyleImageRef(
    val source: StyleImageSource,
    val styleId: String? = null, // set when source == LIBRARY
    val photoUrl: String? = null, // set when source == UPLOADED
    val photoStoragePath: String? = null, // set when source == UPLOADED
    val syncState: ImageSyncState = ImageSyncState.SYNCED,
    val localPhotoPath: String? = null,
)

data class FabricImageRef(
    val photoUrl: String,
    val photoStoragePath: String,
    val syncState: ImageSyncState = ImageSyncState.SYNCED,
    val localPhotoPath: String? = null,
)

data class StatusChange(
    val status: OrderStatus,
    val changedAt: Long
)

data class Order(
    val id: String,
    val userId: String,
    val customerId: String,
    val customerName: String,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val subStatus: OrderSubStatus? = null,
    val priority: OrderPriority,
    val statusHistory: List<StatusChange>,
    val totalPrice: Double,
    val payments: List<Payment> = emptyList(),
    val deadline: Long?,
    val notes: String?,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Sum of all recorded payments. Replaces the prior persisted `depositPaid` field. */
    val depositPaid: Double get() = payments.sumOf { it.amount }

    /** Outstanding balance. Always recomputed from [totalPrice] and [payments]. */
    val balanceRemaining: Double get() = (totalPrice - depositPaid).coerceAtLeast(0.0)
}

fun Order.ownedStoragePaths(): List<String> =
    items
        .flatMap { item ->
            buildList {
                item.fabricPhotoStoragePath?.let(::add)
                item.stylePhotoStoragePath?.let(::add)
                item.fabricImages.forEach { add(it.photoStoragePath) }
                item.styleImages.forEach { ref ->
                    ref.photoStoragePath?.let(::add)
                }
            }
        }
        .filter { it.isNotBlank() }
        .distinct()
