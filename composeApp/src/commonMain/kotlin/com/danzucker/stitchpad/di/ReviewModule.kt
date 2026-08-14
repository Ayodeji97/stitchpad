package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.appLifetimeScope
import com.danzucker.stitchpad.feature.review.presentation.ReviewArmer
import com.danzucker.stitchpad.feature.review.presentation.ReviewController
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

val reviewModule = module {
    single<CoroutineScope>(qualifier = named("reviewAppScope")) {
        appLifetimeScope(tag = "reviewAppScope")
    }
    single {
        ReviewController(
            preferences = get(),
            analytics = get(),
            launcher = get(),
            authUserIds = get<FirebaseAuth>().authStateChanged.map { it?.uid },
            scope = get(qualifier = named("reviewAppScope")),
            now = ::nowEpochMs,
        )
    } bind ReviewArmer::class
}
