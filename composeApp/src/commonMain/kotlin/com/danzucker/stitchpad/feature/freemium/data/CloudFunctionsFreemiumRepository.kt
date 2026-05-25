package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

private const val TAG = "FreemiumRepo"

internal class CloudFunctionsFreemiumRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    // App-lifetime scope so the fire-and-forget swap write outlives the
    // CustomerListViewModel's coroutine (which cancels when the user dismisses
    // the SwapSheet). Same `freemiumAppScope` ReconcileCoordinator uses.
    private val appScope: CoroutineScope,
) : FreemiumRepository {

    override suspend fun reconcileSlots(): EmptyResult<DataError.Network> = try {
        functions.httpsCallable("reconcileCustomerSlots").invoke()
        Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        // Reconciliation failures are non-blocking — the gray-out is a soft
        // UX, not a security boundary. Log so the underlying cause (NOT_FOUND
        // when the function isn't deployed, unauthenticated, etc.) is visible
        // in the platform console instead of silently surfacing as "UNKNOWN".
        AppLogger.e(tag = TAG, throwable = e) { "reconcileSlots failed" }
        Result.Error(DataError.Network.UNKNOWN)
    }

    override suspend fun swapCustomerSlot(
        promote: String,
        demote: String,
    ): EmptyResult<DataError.Network> {
        val uid = auth.currentUser?.uid
            ?: return Result.Error(DataError.Network.UNAUTHORIZED)
        val nowMs = Clock.System.now().toEpochMilliseconds()
        // Client-side swap: flip slotState on both customers via a Firestore
        // batch. Fire-and-forget on appScope per the GitLive memory — batch
        // commit() suspends until the server ACKs, which on a flaky network
        // would hang the SwapSheet indefinitely. The local write applies
        // immediately via Firestore's offline persistence; the customer
        // snapshot listener in CustomerListViewModel reflects the swap on
        // the next emission, and reconcileSlots corrects any drift on the
        // next foreground if the server write truly failed.
        //
        // The synchronous Result.Success means CustomerListEvent.SwapFailed
        // is effectively dead code — kept on the interface for symmetry with
        // future swap paths (e.g. a server-side swapCustomerSlots Cloud
        // Function tracked as a V1.1 follow-up) that can surface real errors.
        val customersCollection = firestore.collection("users").document(uid)
            .collection("customers")
        val promoteRef = customersCollection.document(promote)
        val demoteRef = customersCollection.document(demote)
        appScope.launch {
            try {
                firestore.batch()
                    .update(promoteRef, "slotState" to "active", "lockedAt" to null)
                    .update(demoteRef, "slotState" to "locked", "lockedAt" to nowMs)
                    .commit()
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                AppLogger.e(tag = TAG, throwable = e) {
                    "swapCustomerSlot commit failed promote=$promote demote=$demote " +
                        "(snapshot listener + next reconcile will self-heal)"
                }
            }
        }
        return Result.Success(Unit)
    }
}
