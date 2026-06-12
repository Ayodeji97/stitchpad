package com.danzucker.stitchpad.feature.style.presentation.form

sealed interface StyleFormAction {
    data class OnDescriptionChange(val description: String) : StyleFormAction
    data class OnPhotosPicked(val photos: List<ByteArray>) : StyleFormAction
    data object OnSaveClick : StyleFormAction
    data object OnNavigateBack : StyleFormAction
    data object OnErrorDismiss : StyleFormAction
}
