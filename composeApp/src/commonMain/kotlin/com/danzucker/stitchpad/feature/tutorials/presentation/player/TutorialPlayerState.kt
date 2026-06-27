package com.danzucker.stitchpad.feature.tutorials.presentation.player

import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial

data class TutorialPlayerState(
    val isLoading: Boolean = true,
    val tutorial: Tutorial? = null,
    val playableUri: String? = null,
    val hasError: Boolean = false,
    // The video player buffers the (often remote) clip before the first frame; true while it does
    // so the screen can overlay the branded loading indicator. Starts true once a uri is resolved.
    val isBuffering: Boolean = true,
)
