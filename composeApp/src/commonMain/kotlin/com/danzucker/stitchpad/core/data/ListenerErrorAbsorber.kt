package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.logging.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * Routes a Firestore listener error that arrives AFTER its collector has been
 * cancelled into a log line instead of the process-killing global handler.
 *
 * GitLive's `snapshots` is a callbackFlow: when the server rejects a listen —
 * auth died at sign-out, or access changed during a session flip (redeem,
 * approve, revoke, kill-switch) — the SDK callback closes the channel with the
 * error. If the collecting coroutine is already cancelling, which is exactly
 * what happens while navigation tears screens down during those transitions,
 * that close cause is UNDELIVERABLE: it bypasses every downstream
 * `catch`/`retryWhen` and goes to the producer context's
 * CoroutineExceptionHandler — by default the global one, which kills the app
 * (both platforms, 2026-08-13: iOS Crashlytics `-[KotlinNumber initWithBool:]`
 * terminations; Android FATAL FirebaseFirestoreException with a suppressed
 * `JobCancellationException: FlowCoroutine is cancelling`).
 *
 * `flowOn` merges a CoroutineExceptionHandler into the PRODUCER's context, so
 * the undeliverable cause lands here instead. Normal error delivery — collector
 * alive — is untouched: downstream `catch`/`retryWhen` still see errors first,
 * and this handler is only ever consulted for causes that had nowhere to go.
 *
 * Apply to EVERY `snapshots` flow at its source (the data layer), not at
 * collection sites — the handler must live in the producer's context.
 */
fun <T> Flow<T>.absorbLateListenerErrors(tag: String): Flow<T> =
    absorbLateListenerErrors(tag) { throwable ->
        AppLogger.e(tag = tag, throwable = throwable) {
            "listener error after collector cancellation absorbed"
        }
    }

/** Test seam: same wrapper with an observable absorption callback. */
internal fun <T> Flow<T>.absorbLateListenerErrors(
    tag: String,
    onAbsorbed: (Throwable) -> Unit,
): Flow<T> = flowOn(CoroutineExceptionHandler { _, throwable -> onAbsorbed(throwable) })
