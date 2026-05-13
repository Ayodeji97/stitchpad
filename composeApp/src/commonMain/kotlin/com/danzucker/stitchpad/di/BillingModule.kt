package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.billing.data.InMemoryEntitlementsRepository
import com.danzucker.stitchpad.feature.billing.domain.EntitlementsRepository
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    // Lambda factory (not singleOf) because InMemoryEntitlementsRepository has a
    // default-value constructor parameter (initialIsPremium); singleOf can't skip
    // those defaults — same pitfall as the dashboard's viewModelOf usage.
    // Temporarily defaulting to free tier so the Settings plan card variants
    // (inline / hero-warn / hero-locked) are visible during QA. Flip back to
    // `true` (or remove the arg) before shipping V1.
    single { InMemoryEntitlementsRepository(initialIsPremium = false) } bind EntitlementsRepository::class
}
