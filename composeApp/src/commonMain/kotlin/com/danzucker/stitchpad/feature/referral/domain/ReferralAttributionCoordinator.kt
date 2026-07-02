package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.navigation.DeepLinkParser
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ReferralAttribution"

/**
 * The fire-and-forget hooks the app calls into. Kept as an interface so the SignUp
 * ViewModel can depend on it without constructing the full coordinator in tests.
 */
interface ReferralAttribution {
    /** Android app-start: read the Play Install Referrer once and stash any code. */
    fun captureInstallReferrer()

    /** Post-auth: attribute the manual/captured code exactly once per install. */
    fun submitPendingAttribution(manualCode: String?)
}

/**
 * Owns the client half of referral attribution: capture a code (Play Install Referrer
 * or a /r/ App Link), then submit it once the user is authenticated. Everything is
 * best-effort and fire-and-forget — a signup must never block or fail on attribution.
 *
 * Runs on an app-lifetime [scope] (not a ViewModel's) so the submit survives the
 * SignUp screen being torn down by post-signup navigation.
 */
class ReferralAttributionCoordinator(
    private val referralRepository: ReferralRepository,
    private val preferences: ReferralPreferencesStore,
    private val installReferrerReader: InstallReferrerReader,
    private val pendingDeepLink: PendingDeepLinkHolder,
    private val scope: CoroutineScope,
) : ReferralAttribution {

    /**
     * Android app-start hook: read the Play Install Referrer once and stash any code
     * for a later submit. No-op on iOS (reader yields null) and once already attributed.
     */
    override fun captureInstallReferrer() {
        scope.launch {
            if (preferences.hasAttributed()) return@launch
            val referrer = installReferrerReader.readReferrer() ?: return@launch
            val code = DeepLinkParser.parseInstallReferrerCode(referrer) ?: return@launch
            pendingDeepLink.setReferralCode(code)
            AppLogger.d(tag = TAG) { "captured install-referrer code" }
        }
    }

    /**
     * Post-auth hook (called from signup/SSO success). Fire-and-forget: resolves the
     * code and submits attribution exactly once per install.
     */
    override fun submitPendingAttribution(manualCode: String?) {
        scope.launch { attributeOnce(manualCode) }
    }

    /**
     * The testable core. A manually-entered [manualCode] wins over a captured pending
     * code; if neither is present, or attribution already succeeded, this no-ops.
     * Only consumes the pending code when it's actually used, so a manual-path failure
     * leaves the captured code available for a retry on a later launch.
     */
    suspend fun attributeOnce(manualCode: String?) {
        if (preferences.hasAttributed()) return

        val normalizedManual = DeepLinkParser.normalizeReferralCode(manualCode)
        val (code, source) = if (normalizedManual != null) {
            normalizedManual to ReferralSource.MANUAL
        } else {
            val pending = pendingDeepLink.consumeReferralCode() ?: return
            pending to ReferralSource.INSTALL_REFERRER
        }

        val deviceHash = preferences.getOrCreateDeviceId()
        when (val result = referralRepository.recordAttribution(code, deviceHash, source)) {
            is Result.Success -> {
                preferences.setAttributed()
                AppLogger.d(tag = TAG) {
                    "attributed (source=${source.wire}, already=${result.data.alreadyAttributed})"
                }
            }
            is Result.Error -> {
                // Leave unattributed; the server is idempotent so a later launch can retry.
                AppLogger.w(tag = TAG) { "attribution failed: ${result.error}" }
            }
        }
    }
}
