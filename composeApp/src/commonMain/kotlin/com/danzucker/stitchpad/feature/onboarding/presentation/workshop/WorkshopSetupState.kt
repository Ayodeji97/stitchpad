package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import org.jetbrains.compose.resources.StringResource

data class WorkshopSetupState(
    val businessName: String = "",
    val whatsappNumber: String = "",
    val isLoading: Boolean = false,
    val businessNameError: StringResource? = null,
    val whatsappError: StringResource? = null,
)
