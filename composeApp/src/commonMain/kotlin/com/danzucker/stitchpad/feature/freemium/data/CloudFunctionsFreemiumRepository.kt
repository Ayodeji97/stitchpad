package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlin.time.Clock

internal class CloudFunctionsFreemiumRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : FreemiumRepository {

    override suspend fun reconcileSlots(): EmptyResult<DataError.Network> = try {
        functions.httpsCallable("reconcileCustomerSlots").invoke()
        Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _e: Throwable) {
        // Reconciliation failures are non-blocking — the gray-out is a soft
        // UX, not a security boundary. Log and move on.
        Result.Error(DataError.Network.UNKNOWN)
    }

    @Suppress("ReturnCount")
    override suspend fun swapCustomerSlot(
        promote: String,
        demote: String,
    ): EmptyResult<DataError.Network> = try {
        val uid = auth.currentUser?.uid
            ?: return Result.Error(DataError.Network.UNAUTHORIZED)
        val nowMs = Clock.System.now().toEpochMilliseconds()
        // Client-side swap: flip slotState on both customers in a single
        // batch via Firestore directly (no Cloud Function needed for the
        // user-initiated swap path).
        val customersCollection = firestore.collection("users").document(uid)
            .collection("customers")
        val promoteRef = customersCollection.document(promote)
        val demoteRef = customersCollection.document(demote)
        firestore.batch()
            .update(promoteRef, "slotState" to "active", "lockedAt" to null)
            .update(demoteRef, "slotState" to "locked", "lockedAt" to nowMs)
            .commit()
        Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _e: Throwable) {
        Result.Error(DataError.Network.UNKNOWN)
    }
}
