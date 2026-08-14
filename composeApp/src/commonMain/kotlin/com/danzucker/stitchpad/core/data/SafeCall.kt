package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Standard suspend-call wrapper: expected failures become [Result.Error],
 * [CancellationException] is ALWAYS rethrown so structured cancellation works.
 *
 * Why the rethrow is non-negotiable: a `catch (e: Exception)` without it turns
 * a cancelled coroutine (user navigated away) into the error branch — flipping
 * state and flashing error UI on a screen that is being torn down. See
 * `staffCall` in CloudFunctionsStaffRepository for the same shape with
 * feature-specific error mapping.
 */
suspend fun <T> safeCall(
    tag: String,
    op: String,
    block: suspend () -> T,
): Result<T, DataError.Network> = try {
    Result.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    AppLogger.e(tag = tag, throwable = e) { "$op failed" }
    Result.Error(DataError.Network.UNKNOWN)
}
