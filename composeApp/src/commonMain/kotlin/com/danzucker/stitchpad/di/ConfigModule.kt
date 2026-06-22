package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.config.data.FirebaseAppConfigRepository
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val configDataModule = module {
    singleOf(::FirebaseAppConfigRepository) bind AppConfigRepository::class
}
