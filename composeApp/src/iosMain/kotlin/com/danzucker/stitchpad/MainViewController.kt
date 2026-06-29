package com.danzucker.stitchpad

import androidx.compose.ui.window.ComposeUIViewController
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.logging.CrashReportingAntilog
import com.danzucker.stitchpad.core.logging.IosCrashReporter
import com.danzucker.stitchpad.core.logging.NoOpCrashReporter
import com.danzucker.stitchpad.core.logging.iosNativeCrashReporter

fun MainViewController() = run {
    // Mirror Android: no crash reporting in debug builds (also gated natively in
    // iOSApp.swift via setCrashlyticsCollectionEnabled).
    val crashReporter = if (isDebugBuild) {
        NoOpCrashReporter
    } else {
        iosNativeCrashReporter?.let(::IosCrashReporter) ?: NoOpCrashReporter
    }
    AppLogger.init(
        crashReporter = crashReporter,
        extraAntilogs = if (isDebugBuild) emptyList() else listOf(CrashReportingAntilog(crashReporter)),
    )
    ComposeUIViewController { App() }
}
