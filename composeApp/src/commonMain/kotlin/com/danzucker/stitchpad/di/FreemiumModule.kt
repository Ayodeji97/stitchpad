package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.freemium.data.CloudFunctionsFreemiumRepository
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import com.danzucker.stitchpad.feature.freemium.presentation.reconcile.ReconcileCoordinator
import com.danzucker.stitchpad.feature.freemium.presentation.upgrade.UpgradeViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val freemiumModule = module {
    single<FreemiumRepository> {
        CloudFunctionsFreemiumRepository(
            auth = get(),
            firestore = get(),
            functions = get(),
        )
    }
    // App-lifetime scope for the ReconcileCoordinator's auth + entitlements
    // collector. SupervisorJob so a transient failure doesn't kill the
    // collector; private to the freemium feature (only consumer for now).
    single<CoroutineScope>(qualifier = named("freemiumAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single {
        // Bridge GitLive's FirebaseAuth.authStateChanged into the testable
        // Flow<String?> the coordinator expects. Keeps FirebaseAuth out of
        // the coordinator's API surface so unit tests can feed a synthetic
        // uid flow without faking the GitLive auth stack.
        val auth: FirebaseAuth = get()
        ReconcileCoordinator(
            uidFlow = auth.authStateChanged.map { it?.uid },
            entitlementsProvider = get(),
            freemiumRepository = get(),
            scope = get(qualifier = named("freemiumAppScope")),
        )
    }
    viewModelOf(::UpgradeViewModel)
}
