package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

private class SaverCustomers : CustomerRepository {
    val created = mutableMapOf<String, Customer>()
    var createCalls = 0
    var capAtCreateCall: Int? = null
    var errorOnCreate: DataError.Network? = null
    var errorOnGet: DataError.Network? = null

    override fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>> = emptyFlow()
    override fun observeCustomer(userId: String, customerId: String): Flow<Result<Customer, DataError.Network>> =
        emptyFlow()

    override suspend fun getCustomer(userId: String, customerId: String): Result<Customer, DataError.Network> =
        errorOnGet?.let { Result.Error(it) }
            ?: created[customerId]?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)

    override suspend fun createCustomer(userId: String, customer: Customer): EmptyResult<DataError.Network> {
        createCalls++
        errorOnCreate?.let { return Result.Error(it) }
        if (capAtCreateCall == createCalls) return Result.Error(DataError.Network.CAP_REACHED)
        created[customer.id] = customer
        return Result.Success(Unit)
    }

    override suspend fun updateCustomer(userId: String, customer: Customer): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    override suspend fun deleteCustomer(userId: String, customerId: String): EmptyResult<DataError.Network> =
        Result.Success(Unit)
}

private class SaverMeasurements : MeasurementRepository {
    val created = mutableMapOf<String, Pair<String, Measurement>>()

    override fun observeMeasurements(
        userId: String,
        customerId: String,
    ): Flow<Result<List<Measurement>, DataError.Network>> = emptyFlow()

    override suspend fun createMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement,
    ): EmptyResult<DataError.Network> {
        created[measurement.id] = customerId to measurement
        return Result.Success(Unit)
    }

    override suspend fun updateMeasurement(
        userId: String,
        customerId: String,
        measurement: Measurement,
    ): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun deleteMeasurement(
        userId: String,
        customerId: String,
        measurementId: String,
    ): EmptyResult<DataError.Network> = Result.Success(Unit)
}

private class SaverImportRepo : NotebookImportRepository {
    val updated = mutableListOf<ReviewCard>()
    var failUpdatesRemaining = 0

    override suspend fun createJob(ownerUid: String): Result<String, DataError.Network> = Result.Success("job1")
    override suspend fun uploadPage(ownerUid: String, jobId: String, jpeg: ByteArray) = Result.Success("p001_x")
    override fun observePages(ownerUid: String, jobId: String): Flow<Result<List<ImportPage>, DataError.Network>> =
        emptyFlow()

    override fun observeCards(ownerUid: String, jobId: String): Flow<Result<List<ReviewCard>, DataError.Network>> =
        emptyFlow()

    override fun observeOpenJob(ownerUid: String): Flow<ImportJob?> = emptyFlow()

    override suspend fun updateCard(ownerUid: String, card: ReviewCard): EmptyResult<DataError.Network> {
        if (failUpdatesRemaining > 0) {
            failUpdatesRemaining--
            return Result.Error(DataError.Network.NO_INTERNET)
        }
        updated += card
        return Result.Success(Unit)
    }

    override suspend fun deleteCard(ownerUid: String, jobId: String, cardId: String): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    override suspend fun retryPage(jobId: String, pageId: String): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    override suspend fun finishJob(ownerUid: String, jobId: String, abandoned: Boolean): EmptyResult<DataError.Network> =
        Result.Success(Unit)
}

class NotebookImportSaverTest {
    private val customers = SaverCustomers()
    private val measurements = SaverMeasurements()
    private val importRepo = SaverImportRepo()
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
    }
    private val saver = NotebookImportSaver(customers, measurements, importRepo, clock)

    private fun card(id: String, matched: String? = null, saved: String? = null, status: CardStatus = CardStatus.CHECKED) =
        ReviewCard(
            id = id, jobId = "job1", pageId = "p1", name = "Name $id", phone = null,
            gender = CustomerGender.FEMALE, unit = MeasurementUnit.INCHES, dateWritten = null,
            values = listOf(CardValue("Bust", "36", "bust", false)), box = null, needsReview = false,
            status = status, matchedCustomerId = matched, savedCustomerId = saved,
        )

    private suspend fun save(cards: List<ReviewCard>) =
        saver.save("u1", cards, MeasurementUnit.INCHES, Country.DEFAULT)

    @Test
    fun save_newAndMatchedCards() = runTest {
        val outcome = save(listOf(card("c1"), card("c2", matched = "existing1")))

        assertEquals(SaveOutcome.Done(2), outcome)
        assertEquals(setOf("nb_job1_c1"), customers.created.keys)
        assertEquals("nb_job1_c1", measurements.created.getValue("nbm_job1_c1").first)
        assertEquals("existing1", measurements.created.getValue("nbm_job1_c2").first)
        assertEquals(listOf("nb_job1_c1", "existing1"), importRepo.updated.map { it.savedCustomerId })
        assertEquals(listOf(CardStatus.SAVED, CardStatus.SAVED), importRepo.updated.map { it.status })
    }

    @Test
    fun capReached_stopsAndKeepsRest() = runTest {
        customers.capAtCreateCall = 3
        val cards = (1..5).map { card("c$it") }

        val outcome = save(cards)

        assertEquals(SaveOutcome.StoppedAtCap(savedCount = 2, remaining = 3), outcome)
        assertEquals(setOf("nb_job1_c1", "nb_job1_c2"), customers.created.keys)
        assertEquals(setOf("nbm_job1_c1", "nbm_job1_c2"), measurements.created.keys)
        assertEquals(2, importRepo.updated.size)
    }

    @Test
    fun saveRetry_sameIdsNoDuplicate() = runTest {
        importRepo.failUpdatesRemaining = 1
        val cards = listOf(card("c1"))

        val first = save(cards)
        assertIs<SaveOutcome.Failed>(first)
        assertEquals(1, customers.created.size)

        val second = save(cards)

        assertEquals(SaveOutcome.Done(1), second)
        assertEquals(1, customers.created.size)
        assertEquals(1, customers.createCalls, "retry must not re-create a customer it already created")
        assertEquals(1, measurements.created.size)
        assertEquals("nb_job1_c1", importRepo.updated.single().savedCustomerId)
    }

    @Test
    fun skipsSavedCards() = runTest {
        val outcome = save(
            listOf(
                card("c1", status = CardStatus.SAVED, saved = "nb_job1_c1"),
                card("c2", saved = "somebody"),
                card("c3"),
            ),
        )

        assertEquals(SaveOutcome.Done(1), outcome)
        assertEquals(setOf("nb_job1_c3"), customers.created.keys)
        assertEquals(setOf("nbm_job1_c3"), measurements.created.keys)
    }

    @Test
    fun failed_otherError() = runTest {
        customers.errorOnCreate = DataError.Network.NO_INTERNET

        val outcome = save(listOf(card("c1"), card("c2")))

        assertEquals(SaveOutcome.Failed(0, DataError.Network.NO_INTERNET), outcome)
        assertEquals(1, customers.createCalls)
        assertEquals(0, measurements.created.size)
    }

    @Test
    fun matchedCard_alwaysCreatesNewMeasurementAndNoCustomer() = runTest {
        save(listOf(card("c1", matched = "existing1")))

        assertEquals(0, customers.createCalls)
        assertEquals(1, measurements.created.size)
    }

    @Test
    fun preCheckReadError_stopsWithoutCreating() = runTest {
        customers.errorOnGet = DataError.Network.NO_INTERNET

        val outcome = save(listOf(card("c1"), card("c2")))

        assertEquals(SaveOutcome.Failed(0, DataError.Network.NO_INTERNET), outcome)
        assertEquals(0, customers.createCalls)
        assertEquals(0, measurements.created.size)
    }

    @Test
    fun skippedCards_areLeftAlone() = runTest {
        val outcome = save(listOf(card("c1", status = CardStatus.SKIPPED), card("c2")))

        assertEquals(SaveOutcome.Done(1), outcome)
        assertEquals(setOf("nb_job1_c2"), customers.created.keys)
    }

    @Test
    fun nullGender_leftUnsavedCountedInRemainingOthersContinue() = runTest {
        customers.capAtCreateCall = 2
        val cards = listOf(
            card("c1").copy(gender = null),
            card("c2"),
            card("c3"),
            card("c4"),
        )

        val outcome = save(cards)

        // c2 saved; c3 hits the cap; remaining = c3, c4 (the null-gender c1 is before the cap)
        assertEquals(SaveOutcome.StoppedAtCap(savedCount = 1, remaining = 2), outcome)
        assertEquals(setOf("nb_job1_c2"), customers.created.keys)
    }

    @Test
    fun nullGender_doesNotFailTheRun() = runTest {
        val outcome = save(listOf(card("c1").copy(gender = null), card("c2")))

        assertEquals(SaveOutcome.Done(1), outcome)
        assertEquals(setOf("nb_job1_c2"), customers.created.keys)
    }

    @Test
    fun capRemaining_countsLaterNullGenderCards() = runTest {
        customers.capAtCreateCall = 1
        val outcome = save(listOf(card("c1"), card("c2").copy(gender = null), card("c3")))

        assertEquals(SaveOutcome.StoppedAtCap(savedCount = 0, remaining = 3), outcome)
    }
}
