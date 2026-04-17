package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.measurement.data.FirebaseMeasurementRepository
import com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val measurementDataModule = module {
    singleOf(::FirebaseMeasurementRepository) bind MeasurementRepository::class
}

val measurementPresentationModule = module {
    viewModelOf(::MeasurementFormViewModel)
}
