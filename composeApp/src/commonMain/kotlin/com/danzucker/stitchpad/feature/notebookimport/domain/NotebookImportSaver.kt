package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

sealed interface SaveOutcome {
    data class Done(val savedCount: Int) : SaveOutcome
    data class StoppedAtCap(val savedCount: Int, val remaining: Int) : SaveOutcome
    data class Failed(val savedCount: Int, val error: DataError.Network) : SaveOutcome
}

/**
 * Saves reviewed cards one at a time. Ids are deterministic (see [importCustomerId] and
 * [importMeasurementId]) so a retried save rewrites the same documents instead of duplicating.
 * A card saves into [ReviewCard.matchedCustomerId] only; that field is written solely by the
 * review toggle when the owner confirms a match, never by the server or the matcher.
 * Nothing here logs names, phones or values.
 */
class NotebookImportSaver(
    private val customerRepository: CustomerRepository,
    private val measurementRepository: MeasurementRepository,
    private val importRepository: NotebookImportRepository,
    private val clock: Clock,
) {
    suspend fun save(
        ownerUid: String,
        cards: List<ReviewCard>,
        defaultUnit: MeasurementUnit,
        country: Country,
    ): SaveOutcome {
        val nowMillis = clock.now().toEpochMilliseconds()
        val importDate = Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        var saved = 0
        for ((index, card) in cards.withIndex()) {
            if (card.isAlreadySaved()) continue
            val failure = saveOne(ownerUid, card, defaultUnit, country, importDate, nowMillis, saved) {
                cards.drop(index).count { !it.isAlreadySaved() }
            }
            if (failure != null) return failure
            saved++
        }
        return SaveOutcome.Done(saved)
    }

    /** Returns null on success, otherwise the outcome that stops the run. */
    @Suppress("LongParameterList", "ReturnCount")
    private suspend fun saveOne(
        ownerUid: String,
        card: ReviewCard,
        defaultUnit: MeasurementUnit,
        country: Country,
        importDate: LocalDate,
        nowMillis: Long,
        saved: Int,
        remainingFromHere: () -> Int,
    ): SaveOutcome? {
        if (card.gender == null) return SaveOutcome.Failed(saved, DataError.Network.UNKNOWN)
        val records = cardToRecords(
            card = card,
            ownerUid = ownerUid,
            existingCustomerId = card.matchedCustomerId,
            importDate = importDate,
            defaultUnit = defaultUnit,
            country = country,
            nowMillis = nowMillis,
        )
        val customer = records.customer
        if (customer != null && !customerExists(ownerUid, customer.id)) {
            val created = customerRepository.createCustomer(ownerUid, customer)
            if (created is Result.Error) {
                return if (created.error == DataError.Network.CAP_REACHED) {
                    SaveOutcome.StoppedAtCap(saved, remainingFromHere())
                } else {
                    SaveOutcome.Failed(saved, created.error)
                }
            }
        }
        val customerId = records.measurement.customerId
        val measured = measurementRepository.createMeasurement(ownerUid, customerId, records.measurement)
        if (measured is Result.Error) return SaveOutcome.Failed(saved, measured.error)

        val marked = importRepository.updateCard(
            ownerUid,
            card.copy(status = CardStatus.SAVED, savedCustomerId = customerId),
        )
        return if (marked is Result.Error) SaveOutcome.Failed(saved, marked.error) else null
    }

    private fun ReviewCard.isAlreadySaved() = status == CardStatus.SAVED || savedCustomerId != null

    /**
     * A retry after a failed card update finds the customer already created. Skipping the create
     * keeps the free-tier cap check from rejecting a customer this import already added.
     * Any read error falls through to the (idempotent, same-id) create.
     */
    private suspend fun customerExists(ownerUid: String, customerId: String): Boolean =
        customerRepository.getCustomer(ownerUid, customerId) is Result.Success
}
