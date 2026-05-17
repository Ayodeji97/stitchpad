package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.coroutines.ApplicationScope
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import org.koin.dsl.module

val coreModule = module {
    single { Firebase.auth }
    single { Firebase.firestore }
    single { Firebase.storage }
    single { ApplicationScope() }
}
