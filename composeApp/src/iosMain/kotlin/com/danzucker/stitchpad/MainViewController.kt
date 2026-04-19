package com.danzucker.stitchpad

import androidx.compose.ui.window.ComposeUIViewController
import com.danzucker.stitchpad.core.logging.AppLogger

fun MainViewController() = run {
    AppLogger.init()
    ComposeUIViewController { App() }
}
