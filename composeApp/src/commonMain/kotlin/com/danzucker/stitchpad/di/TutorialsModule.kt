package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.tutorials.data.TutorialMediaResolver
import com.danzucker.stitchpad.feature.tutorials.data.repository.FirebaseTutorialsRepository
import com.danzucker.stitchpad.feature.tutorials.domain.TutorialUriResolver
import com.danzucker.stitchpad.feature.tutorials.domain.repository.TutorialsRepository
import com.danzucker.stitchpad.feature.tutorials.presentation.hint.TutorialHintViewModel
import com.danzucker.stitchpad.feature.tutorials.presentation.library.HelpTutorialsViewModel
import com.danzucker.stitchpad.feature.tutorials.presentation.player.TutorialPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val tutorialsModule = module {
    // App-scoped: shares the catalog Firestore listener across all hint/library subscribers and
    // survives the player screen so a cache download started on play finishes even after the user
    // closes the player. Mirrors the named scopes in CoreModule.
    single<CoroutineScope>(qualifier = named("tutorialsAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<TutorialsRepository> {
        FirebaseTutorialsRepository(
            firestore = get(),
            appScope = get<CoroutineScope>(qualifier = named("tutorialsAppScope")),
        )
    }
    single<TutorialUriResolver> {
        TutorialMediaResolver(
            storage = get(),
            cache = get(),
            downloadScope = get<CoroutineScope>(qualifier = named("tutorialsAppScope")),
        )
    }

    viewModelOf(::HelpTutorialsViewModel)
    viewModelOf(::TutorialPlayerViewModel)
    // Parametrized: topicId is supplied per empty-state surface via parametersOf in TutorialHintRoot.
    viewModel { params -> TutorialHintViewModel(params.get(), get(), get()) }
}
