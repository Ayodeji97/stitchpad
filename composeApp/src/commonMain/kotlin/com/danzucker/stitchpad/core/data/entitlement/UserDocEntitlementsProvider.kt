package com.danzucker.stitchpad.core.data.entitlement

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Wire shape of the slice of `users/{uid}` we actually read for entitlements.
 *
 * Why a typed DTO instead of `Map<String, Any?>`: kotlinx.serialization on
 * Kotlin/Native has no runtime serializer for `Any?`, so calling
 * `snap.data<Map<String, Any?>>()` crashes the first time the snapshot
 * listener fires on iOS with `SerializationException: Serializer for class
 * 'Any' is not found`. Android falls back to reflection-based serializers
 * and silently worked — the bug only surfaced on a physical iOS device.
 *
 * All writers of `welcomeBonusAppliedAt` must write a `Timestamp` (not a
 * raw `Long`) for the deserialization here to succeed. See
 * [com.danzucker.stitchpad.core.debug.FreemiumDebugActions.expireWelcomeWindow]
 * for the debug-menu path.
 */
@Serializable
private data class UserEntitlementsDoc(
    val subscriptionTier: String? = null,
    val welcomeBonusAppliedAt: Timestamp? = null,
)

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

    // Tracks whether the current signed-in user's Firestore snapshot has been
    // applied. Resets to false on sign-out (uid == null) so the next sign-in
    // re-awaits a fresh snapshot. createCustomer + any other write-time cap
    // gates use [awaitHydrated] to avoid blocking a Pro/Atelier account with
    // 15+ customers on the default FREE/15 placeholder before Firestore emits.
    private val _hydrated = MutableStateFlow(false)

    /**
     * Emits Unit immediately, then once every hour. Combined with the
     * Firestore snapshot flow so that [EntitlementsCalculator.calculate]
     * is re-run with the current [now] even if no document change occurs
     * (e.g. the welcome window expiring at midnight while the app is open).
     */
    private val recomputeTicker = flow {
        while (true) {
            emit(Unit)
            delay(60 * 60 * 1000L) // 1 hour
        }
    }

    init {
        scope.launch {
            val snapshotFlow = auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(null as UserEntitlementsDoc?)
                    } else {
                        firestore.collection("users").document(uid).snapshots
                            .map { snap ->
                                if (!snap.exists) {
                                    null
                                } else {
                                    snap.data<UserEntitlementsDoc>()
                                }
                            }
                    }
                }

            combine(snapshotFlow, recomputeTicker) { data, _ -> data }
                .collectLatest { data ->
                    if (data == null) {
                        _flow.value = defaultEntitlements()
                        _hydrated.value = false
                    } else {
                        _flow.value = computeFromData(data)
                        _hydrated.value = true
                    }
                }
        }
    }

    private fun computeFromData(data: UserEntitlementsDoc): UserEntitlements {
        val seededAt = data.welcomeBonusAppliedAt?.let {
            Instant.fromEpochMilliseconds(it.toMilliseconds().toLong())
        }
        return EntitlementsCalculator.calculate(
            tier = SubscriptionTier.fromWire(data.subscriptionTier),
            welcomeBonusAppliedAt = seededAt,
            now = now(),
            timeZone = timeZone,
        )
    }

    override fun current(): UserEntitlements = _flow.value

    override suspend fun awaitHydrated(): UserEntitlements {
        _hydrated.first { it }
        return _flow.value
    }

    private fun defaultEntitlements() = EntitlementsCalculator.calculate(
        tier = SubscriptionTier.FREE,
        welcomeBonusAppliedAt = null,
        now = now(),
        timeZone = timeZone,
    )
}
