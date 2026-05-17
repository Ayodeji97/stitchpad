package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.freemium.data.CloudFunctionsFreemiumRepository
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import org.koin.dsl.module

val freemiumModule = module {
    single<FreemiumRepository> {
        CloudFunctionsFreemiumRepository(
            auth = get(),
            firestore = get(),
            functions = get(),
        )
    }
}
