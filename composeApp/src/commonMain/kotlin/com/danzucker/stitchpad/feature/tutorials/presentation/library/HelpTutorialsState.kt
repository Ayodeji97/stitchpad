package com.danzucker.stitchpad.feature.tutorials.presentation.library

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial

data class HelpTutorialsState(
    val isLoading: Boolean = true,
    val tutorials: List<Tutorial> = emptyList(),
)
