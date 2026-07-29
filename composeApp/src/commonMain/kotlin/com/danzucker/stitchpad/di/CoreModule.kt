package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider
import com.danzucker.stitchpad.core.data.session.FirebaseActiveWorkshopProvider
import com.danzucker.stitchpad.core.data.session.MembershipStatusDto
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineUploadOutbox
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import org.koin.core.qualifier.named
import org.koin.dsl.module

// Backoff between membership-doc snapshot retries during the staff pending
// window, mirroring UserDocEntitlementsProvider's listener-keep-alive policy so
// a transient read failure doesn't strand a pending staffer on owner-of-self.
private const val WORKSHOP_MEMBERSHIP_RETRY_DELAY_MS = 5_000L

val coreModule = module {
    single { Firebase.auth }
    single { Firebase.firestore }
    single { Firebase.storage }

    // App-lifetime scope for the EntitlementsProvider auth-state listener.
    // Named separately from smartAppScope to avoid qualifier collisions.
    single<CoroutineScope>(qualifier = named("entitlementsAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<CoroutineScope>(qualifier = named("offlineWriteAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    // App-lifetime scope for the ActiveWorkshopProvider auth-state listener.
    single<CoroutineScope>(qualifier = named("workshopSessionAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single {
        OfflineWriteDispatcher(
            appScope = get<CoroutineScope>(qualifier = named("offlineWriteAppScope")),
        )
    }
    single {
        OfflineUploadOutbox(
            firestore = get(),
            storage = get(),
            photoStore = get(),
            scheduler = get(),
            appScope = get<CoroutineScope>(qualifier = named("offlineWriteAppScope")),
        )
    }
    single<EntitlementsProvider> {
        UserDocEntitlementsProvider(
            auth = get(),
            firestore = get(),
            scope = get<CoroutineScope>(qualifier = named("entitlementsAppScope")),
        )
    }
    single<ActiveWorkshopProvider> {
        val authRepository = get<AuthRepository>()
        val firestore = get<FirebaseFirestore>()
        val membershipPrefs = get<StaffMembershipPrefsStore>()
        FirebaseActiveWorkshopProvider(
            // idTokenChanged (not authStateChanged) so a claim change — e.g. an
            // owner approving a staff member, once the token refreshes — re-emits
            // and re-resolves the session. Reuses the repo's claim reader.
            authClaims = get<FirebaseAuth>().idTokenChanged.map { authRepository.getWorkshopClaims() },
            scope = get<CoroutineScope>(qualifier = named("workshopSessionAppScope")),
            // The workshopUid the staffer recorded at redeem time; drives the
            // pending-window membership watch before the approval claim lands.
            storedWorkshopUid = { membershipPrefs.getWorkshopUid() },
            membershipStatusFlow = { workshopUid, authUid ->
                firestore.collection("users").document(workshopUid)
                    .collection("memberships").document(authUid).snapshots
                    .map { snap ->
                        MembershipStatus.fromWire(
                            if (snap.exists) snap.data<MembershipStatusDto>().status else null,
                        )
                    }
                    // retryWhen (not .catch) so a transient read failure keeps
                    // the listener alive instead of ending the flow and stranding
                    // the staffer on owner-of-self until the app restarts.
                    .retryWhen { error, _ ->
                        AppLogger.e(tag = "ActiveWorkshopProvider", throwable = error) {
                            "membership snapshot failed; retrying in ${WORKSHOP_MEMBERSHIP_RETRY_DELAY_MS}ms"
                        }
                        delay(WORKSHOP_MEMBERSHIP_RETRY_DELAY_MS)
                        true
                    }
            },
            // After approval the claim is not yet on the live token; force a
            // refresh so idTokenChanged re-emits with the staff claim.
            refreshToken = { authRepository.forceRefreshIdToken() },
        )
    }

    single<CoroutineScope>(qualifier = named("celebrationAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single {
        CelebrationController(
            preferences = get(),
            analytics = get(),
            authUserIds = get<FirebaseAuth>().authStateChanged.map { it?.uid },
            scope = get<CoroutineScope>(qualifier = named("celebrationAppScope")),
        )
    }
}
