package com.danzucker.stitchpad.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
expect class ImageCaptureLauncher {
    fun launch()
}

@Composable
expect fun rememberImageCaptureLauncher(
    onResult: (ByteArray?) -> Unit
): ImageCaptureLauncher
