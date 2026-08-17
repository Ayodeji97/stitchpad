package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.onboarding.domain.ResolveNeedsWorkshopSetup
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val onboardingModule = module {
    // OnboardingPreferences provided by platformModule (needs platform-specific construction)
    singleOf(::ResolveNeedsWorkshopSetup)
}
