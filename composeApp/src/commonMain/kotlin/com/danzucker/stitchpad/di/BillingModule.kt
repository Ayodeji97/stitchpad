package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.billing.data.InMemoryEntitlementsRepository
import com.danzucker.stitchpad.feature.billing.domain.EntitlementsRepository
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    // Lambda factory (not singleOf) because InMemoryEntitlementsRepository has a
    // default-value constructor parameter (initialIsPremium); singleOf can't skip
    // those defaults — same pitfall as the dashboard's viewModelOf usage.
    // QA toggle: flip between false (see Plan card variants) and true (see
    // Pro badge in hero, no Plan card). Currently set to true so the Tailor
    // Pro badge is visible during the hero-card review. Restore to a real
    // EntitlementsRepository before V1 ships.
    single { InMemoryEntitlementsRepository(initialIsPremium = true) } bind EntitlementsRepository::class
}
