package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.offline.OfflineUploadOutbox
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
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
    single<CoroutineScope>(qualifier = named("offlineWriteAppScope")) {
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
