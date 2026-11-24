package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

enum class CardStatus { NEW, CHECKED, SAVED, SKIPPED }

enum class PageStatus { UPLOADING, PROCESSING, DONE, FAILED }

data class CardValue(
    val label: String,
    val value: String,
    val fieldKey: String?,
    val flagged: Boolean,
)

/** Normalised 0..1 rectangle of the page. Not range-checked: the server does not validate it. */
data class PageBox(val x: Float, val y: Float, val w: Float, val h: Float)

data class ReviewCard(
    val id: String,
    val jobId: String,
    val pageId: String,
    val name: String,
    val phone: String?,
    val gender: CustomerGender?,
    val unit: MeasurementUnit?,
    val dateWritten: LocalDate?,
    val values: List<CardValue>,
    val box: PageBox?,
    val needsReview: Boolean,
    val status: CardStatus,
    val matchedCustomerId: String?,
    val savedCustomerId: String?,
    /**
     * The tailor chose "Save as a new customer" for an existing-customer suggestion. A suggestion is
     * resolved only when [matchedCustomerId] is set or this is true.
     */
    val keepAsNew: Boolean = false,
)

/** [localThumb] is always null from the repository; the capture ViewModel fills it. */
data class ImportPage(
    val id: String,
    val status: PageStatus,
    val personCount: Int,
    val error: String?,
    val localThumb: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is ImportPage && id == other.id && status == other.status &&
            personCount == other.personCount && error == other.error &&
            localThumb.contentEquals(other.localThumb)

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + personCount
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (localThumb?.contentHashCode() ?: 0)
        return result
    }
}

/** [pageCount] is written only by the server. */
data class ImportJob(
    val id: String,
    val createdAt: Instant,
    val status: String,
    val pageCount: Int,
    val imagesDeletedAt: Instant?,
)
