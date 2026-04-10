package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

data class WorkshopSetupState(
    val businessName: String = "",
    val phone: String = "",
    val isLoading: Boolean = false
)
