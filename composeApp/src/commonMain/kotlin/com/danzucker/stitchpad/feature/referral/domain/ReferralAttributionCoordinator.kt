package com.danzucker.stitchpad.feature.referral.domain

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
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
 * a captured /r/ App Link, the Play Install Referrer on Android, or the clipboard on
 * iOS) and report it once the user is authenticated. Everything is best-effort and
 * fire-and-forget — a signup must never block or fail on attribution.
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
    private val clipboardReferralReader: ClipboardReferralReader,
    private val pendingDeepLink: PendingDeepLinkHolder,
    private val scope: CoroutineScope,
    private val uidFlow: Flow<String?>,
    private val analytics: Analytics,
    // Clipboard capture stays OFF until the /r/ web landing page ships. That page is
    // what puts our referral URL on the clipboard AND will carry a fresh provenance
    // token; until it exists a clipboard read could only ever fire on a stray URL a
    // user copied for their own reasons (false attribution). The reader is fully wired
    // + tested so activation is a one-line flip in the DI factory then. Default OFF so
    // production stays dormant; tests pass true to exercise the logic.
    private val clipboardCaptureEnabled: Boolean = false,
) : ReferralAttribution {

    // Serializes attributeOnce so the signup-success and auth-observer triggers don't
    // both pass the hasAttributed() guard, double-read the clipboard, or double-submit.
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
     * The testable core. Resolves the code by priority — manual field, captured
     * deep-link code, Play Install Referrer (Android), then clipboard (iOS) — and
     * submits it. No-ops when already attributed or no code is available. Serialized
     * by [submitMutex] so the signup-success and auth-observer triggers can't both
     * read the clipboard / submit in the same session.
     */
    @Suppress("ReturnCount") // staged guards (already-attributed, no-code) read clearer than nesting
    suspend fun attributeOnce(manualCode: String?): Unit = submitMutex.withLock {
        if (preferences.hasAttributed()) return@withLock

        // Prefer an explicitly-passed code, then a code stashed by submitPendingAttribution,
        // so a manual code wins even when this is the collector's attributeOnce(null).
        val manual = DeepLinkParser.normalizeReferralCode(manualCode) ?: manualOverride
        // Only consume the captured code when there's no manual code to prefer; the
        // resolved code is re-stashed below if the submit fails.
        val pending = if (manual == null) pendingDeepLink.consumeReferralCode() else null

        val (code, source) = resolveCode(manual, pending) ?: return@withLock

        val deviceHash = preferences.getOrCreateDeviceId()
        when (val result = referralRepository.recordAttribution(code, deviceHash, source)) {
            is Result.Success -> {
                preferences.setAttributed()
                manualOverride = null
                if (!result.data.alreadyAttributed) {
                    analytics.logEvent(
                        AnalyticsEvent.ReferralCodeApplied(source = source.wire, surface = "signup")
                    )
                }
                AppLogger.d(tag = TAG) {
                    "attributed (source=${source.wire}, already=${result.data.alreadyAttributed})"
                }
            }
            is Result.Error -> {
                // Leave unattributed so a later authenticated launch retries. Re-stash the
                // captured code (in-memory) so a within-session retry doesn't re-read the
                // source — important for the clipboard, whose read is one-shot (see
                // readClipboardReferralCode) to avoid re-showing the iOS paste banner. A
                // MANUAL code isn't re-stashed here: it survives in manualOverride, so
                // re-stashing would mislabel it INSTALL_REFERRER on the retry.
                if (source != ReferralSource.MANUAL) pendingDeepLink.setReferralCode(code)
                AppLogger.w(tag = TAG) { "attribution failed: ${result.error}" }
            }
        }
    }

    /**
     * Resolves the code + source by priority: manual field, then a captured deep-link
     * code, then the Play Install Referrer (Android), then the clipboard (iOS). The two
     * platform readers no-op on the other platform, so they never collide.
     */
    private suspend fun resolveCode(
        manual: String?,
        pending: String?,
    ): Pair<String, ReferralSource>? = when {
        manual != null -> manual to ReferralSource.MANUAL
        pending != null -> pending to ReferralSource.INSTALL_REFERRER
        else -> readInstallReferrerCode()?.let { it to ReferralSource.INSTALL_REFERRER }
            ?: readClipboardReferralCode()?.let { it to ReferralSource.CLIPBOARD }
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

    /**
     * Reads the clipboard exactly once per install (iOS deferred capture). Unlike the
     * silent Install Referrer read, touching the pasteboard shows the iOS "pasted
     * from…" banner, so we mark it checked after a SINGLE read regardless of outcome —
     * a genuine-but-unsubmitted code is retried from the in-memory re-stash, never by
     * re-reading (which would re-banner). Requires a full /r/ referral URL — a bare
     * pasted string is NOT accepted, so we never grab unrelated clipboard content.
     */
    private suspend fun readClipboardReferralCode(): String? {
        if (!clipboardCaptureEnabled || preferences.hasCheckedClipboard()) return null
        val clip = clipboardReferralReader.readClipboard()
        preferences.setClipboardChecked()
        return DeepLinkParser.parseReferral(clip)
    }
}
