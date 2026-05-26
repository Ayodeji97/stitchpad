package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import org.jetbrains.compose.resources.StringResource

data class WorkshopSetupState(
    val businessName: String = "",
    val whatsappNumber: String = "",
    val isLoading: Boolean = false,
    val businessNameError: StringResource? = null,
    val whatsappError: StringResource? = null,
    val logo: LogoUploadState = LogoUploadState.Empty,
    /** True between Continue tap and in-flight upload completion. UI shows "Finishing logo upload…". */
    val isAwaitingLogo: Boolean = false,
)
