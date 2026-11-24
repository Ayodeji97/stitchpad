package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
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
    /** [blockedCardIds]: cards the caller has not cleared for saving; they are skipped, never saved. */
    suspend fun save(
        ownerUid: String,
        cards: List<ReviewCard>,
        defaultUnit: MeasurementUnit,
        country: Country,
        blockedCardIds: Set<String> = emptySet(),
    ): SaveOutcome {
        val nowMillis = clock.now().toEpochMilliseconds()
        val importDate = Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        var saved = 0
        for ((index, card) in cards.withIndex()) {
            // Review forces a gender; a card without one is not saveable. Neither is a card the caller
            // still blocks (an unanswered match or same-person prompt). Such a card stays unsaved (and
            // counts as remaining) while the others continue.
            if (card.isNotForSaving() || card.gender == null || card.id in blockedCardIds) continue
            val failure = saveOne(ownerUid, card, defaultUnit, country, importDate, nowMillis, saved) {
                cards.drop(index).count { !it.isNotForSaving() }
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
        if (customer != null) {
            val existing = customerRepository.getCustomer(ownerUid, customer.id)
            // Only a definite NOT_FOUND means "create it"; any other read error stops the run
            // rather than risk a second create.
            if (existing is Result.Error) {
                if (existing.error != DataError.Network.NOT_FOUND) return SaveOutcome.Failed(saved, existing.error)
                createCustomer(ownerUid, customer, saved, remainingFromHere)?.let { return it }
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

    private fun ReviewCard.isNotForSaving() =
        status == CardStatus.SAVED || status == CardStatus.SKIPPED || savedCustomerId != null

    private suspend fun createCustomer(
        ownerUid: String,
        customer: Customer,
        saved: Int,
        remainingFromHere: () -> Int,
    ): SaveOutcome? {
        val created = customerRepository.createCustomer(ownerUid, customer)
        if (created !is Result.Error) return null
        return if (created.error == DataError.Network.CAP_REACHED) {
            SaveOutcome.StoppedAtCap(saved, remainingFromHere())
        } else {
            SaveOutcome.Failed(saved, created.error)
        }
    }
}
