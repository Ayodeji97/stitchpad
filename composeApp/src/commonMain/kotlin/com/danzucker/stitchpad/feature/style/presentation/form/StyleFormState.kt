package com.danzucker.stitchpad.feature.style.presentation.form

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapInfo

/** Hard ceiling on how many photos can be multi-picked in one batch (UX guard). */
internal const val STYLE_MULTI_PICK_CEILING = 10

data class StyleFormState(
    val description: String = "",
    /**
     * Photos picked for this save. Empty until the user picks. In edit mode and
     * the order-link flow this holds at most one entry; in closet add mode
     * ([allowMultiPhoto]) it can hold several (PTSP-36).
     */
    val selectedPhotos: List<ByteArray> = emptyList(),
    val existingStyle: Style? = null,
    val isEditMode: Boolean = false,
    /**
     * True only when adding to a customer's closet (not editing, not linking a
     * style to an order). Gates multi-image selection — a single style is
     * replaced one photo at a time, and the order-link flow attaches exactly one.
     */
    val allowMultiPhoto: Boolean = false,
    /**
     * How many photos the picker may select in one batch — the folder's remaining
     * capacity (cap − current count), clamped to [1, STYLE_MULTI_PICK_CEILING]. A
     * Pro user in a 5-image folder that already holds 2 can pick at most 3.
     */
    val maxPhotoSelection: Int = STYLE_MULTI_PICK_CEILING,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null,
    /** Non-null while the cap-reached upgrade sheet should be shown. */
    val capSheet: StyleCapInfo? = null,
    /**
     * True when the user is on a Free tier viewing a style that belongs to a
     * paid-tier feature. The form is read-only: save is intercepted and redirects
     * to the upgrade flow instead of persisting.
     */
    val readOnly: Boolean = false,
) {
    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StyleFormState) return false
        if (description != other.description) return false
        if (!selectedPhotos.contentEqualsSafe(other.selectedPhotos)) return false
        if (existingStyle != other.existingStyle) return false
        if (isEditMode != other.isEditMode) return false
        if (allowMultiPhoto != other.allowMultiPhoto) return false
        if (maxPhotoSelection != other.maxPhotoSelection) return false
        if (isLoading != other.isLoading) return false
        if (isSaving != other.isSaving) return false
        if (errorMessage != other.errorMessage) return false
        if (capSheet != other.capSheet) return false
        if (readOnly != other.readOnly) return false
        return true
    }

    override fun hashCode(): Int {
        var result = description.hashCode()
        result = 31 * result + selectedPhotos.fold(0) { acc, bytes -> 31 * acc + bytes.contentHashCode() }
        result = 31 * result + (existingStyle?.hashCode() ?: 0)
        result = 31 * result + isEditMode.hashCode()
        result = 31 * result + allowMultiPhoto.hashCode()
        result = 31 * result + maxPhotoSelection.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isSaving.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (capSheet?.hashCode() ?: 0)
        result = 31 * result + readOnly.hashCode()
        return result
    }

    private fun List<ByteArray>.contentEqualsSafe(other: List<ByteArray>): Boolean {
        if (size != other.size) return false
        return indices.all { this[it].contentEquals(other[it]) }
    }
}
