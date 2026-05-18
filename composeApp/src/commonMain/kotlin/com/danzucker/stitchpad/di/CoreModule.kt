package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val coreModule = module {
    single { Firebase.auth }
    single { Firebase.firestore }
    single { Firebase.storage }

    // App-lifetime scope for the EntitlementsProvider auth-state listener.
    // Named separately from smartAppScope to avoid qualifier collisions.
    single<CoroutineScope>(qualifier = named("entitlementsAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<EntitlementsProvider> {
        UserDocEntitlementsProvider(
            auth = get(),
            firestore = get(),
            scope = get<CoroutineScope>(qualifier = named("entitlementsAppScope")),
        )
    }
}
