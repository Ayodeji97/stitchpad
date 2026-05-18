package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

sealed interface UpgradeEvent {
    data class OpenExternalBrowser(val url: String) : UpgradeEvent
}
