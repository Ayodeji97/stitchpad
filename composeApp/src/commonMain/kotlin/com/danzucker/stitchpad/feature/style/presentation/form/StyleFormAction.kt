package com.danzucker.stitchpad.feature.style.presentation.form

sealed interface StyleFormAction {
    data class OnPhotosPicked(val photos: List<ByteArray>) : StyleFormAction
    class OnRemovePhoto(val photo: ByteArray) : StyleFormAction
    data object OnSaveClick : StyleFormAction
    data object OnNavigateBack : StyleFormAction
    data object OnErrorDismiss : StyleFormAction

    /** Dismiss the cap-reached sheet without upgrading. */
    data object OnDismissCapSheet : StyleFormAction

    /** CTA tapped on the cap-reached sheet — clears sheet then routes to Upgrade. */
    data object OnUpgradeFromCap : StyleFormAction
}
