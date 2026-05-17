package com.danzucker.stitchpad.core.data.entitlement

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

private const val TAG = "EntitlementsProvider"

/**
 * Watches the signed-in user's Firestore document and recomputes
 * [UserEntitlements] whenever [subscriptionTier] or [welcomeBonusAppliedAt]
 * changes. Resets to FREE defaults on sign-out so one user's entitlements
 * never leak into the next user's session in the same process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class UserDocEntitlementsProvider(
    auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val now: () -> Instant = {
        Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
    },
    private val timeZone: TimeZone = TimeZone.of("Africa/Lagos"),
    scope: CoroutineScope,
) : EntitlementsProvider {

    private val _flow = MutableStateFlow(defaultEntitlements())
    override val flow: StateFlow<UserEntitlements> = _flow.asStateFlow()

    init {
        scope.launch {
            auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(defaultEntitlements())
                    } else {
                        firestore.collection("users").document(uid).snapshots
                            .map { snap ->
                                if (!snap.exists) return@map defaultEntitlements()
                                @Suppress("UNCHECKED_CAST")
                                val data = snap.data<Map<String, Any?>>()
                                val tierWire = data["subscriptionTier"] as? String
                                val seededAt = extractTimestamp(data["welcomeBonusAppliedAt"])
                                EntitlementsCalculator.calculate(
                                    tier = SubscriptionTier.fromWire(tierWire),
                                    welcomeBonusAppliedAt = seededAt,
                                    now = now(),
                                    timeZone = timeZone,
                                )
                            }
                    }
                }
                .collectLatest { _flow.value = it }
        }
    }

    override fun current(): UserEntitlements = _flow.value

    private fun defaultEntitlements() = EntitlementsCalculator.calculate(
        tier = SubscriptionTier.FREE,
        welcomeBonusAppliedAt = null,
        now = now(),
        timeZone = timeZone,
    )

    /**
     * Extracts a server-written timestamp from a raw Firestore map value.
     *
     * GitLive Firestore returns [FieldValue.serverTimestamp] writes as a
     * [Timestamp] object (not a Long) when the document is read back as
     * [Map<String, Any?>]. Convert via [Timestamp.toMilliseconds] which
     * returns a Double (milliseconds since epoch).
     */
    private fun extractTimestamp(raw: Any?): Instant? {
        return when (raw) {
            is Timestamp -> {
                val millis = raw.toMilliseconds().toLong()
                Instant.fromEpochMilliseconds(millis)
            }
            is Long -> Instant.fromEpochMilliseconds(raw)
            is Double -> Instant.fromEpochMilliseconds(raw.toLong())
            else -> {
                if (raw != null) {
                    AppLogger.w(tag = TAG) { "Unexpected welcomeBonusAppliedAt type: ${raw::class}" }
                }
                null
            }
        }
    }
}
