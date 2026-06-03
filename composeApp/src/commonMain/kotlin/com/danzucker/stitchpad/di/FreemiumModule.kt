package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.freemium.data.CloudFunctionsFreemiumRepository
import com.danzucker.stitchpad.feature.freemium.data.CloudFunctionsPaymentRepository
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import com.danzucker.stitchpad.feature.freemium.domain.PaymentRepository
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
    // App-lifetime scope for the ReconcileCoordinator's auth + entitlements
    // collector AND the fire-and-forget swap commit in
    // CloudFunctionsFreemiumRepository. SupervisorJob so a transient failure
    // in one consumer doesn't kill the others. Defined BEFORE the repository
    // single because the repository now depends on it.
    single<CoroutineScope>(qualifier = named("freemiumAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<FreemiumRepository> {
        CloudFunctionsFreemiumRepository(
            auth = get(),
            firestore = get(),
            functions = get(),
            appScope = get(qualifier = named("freemiumAppScope")),
        )
    }
    single<PaymentRepository> {
        CloudFunctionsPaymentRepository(functions = get())
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
