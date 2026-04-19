package com.danzucker.stitchpad

import androidx.compose.ui.window.ComposeUIViewController
import com.danzucker.stitchpad.core.logging.AppLogger

fun MainViewController() = ComposeUIViewController {
    App()
}.also { AppLogger.init() }
