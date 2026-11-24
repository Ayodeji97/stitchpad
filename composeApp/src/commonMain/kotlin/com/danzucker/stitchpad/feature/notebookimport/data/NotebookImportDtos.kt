package com.danzucker.stitchpad.feature.notebookimport.data

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportJob
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.PageBox
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CardValueDto(
    val label: String = "",
    val value: String = "",
    val fieldKey: String? = null,
    val flagged: Boolean = false,
)

@Serializable
data class PageBoxDto(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
)

/** Card doc `users/{uid}/notebookImports/{jobId}/cards/{cardId}`. Created server-side only. */
@Serializable
data class NotebookCardDto(
    val name: String = "",
    val phone: String? = null,
    val gender: String = "unknown",
    val unit: String = "unknown",
    val dateWritten: String? = null,
    val values: List<CardValueDto> = emptyList(),
    val box: PageBoxDto? = null,
    val needsReview: Boolean = false,
    val pageId: String = "",
    val status: String = "new",
    val matchedCustomerId: String? = null,
    val savedCustomerId: String? = null,
    val keepAsNew: Boolean = false,
)

@Serializable
data class NotebookPageDto(
    val status: String? = null,
    val personCount: Int = 0,
    val error: String? = null,
)

/** A null [createdAt] is a local write whose server timestamp has not resolved yet. */
@Serializable
data class NotebookJobDto(
    val createdAt: Timestamp? = null,
    val status: String = "open",
    val pageCount: Int = 0,
    val imagesDeletedAt: Timestamp? = null,
)

@Serializable
data class RetryNotebookPageRequest(val jobId: String, val pageId: String)

fun NotebookCardDto.toReviewCard(cardId: String, jobId: String): ReviewCard = ReviewCard(
    id = cardId,
    jobId = jobId,
    pageId = pageId,
    name = name,
    phone = phone,
    gender = when (gender) {
        "female" -> CustomerGender.FEMALE
        "male" -> CustomerGender.MALE
        else -> null
    },
    unit = when (unit) {
        "inches" -> MeasurementUnit.INCHES
        "cm" -> MeasurementUnit.CM
        else -> null
    },
    dateWritten = dateWritten?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    values = values.map { CardValue(it.label, it.value, it.fieldKey, it.flagged) },
    box = box?.let { PageBox(it.x, it.y, it.w, it.h) },
    needsReview = needsReview,
    status = when (status) {
        "checked" -> CardStatus.CHECKED
        "saved" -> CardStatus.SAVED
        "skipped" -> CardStatus.SKIPPED
        else -> CardStatus.NEW
    },
    matchedCustomerId = matchedCustomerId,
    savedCustomerId = savedCustomerId,
    keepAsNew = keepAsNew,
)

/** Wire form of [CardStatus]. Never produces 'uploading' style values; only the four the rules accept. */
fun CardStatus.toWire(): String = name.lowercase()

/**
 * Only the fields Firestore rules let the client change (plus `updatedAt`, added by the repository).
 * Anything else here would make the whole update denied.
 */
fun ReviewCard.toUpdateFields(): Map<String, Any?> = mapOf(
    "name" to name,
    "phone" to phone,
    "gender" to when (gender) {
        CustomerGender.FEMALE -> "female"
        CustomerGender.MALE -> "male"
        null -> "unknown"
    },
    "unit" to when (unit) {
        MeasurementUnit.INCHES -> "inches"
        MeasurementUnit.CM -> "cm"
        null -> "unknown"
    },
    "dateWritten" to dateWritten?.toString(),
    "values" to values.map {
        mapOf(
            "label" to it.label,
            "value" to it.value,
            "fieldKey" to it.fieldKey,
            "flagged" to it.flagged,
        )
    },
    "status" to status.toWire(),
    "matchedCustomerId" to matchedCustomerId,
    "savedCustomerId" to savedCustomerId,
    "keepAsNew" to keepAsNew,
)

fun NotebookPageDto.toImportPage(pageId: String): ImportPage = ImportPage(
    id = pageId,
    status = when (status) {
        "processing" -> PageStatus.PROCESSING
        "done" -> PageStatus.DONE
        "failed" -> PageStatus.FAILED
        else -> PageStatus.UPLOADING
    },
    personCount = personCount,
    error = error,
    localThumb = null,
)

fun NotebookJobDto.toImportJob(jobId: String): ImportJob = ImportJob(
    id = jobId,
    createdAt = createdAt.toInstantOrEpoch(),
    status = status,
    pageCount = pageCount,
    imagesDeletedAt = imagesDeletedAt?.let { Instant.fromEpochSeconds(it.seconds, it.nanoseconds) },
)

internal fun Timestamp?.toInstantOrEpoch(): Instant =
    this?.let { Instant.fromEpochSeconds(it.seconds, it.nanoseconds) } ?: Instant.fromEpochMilliseconds(0)
