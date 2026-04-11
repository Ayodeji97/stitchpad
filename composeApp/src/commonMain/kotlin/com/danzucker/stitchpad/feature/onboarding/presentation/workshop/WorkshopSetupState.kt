package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import org.jetbrains.compose.resources.StringResource

data class WorkshopSetupState(
    val businessName: String = "",
    val phone: String = "",
    val isLoading: Boolean = false,
    val businessNameError: StringResource? = null,
    val phoneError: StringResource? = null
)
