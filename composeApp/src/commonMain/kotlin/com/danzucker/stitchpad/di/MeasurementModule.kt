package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.measurement.data.FirebaseCustomMeasurementFieldRepository
import com.danzucker.stitchpad.feature.measurement.data.FirebaseMeasurementRepository
import com.danzucker.stitchpad.feature.measurement.presentation.detail.MeasurementDetailViewModel
import com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val measurementDataModule = module {
    singleOf(::FirebaseMeasurementRepository) bind MeasurementRepository::class
    singleOf(::FirebaseCustomMeasurementFieldRepository) bind CustomMeasurementFieldRepository::class
}

val measurementPresentationModule = module {
    viewModelOf(::MeasurementFormViewModel)
    // Explicit `viewModel { ... }` factory rather than viewModelOf(::MeasurementDetailViewModel)
    // because the VM takes a defaulted shareLabelsResolver param — viewModelOf can't skip
    // defaulted params (see feedback_koin_constructor_ref_defaults memory).
    viewModel {
        MeasurementDetailViewModel(
            savedStateHandle = get(),
            measurementRepository = get(),
            customFieldRepository = get(),
            customerRepository = get(),
            authRepository = get(),
            analytics = get(),
            measurementSharer = get(),
        )
    }
}
