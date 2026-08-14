package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App-lifetime scope with the project-standard context: SupervisorJob so one
 * failed child never cancels its siblings, Dispatchers.Default so launches
 * never land on Main by accident, and a CoroutineExceptionHandler so an
 * uncaught throw is logged instead of reaching the platform default handler —
 * which terminates the process on both Android and Kotlin/Native.
 *
 * Every Koin app scope and iOS bridge scope must be built through this factory;
 * a bare `CoroutineScope(SupervisorJob() + Dispatchers.Default)` has NO handler
 * (SupervisorJob isolates siblings but does not catch anything).
 */
fun appLifetimeScope(
    tag: String,
    onUncaught: (Throwable) -> Unit = { throwable ->
        AppLogger.e(tag = tag, throwable = throwable) { "uncaught exception in app-lifetime scope" }
    },
): CoroutineScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
        onUncaught(throwable)
    },
)
