package com.danzucker.stitchpad.feature.style.presentation.form

import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.presentation.UiText

data class StyleFormState(
    val description: String = "",
    val selectedPhotoBytes: ByteArray? = null,
    val existingStyle: Style? = null,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null
) {
    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StyleFormState) return false
        if (description != other.description) return false
        if (!selectedPhotoBytes.contentEqualsSafe(other.selectedPhotoBytes)) return false
        if (existingStyle != other.existingStyle) return false
        if (isEditMode != other.isEditMode) return false
        if (isLoading != other.isLoading) return false
        if (isSaving != other.isSaving) return false
        if (errorMessage != other.errorMessage) return false
        return true
    }

    override fun hashCode(): Int {
        var result = description.hashCode()
        result = 31 * result + (selectedPhotoBytes?.contentHashCode() ?: 0)
        result = 31 * result + (existingStyle?.hashCode() ?: 0)
        result = 31 * result + isEditMode.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isSaving.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }

    private fun ByteArray?.contentEqualsSafe(other: ByteArray?): Boolean = when {
        this == null || other == null -> this == null && other == null
        else -> contentEquals(other)
    }
}
