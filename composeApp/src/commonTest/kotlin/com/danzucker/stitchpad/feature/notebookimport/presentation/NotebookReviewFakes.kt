package com.danzucker.stitchpad.feature.notebookimport.presentation

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportJob
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.PageBox
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal const val REVIEW_OWNER = "owner-1"
internal const val REVIEW_JOB = "job-1"

internal data class FinishCall(val jobId: String, val abandoned: Boolean)

/** Behaves like the Firestore cache: updates and deletes show up on the observed cards at once. */
internal class FakeReviewRepository : NotebookImportRepository {
    val cards = MutableStateFlow<List<ReviewCard>>(emptyList())
    val pages = MutableStateFlow<List<ImportPage>>(emptyList())
    val job = MutableStateFlow<ImportJob?>(ImportJob(REVIEW_JOB, Instant.fromEpochSeconds(0), "open", 1, null))
    var imageUrlResult: Result<String, DataError.Network> = Result.Success("https://example.test/page.jpg")

    val updated = mutableListOf<ReviewCard>()
    val deleted = mutableListOf<String>()
    val finished = mutableListOf<FinishCall>()
    var deleteError: DataError.Network? = null

    override suspend fun createJob(ownerUid: String): Result<String, DataError.Network> = Result.Success(REVIEW_JOB)

    override suspend fun uploadPage(ownerUid: String, jobId: String, jpeg: ByteArray) = Result.Success("p1")

    override fun observePages(ownerUid: String, jobId: String): Flow<Result<List<ImportPage>, DataError.Network>> =
        pages.map { Result.Success(it) }

    override fun observeCards(ownerUid: String, jobId: String): Flow<Result<List<ReviewCard>, DataError.Network>> =
        cards.map { Result.Success(it) }

    override fun observeOpenJob(ownerUid: String): Flow<ImportJob?> = job

    override fun observeJob(ownerUid: String, jobId: String): Flow<ImportJob?> = job

    override suspend fun pageImageUrl(
        ownerUid: String,
        jobId: String,
        pageId: String,
    ): Result<String, DataError.Network> = imageUrlResult

    override suspend fun updateCard(ownerUid: String, card: ReviewCard): EmptyResult<DataError.Network> {
        updated += card
        cards.value = cards.value.map { if (it.id == card.id) card else it }
        return Result.Success(Unit)
    }

    override suspend fun deleteCard(ownerUid: String, jobId: String, cardId: String): EmptyResult<DataError.Network> {
        deleteError?.let { return Result.Error(it) }
        deleted += cardId
        cards.value = cards.value.filterNot { it.id == cardId }
        return Result.Success(Unit)
    }

    override suspend fun retryPage(jobId: String, pageId: String): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    override suspend fun finishJob(ownerUid: String, jobId: String, abandoned: Boolean): EmptyResult<DataError.Network> {
        finished += FinishCall(jobId, abandoned)
        return Result.Success(Unit)
    }

    fun lastUpdate(cardId: String): ReviewCard? = updated.lastOrNull { it.id == cardId }
}

/** Customer store for the saver: creates succeed until [capReached] is set. */
internal class FakeReviewCustomers : CustomerRepository {
    val customers = MutableStateFlow<List<Customer>>(emptyList())
    val created = mutableListOf<Customer>()
    var capReached = false

    override fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>> =
        customers.map { Result.Success(it) }

    override fun observeCustomer(userId: String, customerId: String): Flow<Result<Customer, DataError.Network>> =
        customers.map { list ->
            list.find { it.id == customerId }?.let { Result.Success(it) } ?: Result.Error(DataError.Network.NOT_FOUND)
        }

    override suspend fun getCustomer(userId: String, customerId: String): Result<Customer, DataError.Network> =
        customers.value.find { it.id == customerId }?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)

    override suspend fun createCustomer(userId: String, customer: Customer): EmptyResult<DataError.Network> {
        if (capReached) return Result.Error(DataError.Network.CAP_REACHED)
        created += customer
        customers.value = customers.value + customer
        return Result.Success(Unit)
    }

    override suspend fun updateCustomer(userId: String, customer: Customer): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    override suspend fun deleteCustomer(userId: String, customerId: String): EmptyResult<DataError.Network> =
        Result.Success(Unit)
}

internal class FakeUnitStore(private var unit: MeasurementUnit = MeasurementUnit.INCHES) : MeasurementPreferencesStore {
    override suspend fun getUnit(): MeasurementUnit = unit
    override suspend fun setUnit(unit: MeasurementUnit) {
        this.unit = unit
    }
}

internal class FakeReviewEntitlements(cap: Int = 15) : EntitlementsProvider {
    private val value = UserEntitlements(
        tier = SubscriptionTier.FREE,
        customerCap = cap,
        smartCoinAllowance = 5,
        isInWelcomeWindow = false,
        welcomeEndsAt = null,
        isWithinWelcomeEndingWarning = false,
        welcomeDaysLeft = null,
        canUseCustomMeasurements = false,
    )
    private val _flow = MutableStateFlow(value)
    override val flow: StateFlow<UserEntitlements> = _flow
    override fun current(): UserEntitlements = value
    override suspend fun awaitHydrated(): UserEntitlements = value
}

internal fun customer(id: String, name: String, phone: String = "") =
    Customer(id = id, userId = REVIEW_OWNER, name = name, phone = phone, createdAt = 0L)

@Suppress("LongParameterList")
internal fun reviewCard(
    id: String,
    name: String = "Name $id",
    pageId: String = "p1",
    status: CardStatus = CardStatus.NEW,
    needsReview: Boolean = false,
    gender: CustomerGender? = CustomerGender.FEMALE,
    values: List<CardValue> = listOf(CardValue("Bust", "38", "bust", false)),
    phone: String? = null,
    matched: String? = null,
    box: PageBox? = null,
    keepAsNew: Boolean = false,
) = ReviewCard(
    id = id,
    jobId = REVIEW_JOB,
    pageId = pageId,
    name = name,
    phone = phone,
    gender = gender,
    unit = MeasurementUnit.INCHES,
    dateWritten = null,
    values = values,
    box = box,
    needsReview = needsReview,
    status = status,
    matchedCustomerId = matched,
    savedCustomerId = null,
    keepAsNew = keepAsNew,
)

internal fun donePage(id: String) = ImportPage(id, PageStatus.DONE, 1, null, null)
