package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.goals.data.FirebaseWeeklyGoalRepository
import com.danzucker.stitchpad.feature.goals.domain.repository.WeeklyGoalRepository
import com.danzucker.stitchpad.feature.goals.presentation.setup.GoalSetupViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val goalsDataModule = module {
    singleOf(::FirebaseWeeklyGoalRepository) bind WeeklyGoalRepository::class
}

val goalsPresentationModule = module {
    viewModel {
        GoalSetupViewModel(
            weeklyGoalRepository = get(),
            authRepository = get()
        )
    }
}
