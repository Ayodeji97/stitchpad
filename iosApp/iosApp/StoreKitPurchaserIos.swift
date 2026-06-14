import Foundation
import StoreKit
import CryptoKit
import ComposeApp

/// Implements the Kotlin-defined `NativeStoreKitPurchaser` protocol so KMP iOS
/// code can drive Apple In-App Purchase. StoreKit 2's `Product.purchase`,
/// `AppStore.sync`, and `Transaction.*` APIs are Swift-only.
///
/// The server (`verifyAppleTransaction`) re-verifies every signed transaction and
/// owns entitlement, so this class never trusts a purchase locally — it forwards
/// the JWS and only finishes a transaction once Kotlin confirms the server grant.
///
/// Registered into Koin via PlatformModule_iosKt.iosNativeStoreKitPurchaser from
/// AppDelegate BEFORE doInitKoin, mirroring AppleSignInLauncherIos.
@objc public class StoreKitPurchaserIos: NSObject, NativeStoreKitPurchaser {

    // Transactions awaiting `finish()` until the server grants. Guarded by a lock
    // because purchase(), restore(), and the updates listener all touch it.
    private var pendingTransactions: [String: Transaction] = [:]
    private let pendingLock = NSLock()
    private var updatesListener: Task<Void, Never>?

    // MARK: NativeStoreKitPurchaser

    public func fetchProducts(productIds: [String]) async throws -> any ComposeApp.Result {
        do {
            let products = try await Product.products(for: productIds)
            let mapped = products.map { product in
                StoreKitProduct(
                    id: product.id,
                    displayName: product.displayName,
                    displayPrice: product.displayPrice
                )
            }
            return ResultSuccess(data: mapped)
        } catch {
            return ResultError(error: StoreKitError.network)
        }
    }

    public func purchase(productId: String, accountUid: String) async throws -> any ComposeApp.Result {
        do {
            let products = try await Product.products(for: [productId])
            guard let product = products.first else {
                return ResultError(error: StoreKitError.productNotFound)
            }
            let token = appAccountToken(forUid: accountUid)
            let result = try await product.purchase(options: [.appAccountToken(token)])
            switch result {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    let txnId = String(transaction.id)
                    storePending(txnId, transaction)
                    return ResultSuccess(data: storeKitPurchase(transaction, jws: verification.jwsRepresentation))
                case .unverified:
                    return ResultError(error: StoreKitError.verificationFailed)
                }
            case .pending:
                return ResultError(error: StoreKitError.pending)
            case .userCancelled:
                return ResultError(error: StoreKitError.cancelled)
            @unknown default:
                return ResultError(error: StoreKitError.unknown)
            }
        } catch {
            return ResultError(error: StoreKitError.network)
        }
    }

    public func restore() async throws -> any ComposeApp.Result {
        // Best-effort: AppStore.sync prompts for the Apple ID; ignore its failure
        // and still read whatever entitlements are already present.
        try? await AppStore.sync()

        var purchases: [StoreKitPurchase] = []
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result {
                let txnId = String(transaction.id)
                storePending(txnId, transaction)
                purchases.append(storeKitPurchase(transaction, jws: result.jwsRepresentation))
            }
        }
        return ResultSuccess(data: purchases)
    }

    public func finishTransaction(transactionId: String) async throws {
        if let transaction = takePending(transactionId) {
            await transaction.finish()
            return
        }
        // Fallback: the transaction wasn't cached (e.g. app relaunched) — find it
        // among the unfinished set and finish by id.
        for await result in Transaction.unfinished {
            if case .verified(let transaction) = result, String(transaction.id) == transactionId {
                await transaction.finish()
                return
            }
        }
    }

    // MARK: Transaction.updates listener

    /// Starts the long-lived listener for transactions delivered outside an active
    /// purchase (Ask-to-Buy approvals, auto-renewals, recovery). Each is forwarded
    /// to Kotlin, which re-verifies on the server and finishes on success. Call
    /// once from the AppDelegate at launch.
    @objc public func startObservingTransactions() {
        guard updatesListener == nil else { return }
        updatesListener = Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard let self = self, case .verified(let transaction) = result else { continue }
                let txnId = String(transaction.id)
                self.storePending(txnId, transaction)
                IosStoreKitBridgeKt.iosOnStoreKitTransaction(
                    signedTransactionJws: result.jwsRepresentation,
                    transactionId: txnId
                )
            }
        }
    }

    // MARK: Helpers

    private func storeKitPurchase(_ transaction: Transaction, jws: String) -> StoreKitPurchase {
        StoreKitPurchase(
            productId: transaction.productID,
            signedTransactionJws: jws,
            originalTransactionId: String(transaction.originalID),
            transactionId: String(transaction.id)
        )
    }

    private func storePending(_ id: String, _ transaction: Transaction) {
        pendingLock.lock(); defer { pendingLock.unlock() }
        pendingTransactions[id] = transaction
    }

    private func takePending(_ id: String) -> Transaction? {
        pendingLock.lock(); defer { pendingLock.unlock() }
        let transaction = pendingTransactions[id]
        pendingTransactions.removeValue(forKey: id)
        return transaction
    }

    /// Deterministic Firebase-uid → UUID, matching `appleAppAccountToken` in
    /// functions/src/billing/appleBilling.ts (SHA-256(uid), first 16 bytes as a
    /// UUID). Lets the server bind a verified transaction to this Firebase user.
    private func appAccountToken(forUid uid: String) -> UUID {
        let digest = SHA256.hash(data: Data(uid.utf8))
        let b = Array(digest.prefix(16))
        return UUID(uuid: (
            b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7],
            b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]
        ))
    }
}
