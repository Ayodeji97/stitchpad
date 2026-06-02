package com.danzucker.stitchpad.core.offline

import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "OfflineWrite"

/**
 * Schedules Firestore writes off the UI's save path.
 *
 * Firestore applies mutations to its local cache and emits snapshots before the
 * write task is server-acknowledged. Awaiting the task in a ViewModel keeps
 * forms stuck in airplane mode even though the local document already exists,
 * so business writes are launched in an app-lifetime scope and failures are
 * surfaced through logs plus snapshot/server reconciliation.
 */
class OfflineWriteDispatcher(
    private val appScope: CoroutineScope,
) {
    suspend fun enqueue(
        operationName: String,
        block: suspend () -> Unit,
    ): Boolean {
        return try {
            appScope.launch {
                try {
                    block()
                } catch (throwable: kotlinx.coroutines.CancellationException) {
                    throw throwable
                } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
                    AppLogger.e(tag = TAG, throwable = throwable) {
                        "$operationName failed in background"
                    }
                }
            }
            true
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
            AppLogger.e(tag = TAG, throwable = throwable) {
                "$operationName could not be scheduled"
            }
            false
        }
    }
}
