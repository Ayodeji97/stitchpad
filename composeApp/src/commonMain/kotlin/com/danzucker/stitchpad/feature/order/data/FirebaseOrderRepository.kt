package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.decodeDocOrLog
import com.danzucker.stitchpad.core.data.dto.OrderCostDto
import com.danzucker.stitchpad.core.data.dto.OrderDto
import com.danzucker.stitchpad.core.data.dto.OrderMoneyDto
import com.danzucker.stitchpad.core.data.dto.PaymentDto
import com.danzucker.stitchpad.core.data.dto.StatusChangeDto
import com.danzucker.stitchpad.core.data.mapper.toOrder
import com.danzucker.stitchpad.core.data.mapper.toOrderBaseDto
import com.danzucker.stitchpad.core.data.mapper.toOrderCostDto
import com.danzucker.stitchpad.core.data.mapper.toOrderMoneyDto
import com.danzucker.stitchpad.core.data.mapper.toPaymentDto
import com.danzucker.stitchpad.core.data.mapper.withMoney
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderCost
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import com.danzucker.stitchpad.core.domain.model.Payment
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflinePhotoStore
import com.danzucker.stitchpad.core.offline.OfflineUploadJob
import com.danzucker.stitchpad.core.offline.OfflineUploadJobType
import com.danzucker.stitchpad.core.offline.OfflineUploadOutbox
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.feature.style.data.toStorageData
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "OrderRepo"
private const val LEGACY_DEPOSIT_PAYMENT_ID = "legacy-deposit"

/**
 * Stamps the authoritative Firestore *document* id onto a decoded [OrderDto].
 *
 * The `id` FIELD inside the document is only a mirror of the path and can be missing,
 * blank, or stale (hand-edited doc, partial import, a `set(..., merge = true)` written
 * before the field existed). Such a doc decodes as `id = ""`, and the Orders list keys
 * its LazyColumn rows by order id — so TWO id-less docs in one workshop threw
 * `IllegalArgumentException: Key "" was already used` and hard-crashed the whole screen
 * (found in device smoke testing). Blank ids also broke the `/private/money` join, which
 * is keyed by order id.
 *
 * Applied at every read/decode site so the DTO identity can never disagree with the path.
 * Pure + internal so the invariant is unit-testable without a Firestore fake.
 */
internal fun OrderDto.withDocumentId(docId: String): OrderDto =
    if (id == docId) this else copy(id = docId)

internal fun applyCompletedOrderUploadPatches(
    order: Order,
    completedUrlForStoragePath: (String) -> String?,
): Order = order.copy(
    items = order.items.map { item ->
        item.copy(
            styleImages = item.styleImages.map { ref ->
                val storagePath = ref.photoStoragePath
                val completedUrl = storagePath?.let(completedUrlForStoragePath)
                if (completedUrl != null) {
                    ref.copy(
                        photoUrl = completedUrl,
                        syncState = ImageSyncState.SYNCED,
                        localPhotoPath = null,
                    )
                } else {
                    ref
                }
            },
            fabricImages = item.fabricImages.map { ref ->
                val completedUrl = completedUrlForStoragePath(ref.photoStoragePath)
                if (completedUrl != null) {
                    ref.copy(
                        photoUrl = completedUrl,
                        syncState = ImageSyncState.SYNCED,
                        localPhotoPath = null,
                    )
                } else {
                    ref
                }
            },
        )
    },
)

internal fun paymentDtosForOfflineAppend(
    payment: Payment,
    knownPayments: List<Payment>,
): List<PaymentDto> =
    (knownPayments.filter { it.id == LEGACY_DEPOSIT_PAYMENT_ID } + payment)
        .distinctBy { it.id }
        .map { it.toPaymentDto() }

// gitlive's FieldValue.arrayUnion(vararg Any) does NOT run the kotlinx
// serializer over its elements — on iOS it hands them straight to native
// FIRFieldValue, which rejects Kotlin data classes ("Unsupported type:
// com.danzucker…") and hard-crashes. (set()/toOrderDto() are safe — they
// serialize the whole DTO via its @Serializable strategy.) arrayUnion elements
// must therefore be primitive maps. Keys MUST match the matching DTO's
// @Serializable field names so snapshot reads via OrderDto round-trip.
internal fun StatusChangeDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    "status" to status,
    "changedAt" to changedAt,
)

internal fun PaymentDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "amount" to amount,
    "method" to method,
    "type" to type,
    "recordedAt" to recordedAt,
    "note" to note,
)

internal fun OrderCostDto.toFirestoreMap(): Map<String, Any?> = mapOf(
    "category" to category,
    "amount" to amount,
    "note" to note,
)

/**
 * Write payload for [FirebaseOrderRepository.updateCosts] — costs only, deliberately
 * never `updatedAt`. Extracted as a pure helper so the "no updatedAt" invariant can be
 * asserted in commonTest without a Firestore fake (see [FirebaseOrderRepository.updateCosts]
 * KDoc for why bumping updatedAt here would be wrong).
 */
internal fun orderCostsWriteFields(costs: List<OrderCost>): Map<String, Any?> = mapOf(
    "costs" to costs.map { it.toOrderCostDto().toFirestoreMap() },
)

/**
 * Write payload for [FirebaseOrderRepository.recordPayment]'s BASE order-doc update
 * (Slice 8d-1, stop-dual-write). Only the non-sensitive `updatedAt`: `payments` and the
 * legacy `depositPaid` now live solely in `/private/money`, so the base doc stops
 * carrying money and a later strip can remove the base money fields for good. Extracted
 * as a pure helper so the "no money on the base payment write" invariant is asserted in
 * commonTest without a Firestore fake.
 */
internal fun orderPaymentBaseWriteFields(now: Long): Map<String, Any?> = mapOf(
    "updatedAt" to now,
)

@Suppress("TooManyFunctions")
class FirebaseOrderRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val offlineWrites: OfflineWriteDispatcher,
    private val photoStore: OfflinePhotoStore,
    private val uploadOutbox: OfflineUploadOutbox,
) : OrderRepository {

    private fun ordersCollection(userId: String) =
        firestore.collection("users").document(userId).collection("orders")

    // Owner-only money sub-doc (Owner + Staff feature). The owner reads money from
    // here (Slice 8a); staff are denied by Firestore rules. During the dual-write
    // window the base order doc still carries money too, so a legacy/seeded order
    // without this sub-doc falls back to the base (see [Order.withMoney]).
    private fun orderMoneyDoc(userId: String, orderId: String) =
        ordersCollection(userId).document(orderId).collection("private").document("money")

    // Slice 8a: one collection-group read of every money sub-doc the owner owns,
    // keyed by order id, so the LIST can join money without an N+1 per-order read.
    // Scoped by the `ownerId` field (a nested-path rule can't authorize a
    // collection-group query). If this read fails (e.g. the collection-group index
    // is still propagating), degrade to an empty map so orders fall back to base
    // money — never break the owner's money display over a sub-doc read.
    private fun moneyByOrderId(userId: String): Flow<Map<String, OrderMoneyDto>> =
        firestore.collectionGroup("private")
            .where { "ownerId" equalTo userId }
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .filter { it.id == "money" }
                    .mapNotNull { doc -> decodeDocOrLog(tag = TAG, docId = doc.id) { doc.data<OrderMoneyDto>() } }
                    .associateBy { it.orderId }
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) {
                    "observeOrders money collection-group failed; falling back to base money"
                }
                emit(emptyMap())
            }

    private fun orderMoneyFlow(userId: String, orderId: String): Flow<OrderMoneyDto?> =
        orderMoneyDoc(userId, orderId)
            .snapshots
            .map { snapshot ->
                if (snapshot.exists) {
                    decodeDocOrLog(tag = TAG, docId = orderId) { snapshot.data<OrderMoneyDto>() }
                } else {
                    null
                }
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) {
                    "observeOrder money sub-doc failed orderId=$orderId; falling back to base money"
                }
                emit(null)
            }

    private fun fabricStoragePath(userId: String, orderId: String, itemId: String): String =
        "users/$userId/orders/$orderId/fabrics/$itemId.jpg"

    /**
     * Multi-image path scheme. Uses a unique suffix per upload (not a positional
     * index) so that appending images to an order that already has saved images
     * never collides with their storage paths. codex P1 — without this, editing
     * an order with N saved images and adding one more would overwrite the
     * existing first image at `$itemId-0.jpg`.
     */
    private fun fabricStoragePath(userId: String, orderId: String, itemId: String, suffix: String): String =
        "users/$userId/orders/$orderId/fabrics/$itemId-$suffix.jpg"

    private fun styleStoragePath(userId: String, orderId: String, itemId: String): String =
        "users/$userId/orders/$orderId/styles/$itemId.jpg"

    /** See [fabricStoragePath] — same rationale for unique-suffix scheme. */
    private fun styleStoragePath(userId: String, orderId: String, itemId: String, suffix: String): String =
        "users/$userId/orders/$orderId/styles/$itemId-$suffix.jpg"

    private fun uploadJobId(type: OfflineUploadJobType, path: String): String =
        "$type:$path"

    private fun Order.withLocalPendingImages(): Order = copy(
        items = items.map { item ->
            item.copy(
                styleImages = item.styleImages.map { ref ->
                    if (ref.syncState == ImageSyncState.PENDING) {
                        ref.copy(
                            localPhotoPath = ref.photoStoragePath
                                ?.let(uploadOutbox::localPathForStoragePath)
                        )
                    } else {
                        ref
                    }
                },
                fabricImages = item.fabricImages.map { ref ->
                    if (ref.syncState == ImageSyncState.PENDING) {
                        ref.copy(localPhotoPath = uploadOutbox.localPathForStoragePath(ref.photoStoragePath))
                    } else {
                        ref
                    }
                },
            )
        },
    )

    private fun Order.withCompletedUploadPatches(): Order = copy(
        items = applyCompletedOrderUploadPatches(
            order = this,
            completedUrlForStoragePath = uploadOutbox::completedUrlForStoragePath,
        ).items,
    )

    // Parse every order doc in a snapshot. Archive partitioning happens client-side:
    // the GitLive SDK doesn't support `whereEqualTo(field, null)` cleanly across
    // platforms, and the per-user dataset is small enough (< 1k orders) that
    // filtering in memory is fine.
    private fun List<dev.gitlive.firebase.firestore.DocumentSnapshot>.toOrders(
        userId: String,
    ): List<Order> = mapNotNull { doc ->
        decodeDocOrLog(tag = TAG, docId = doc.id) {
            doc.data<OrderDto>()
                .withDocumentId(doc.id)
                .toOrder(userId)
                .withLocalPendingImages()
        }
    }

    override fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>> =
        combine(ordersCollection(userId).snapshots(), moneyByOrderId(userId)) { snapshot, money ->
            val orders = snapshot.documents.toOrders(userId)
                .filter { it.archivedAt == null }
                .map { it.withMoney(money[it.id]) }
            Result.Success(orders) as Result<List<Order>, DataError.Network>
        }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeOrders failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override fun observeArchivedOrders(
        userId: String,
    ): Flow<Result<List<Order>, DataError.Network>> =
        combine(ordersCollection(userId).snapshots(), moneyByOrderId(userId)) { snapshot, money ->
            val archived = snapshot.documents.toOrders(userId)
                .filter { it.archivedAt != null }
                .sortedByDescending { it.archivedAt }
                .map { it.withMoney(money[it.id]) }
            Result.Success(archived) as Result<List<Order>, DataError.Network>
        }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeArchivedOrders failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override fun observeOrder(
        userId: String,
        orderId: String
    ): Flow<Result<Order, DataError.Network>> =
        combine(
            ordersCollection(userId).document(orderId).snapshots,
            orderMoneyFlow(userId, orderId),
        ) { snapshot, money ->
            if (!snapshot.exists) {
                Result.Error(DataError.Network.NOT_FOUND) as Result<Order, DataError.Network>
            } else {
                val dto = snapshot.data<OrderDto>().withDocumentId(snapshot.id)
                Result.Success(
                    dto.toOrder(userId).withLocalPendingImages().withMoney(money),
                ) as Result<Order, DataError.Network>
            }
        }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeOrder failed orderId=$orderId" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun getOrder(
        userId: String,
        orderId: String
    ): Result<Order, DataError.Network> {
        return try {
            val doc = ordersCollection(userId).document(orderId).get()
            if (!doc.exists) return Result.Error(DataError.Network.NOT_FOUND)
            val dto = doc.data<OrderDto>().withDocumentId(doc.id)
            // Prefer the owner-only money sub-doc; fall back to the base doc's money
            // when it's missing (legacy/seeded order) or the read fails.
            val money = runCatching {
                val moneyDoc = orderMoneyDoc(userId, orderId).get()
                if (moneyDoc.exists) moneyDoc.data<OrderMoneyDto>() else null
            }.getOrNull()
            Result.Success(dto.toOrder(userId).withLocalPendingImages().withMoney(money))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "getOrder failed orderId=$orderId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun createOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        val docRef = if (order.id.isBlank()) {
            ordersCollection(userId).document
        } else {
            ordersCollection(userId).document(order.id)
        }
        // Slice 8d-1 (stop-dual-write): the base doc is money-free; money goes only to
        // the /private/money sub-doc below via toOrderMoneyDto.
        val dto = order.withCompletedUploadPatches().toOrderBaseDto().copy(id = docRef.id)
        val accepted = offlineWrites.enqueue("createOrder orderId=${docRef.id}") {
            docRef.set(dto)
            docRef.set(mapOf("serverCreatedAt" to FieldValue.serverTimestamp), merge = true)
            // orderId comes from docRef (order.id may be blank for a new order).
            orderMoneyDoc(userId, docRef.id).set(order.toOrderMoneyDto(userId).copy(orderId = docRef.id))
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateOrder(
        userId: String,
        order: Order
    ): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("updateOrder orderId=${order.id}") {
            // merge = true so the server-stamped `serverCreatedAt` survives the edit.
            // A replacement write drops every field absent from the DTO, and the
            // rules require an already-stamped doc to keep its stamp — so a
            // replacement here is rejected with permission-denied. GitLive encodes
            // defaults, so every DTO field (nulls and zeros included) is still
            // written and field-clearing behaves exactly as before.
            //
            // Slice 8d-1 (stop-dual-write): base write is money-free (toOrderBaseDto).
            // With merge = true the base doc's pre-existing money fields (on a legacy
            // doc written before 8d-1) are simply left untouched — never refreshed —
            // and the full money is re-mirrored to /private/money below.
            ordersCollection(userId).document(order.id)
                .set(order.withCompletedUploadPatches().toOrderBaseDto(), merge = true)
            // Full-money mirror; order carries the current money so a replace is correct.
            orderMoneyDoc(userId, order.id).set(order.toOrderMoneyDto(userId))
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val change = StatusChangeDto(status = newStatus.name, changedAt = now)
        val accepted = offlineWrites.enqueue("updateOrderStatus orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "status" to newStatus.name,
                "statusHistory" to FieldValue.arrayUnion(change.toFirestoreMap()),
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    @Suppress("SpreadOperator") // GitLive's update() only accepts vararg Pair<String, Any?>;
    // spreading orderPaymentBaseWriteFields' map keeps the tested helper as the actual write path.
    override suspend fun recordPayment(
        userId: String,
        orderId: String,
        payment: Payment,
        knownPayments: List<Payment>,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val stampedPayment = payment.copy(recordedAt = payment.recordedAt.takeIf { it > 0L } ?: now)
        val paymentsToAppend = paymentDtosForOfflineAppend(stampedPayment, knownPayments)
            .map { it.toFirestoreMap() }
        val paymentsArrayUnion = if (paymentsToAppend.size == 1) {
            FieldValue.arrayUnion(paymentsToAppend.first())
        } else {
            FieldValue.arrayUnion(paymentsToAppend[0], paymentsToAppend[1])
        }
        val accepted = offlineWrites.enqueue("recordPayment orderId=$orderId paymentId=${payment.id}") {
            // Slice 8d-1 (stop-dual-write): the base doc no longer carries money —
            // only bump updatedAt. `payments` (and the legacy `depositPaid`) live
            // solely in /private/money now (the mirror below).
            ordersCollection(userId).document(orderId).update(
                *orderPaymentBaseWriteFields(now).entries.map { it.key to it.value }.toTypedArray(),
            )
            // Mirror the payment append to the money sub-doc. merge=true so it
            // create-or-updates even for orders whose money doc isn't backfilled yet.
            // Partial mirror: payments only. Deliberately does NOT stamp ownerId — that
            // is the completeness sentinel (see Order.withMoney). Stamping it here would
            // make an incomplete doc (created when no full mirror has run yet for a
            // legacy order) look authoritative and zero out totalPrice/discount/costs on
            // read. The full write (create/update) or the backfill owns the ownerId stamp.
            orderMoneyDoc(userId, orderId).set(mapOf("payments" to paymentsArrayUnion), merge = true)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateSubStatus(
        userId: String,
        orderId: String,
        subStatus: OrderSubStatus?,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("updateSubStatus orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "subStatus" to (subStatus?.name ?: FieldValue.delete),
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateNotes(
        userId: String,
        orderId: String,
        notes: String?,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("updateNotes orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "notes" to (notes ?: FieldValue.delete),
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    /**
     * Updates an order's cost lines only.
     *
     * Deliberately does NOT bump `updatedAt` (unlike [recordPayment]/[updateNotes]/
     * [updateSubStatus]). Costs are private analytics data the tailor may backfill onto an
     * old, already-delivered order long after the fact. Reports buckets every order into its
     * reporting window by `updatedAt` ([com.danzucker.stitchpad.feature.reports.domain.KpiCalculator]),
     * so bumping it here would yank that old order into the CURRENT window and inflate this
     * period's Revenue/Collected/Outstanding/Orders/Profit — editing a private field would
     * corrupt a public report. See [orderCostsWriteFields] for the write payload this
     * invariant is guarded by in commonTest.
     */
    override suspend fun updateCosts(
        userId: String,
        orderId: String,
        costs: List<OrderCost>,
    ): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("updateCosts orderId=$orderId") {
            // Slice 8d-1 (stop-dual-write): costs are money — they live solely in
            // /private/money now, so the base order doc gets NO cost write at all (it
            // never bumped updatedAt either — see the KDoc — so nothing else is lost).
            // Partial mirror: costs only. No ownerId stamp — same completeness-sentinel
            // reasoning as recordPayment above (see Order.withMoney).
            orderMoneyDoc(userId, orderId).set(orderCostsWriteFields(costs), merge = true)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun archiveOrder(
        userId: String,
        orderId: String,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        val accepted = offlineWrites.enqueue("archiveOrder orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "archivedAt" to now,
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun unarchiveOrder(
        userId: String,
        orderId: String,
    ): EmptyResult<DataError.Network> {
        val now = Clock.System.now().toEpochMilliseconds()
        // Clear archivedAt (delete the field) so the order rejoins observeOrders.
        // Mirrors updateNotes/updateSubStatus's FieldValue.delete null-clear pattern.
        val accepted = offlineWrites.enqueue("unarchiveOrder orderId=$orderId") {
            ordersCollection(userId).document(orderId).update(
                "archivedAt" to FieldValue.delete,
                "updatedAt" to now,
            )
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun deleteOrder(
        userId: String,
        orderId: String,
        ownedStoragePaths: List<String>,
    ): EmptyResult<DataError.Network> {
        ownedStoragePaths.forEach { path ->
            runCatching { deleteOrderImagePath(path) }
                .onFailure { throwable ->
                    AppLogger.w(tag = TAG, throwable = throwable) {
                        "deleteOrder image cleanup enqueue failed path=$path"
                    }
                }
        }
        val accepted = offlineWrites.enqueue("deleteOrder orderId=$orderId") {
            ordersCollection(userId).document(orderId).delete()
            orderMoneyDoc(userId, orderId).delete()
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    private suspend fun deleteOrderImagePath(storagePath: String) {
        cancelPendingOrderImageUploadAndDeleteStorage(storagePath)
    }

    private suspend fun cancelPendingOrderImageUploadAndDeleteStorage(storagePath: String) {
        uploadOutbox.cancel(uploadJobId(OfflineUploadJobType.ORDER_FABRIC_IMAGE, storagePath))
        uploadOutbox.cancel(uploadJobId(OfflineUploadJobType.ORDER_STYLE_IMAGE, storagePath))
        uploadOutbox.enqueue(
            OfflineUploadJob(
                id = uploadJobId(OfflineUploadJobType.STORAGE_DELETE, storagePath),
                type = OfflineUploadJobType.STORAGE_DELETE,
                userId = "",
                storagePath = storagePath,
            )
        )
    }

    override fun newOrderId(userId: String): String =
        ordersCollection(userId).document.id

    override suspend fun uploadFabricPhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network> {
        val path = fabricStoragePath(userId, orderId, itemId)
        return try {
            storage.reference.child(path).putData(photoBytes.toStorageData())
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            Result.Success(downloadUrl to path)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "uploadFabricPhoto failed itemId=$itemId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun uploadStylePhoto(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytes: ByteArray
    ): Result<Pair<String, String>, DataError.Network> {
        val path = styleStoragePath(userId, orderId, itemId)
        return try {
            storage.reference.child(path).putData(photoBytes.toStorageData())
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            Result.Success(downloadUrl to path)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "uploadStylePhoto failed itemId=$itemId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("ReturnCount")
    override suspend fun uploadFabricPhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        return uploadPhotos(
            userId = userId,
            orderId = orderId,
            itemId = itemId,
            photoBytesList = photoBytesList,
            operationName = "uploadFabricPhotos",
            jobType = OfflineUploadJobType.ORDER_FABRIC_IMAGE,
        ) { suffix ->
            fabricStoragePath(userId, orderId, itemId, suffix)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("ReturnCount")
    override suspend fun uploadStylePhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        return uploadPhotos(
            userId = userId,
            orderId = orderId,
            itemId = itemId,
            photoBytesList = photoBytesList,
            operationName = "uploadStylePhotos",
            jobType = OfflineUploadJobType.ORDER_STYLE_IMAGE,
        ) { suffix ->
            styleStoragePath(userId, orderId, itemId, suffix)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("ReturnCount")
    private suspend fun uploadPhotos(
        userId: String,
        orderId: String,
        itemId: String,
        photoBytesList: List<ByteArray>,
        operationName: String,
        jobType: OfflineUploadJobType,
        storagePath: (suffix: String) -> String,
    ): Result<List<Pair<String, String>>, DataError.Network> {
        if (photoBytesList.isEmpty()) return Result.Success(emptyList())
        val results = mutableListOf<Pair<String, String>>()
        photoBytesList.forEach { bytes ->
            // Unique suffix per upload — see fabricStoragePath() docs. Positional
            // index would collide when editing an order that already has saved
            // images and appending one more.
            val suffix = Uuid.random().toString().take(8)
            val path = storagePath(suffix)
            try {
                val localPath = photoStore.save(bytes, "$operationName-$suffix.jpg")
                results += localPath to path
                uploadOutbox.enqueue(
                    OfflineUploadJob(
                        id = uploadJobId(jobType, path),
                        type = jobType,
                        userId = userId,
                        storagePath = path,
                        localPath = localPath,
                        orderId = orderId,
                        itemId = itemId,
                    )
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AppLogger.e(tag = TAG, throwable = e) { "$operationName failed itemId=$itemId suffix=$suffix" }
                return Result.Error(DataError.Network.UNKNOWN)
            }
        }
        return Result.Success(results)
    }

    suspend fun deleteFabricPhoto(storagePath: String) {
        runCatching {
            deleteOrderImagePath(storagePath)
        }
            .onFailure { throwable ->
                AppLogger.w(tag = TAG, throwable = throwable) { "deleteFabricPhoto failed" }
            }
    }

    override suspend fun deleteStoragePaths(
        paths: List<String>,
    ): EmptyResult<DataError.Network> {
        paths.filter { it.isNotBlank() }.forEach { path ->
            runCatching {
                cancelPendingOrderImageUploadAndDeleteStorage(path)
            }.onFailure { throwable ->
                AppLogger.w(tag = TAG, throwable = throwable) {
                    "deleteStoragePaths cleanup enqueue failed path=$path"
                }
            }
        }
        return Result.Success(Unit)
    }
}
