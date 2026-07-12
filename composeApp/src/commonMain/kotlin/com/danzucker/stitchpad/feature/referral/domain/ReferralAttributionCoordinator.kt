package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.navigation.DeepLinkParser
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

private const val TAG = "ReferralAttribution"

/**
 * The fire-and-forget hook the SignUp ViewModel calls into. Kept as an interface so
 * the VM can depend on it without constructing the full coordinator in tests.
 */
interface ReferralAttribution {
    /**
     * Attribute the manual/captured code exactly once per install. Safe to call
     * repeatedly — no-ops once attribution has succeeded. [manualCode] (the SignUp
     * field) wins over a captured code.
     */
    fun submitPendingAttribution(manualCode: String?)
}

/**
 * Owns the client half of referral attribution: resolve a code (a manually typed one,
 * a captured /r/ App Link, or the Play Install Referrer) and report it once the user
 * is authenticated. Everything is best-effort and fire-and-forget — a signup must
 * never block or fail on attribution.
 *
 * Two triggers, both funnelling into [attributeOnce]:
 *  - SignUp email/SSO success (via [submitPendingAttribution]) for immediacy + the
 *    manually typed code.
 *  - The auth-state [start] collector, so a submit that failed at signup (flaky
 *    network) retries on the next authenticated launch — the plan's "first
 *    authenticated launch" semantics.
 *
 * Runs on an app-lifetime [scope] so a submit survives the SignUp screen being torn
 * down by post-signup navigation.
 */
class ReferralAttributionCoordinator(
    private val referralRepository: ReferralRepository,
    private val preferences: ReferralPreferencesStore,
    private val installReferrerReader: InstallReferrerReader,
    private val pendingDeepLink: PendingDeepLinkHolder,
    private val scope: CoroutineScope,
    private val uidFlow: Flow<String?>,
) : ReferralAttribution {

    // Serializes attributeOnce so the two triggers (auth-state collector +
    // submitPendingAttribution) can never run concurrently — otherwise they could
    // double-consume a captured code or double-submit before the first completes.
    private val submitMutex = Mutex()

    // A manually-typed code, stashed synchronously the instant it's submitted, so it
    // deterministically wins over any captured code even when the collector's
    // attributeOnce(null) is the coroutine that acquires the lock first. (Residual:
    // only if the collector reads this before submitPendingAttribution is even called
    // could a captured code win — and the server is idempotent, so no double-attribution.)
    @Volatile
    private var manualOverride: String? = null

    /**
     * Begin observing auth state: whenever a user is signed in, attempt attribution.
     * Idempotent downstream ([attributeOnce] guards on hasAttributed), so this safely
     * re-runs a failed submit on later launches. Called once from the DI factory.
     */
    fun start() {
        scope.launch {
            uidFlow.collect { uid ->
                if (uid != null) attributeOnce(manualCode = null)
            }
        }
    }

    override fun submitPendingAttribution(manualCode: String?) {
        // Stash the manual code BEFORE launching, so whichever attributeOnce wins the
        // Mutex (this one or the collector's) sees it and prefers it — manual always wins.
        DeepLinkParser.normalizeReferralCode(manualCode)?.let { manualOverride = it }
        scope.launch { attributeOnce(manualCode) }
    }

    /**
     * The testable core. Resolves the code by priority — manual field, then a captured
     * deep-link code, then the Play Install Referrer (read at most once per install) —
     * and submits it. No-ops when already attributed or no code is available.
     */
    @Suppress("ReturnCount") // staged guards (already-attributed, no-code) read clearer than nesting
    suspend fun attributeOnce(manualCode: String?): Unit = submitMutex.withLock {
        if (preferences.hasAttributed()) return@withLock

        // Prefer an explicitly-passed code, then a code stashed by submitPendingAttribution,
        // so a manual code wins even when this is the collector's attributeOnce(null).
        val manual = DeepLinkParser.normalizeReferralCode(manualCode) ?: manualOverride
        // Only consume the captured code when there's no manual code to prefer; a
        // deep-link code we do consume is re-stashed below if the submit fails.
        val pending = if (manual == null) pendingDeepLink.consumeReferralCode() else null

        val code: String
        val source: ReferralSource
        when {
            manual != null -> {
                code = manual
                source = ReferralSource.MANUAL
            }
            pending != null -> {
                code = pending
                source = ReferralSource.INSTALL_REFERRER
            }
            else -> {
                val fromInstall = readInstallReferrerCode() ?: return@withLock
                code = fromInstall
                source = ReferralSource.INSTALL_REFERRER
            }
        }

        val deviceHash = preferences.getOrCreateDeviceId()
        when (val result = referralRepository.recordAttribution(code, deviceHash, source)) {
            is Result.Success -> {
                preferences.setAttributed()
                manualOverride = null
                AppLogger.d(tag = TAG) {
                    "attributed (source=${source.wire}, already=${result.data.alreadyAttributed})"
                }
            }
            is Result.Error -> {
                // Leave unattributed so a later authenticated launch retries. Re-stash a
                // consumed deep-link code (in-memory only); the Install Referrer is
                // re-readable next launch because its checked flag stays unset on failure.
                if (pending != null) pendingDeepLink.setReferralCode(pending)
                AppLogger.w(tag = TAG) { "attribution failed: ${result.error}" }
            }
        }
    }

    /**
     * Reads the Play Install Referrer at most once per install. Sets the checked flag
     * only when the read yields no code (organic) — a genuine code that fails to submit
     * leaves it unset so the read + retry runs again next launch.
     */
    private suspend fun readInstallReferrerCode(): String? {
        if (preferences.hasCheckedReferrer()) return null
        val referrer = installReferrerReader.readReferrer()
        val code = DeepLinkParser.parseInstallReferrerCode(referrer)
        if (code == null) preferences.setReferrerChecked()
        return code
    }
}
