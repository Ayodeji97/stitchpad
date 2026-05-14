package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.billing.data.InMemoryEntitlementsRepository
import com.danzucker.stitchpad.feature.billing.domain.EntitlementsRepository
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    // Lambda factory (not singleOf) because InMemoryEntitlementsRepository has a
    // default-value constructor parameter (initialIsPremium); singleOf can't skip
    // those defaults — same pitfall as the dashboard's viewModelOf usage.
    // Pass false explicitly: the class default is `true` (a legacy paywall-preview
    // affordance), but Settings should ship the free experience until the real
    // freemium model lands.
    single {
        InMemoryEntitlementsRepository(initialIsPremium = false)
    } bind EntitlementsRepository::class
}
