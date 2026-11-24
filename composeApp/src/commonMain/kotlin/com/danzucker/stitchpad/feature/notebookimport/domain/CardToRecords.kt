@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.sharing.customerPhoneForStorage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock

data class RecordsToSave(val customer: Customer?, val measurement: Measurement)

fun importCustomerId(jobId: String, cardId: String): String = "nb_${jobId}_$cardId"

fun importMeasurementId(jobId: String, cardId: String): String = "nbm_${jobId}_$cardId"

/**
 * Builds the records for one reviewed card. The review UI forces a gender choice, so
 * [ReviewCard.gender] must be non-null. [country] formats the phone exactly as the customer form does.
 */
fun cardToRecords(
    card: ReviewCard,
    ownerUid: String,
    existingCustomerId: String?,
    importDate: LocalDate,
    defaultUnit: MeasurementUnit,
    country: Country,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): RecordsToSave {
    val gender = requireNotNull(card.gender) { "Card ${card.id} has no gender; review must force a choice" }
    val customerId = existingCustomerId ?: importCustomerId(card.jobId, card.id)

    val fields = linkedMapOf<String, String>()
    val notes = mutableListOf<String>()
    for (v in card.values) {
        val key = v.fieldKey
        if (key != null && key !in fields) {
            fields[key] = v.value
        } else {
            notes.add("${v.label}: ${v.value}")
        }
    }

    val customer = if (existingCustomerId != null) {
        null
    } else {
        Customer(
            id = customerId,
            userId = ownerUid,
            name = card.name.trim(),
            phone = customerPhoneForStorage(card.phone.orEmpty(), country),
            createdAt = nowMillis,
        )
    }
    val measurement = Measurement(
        id = importMeasurementId(card.jobId, card.id),
        customerId = customerId,
        gender = gender,
        fields = fields,
        unit = card.unit ?: defaultUnit,
        notes = notes.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        dateTaken = (card.dateWritten ?: importDate).atNoonUtc(),
        createdAt = nowMillis,
    )
    return RecordsToSave(customer, measurement)
}

/** Noon UTC keeps the same calendar day in every timezone from UTC-11 to UTC+11. */
private fun LocalDate.atNoonUtc(): Long =
    LocalDateTime(year, month, day, NOON_HOUR, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

private const val NOON_HOUR = 12
