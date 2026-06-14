package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private val iosStoreKitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Called by Swift's `Transaction.updates` listener (see StoreKitPurchaserIos) for
 * every transaction the App Store delivers outside an active purchase call:
 * delayed Ask-to-Buy approvals, auto-renewals, and recovery of a purchase whose
 * first server verify failed. Re-verifies on the server and finishes on success.
 *
 * The server binds the grant to the transaction's appAccountToken, so a
 * transaction belonging to a different Firebase user is rejected and left
 * unfinished for the rightful account. No-ops when logged out / not on iOS billing.
 */
fun iosOnStoreKitTransaction(signedTransactionJws: String, transactionId: String) {
    iosStoreKitScope.launch {
        runCatching {
            val repository = KoinPlatform.getKoin().get<PaymentRepository>()
            (repository as? StoreKitPaymentRepository)
                ?.reconcileTransaction(signedTransactionJws, transactionId)
        }.onFailure { AppLogger.w { "iosOnStoreKitTransaction failed: ${it.message}" } }
    }
}
