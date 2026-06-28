package com.danzucker.stitchpad

import androidx.compose.ui.window.ComposeUIViewController
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.logging.CrashReportingAntilog
import com.danzucker.stitchpad.core.logging.IosCrashReporter
import com.danzucker.stitchpad.core.logging.NoOpCrashReporter
import com.danzucker.stitchpad.core.logging.iosNativeCrashReporter

fun MainViewController() = run {
    val crashReporter = iosNativeCrashReporter?.let(::IosCrashReporter) ?: NoOpCrashReporter
    AppLogger.init(
        crashReporter = crashReporter,
        extraAntilogs = listOf(CrashReportingAntilog(crashReporter)),
    )
    ComposeUIViewController { App() }
}
