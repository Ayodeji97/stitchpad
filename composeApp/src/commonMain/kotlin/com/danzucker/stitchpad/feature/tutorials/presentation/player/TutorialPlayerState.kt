package com.danzucker.stitchpad.feature.tutorials.presentation.player

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial

data class TutorialPlayerState(
    val isLoading: Boolean = true,
    val tutorial: Tutorial? = null,
    val playableUri: String? = null,
    val hasError: Boolean = false,
)
