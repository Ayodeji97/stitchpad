package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.CustomGarmentTypeRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.order.data.FirebaseCustomGarmentTypeRepository
import com.danzucker.stitchpad.feature.order.data.FirebaseOrderRepository
import com.danzucker.stitchpad.feature.order.presentation.detail.OrderDetailViewModel
import com.danzucker.stitchpad.feature.order.presentation.form.OrderFormViewModel
import com.danzucker.stitchpad.feature.order.presentation.list.OrderListViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val orderDataModule = module {
    single<OrderRepository> {
        FirebaseOrderRepository(
            firestore = get(),
            storage = get(),
            offlineWrites = get(),
            photoStore = get(),
            uploadOutbox = get(),
            shareScope = get(qualifier = named("orderShareAppScope")),
        )
    }
    singleOf(::FirebaseCustomGarmentTypeRepository) bind CustomGarmentTypeRepository::class
}

val orderPresentationModule = module {
    viewModelOf(::OrderListViewModel)
    viewModelOf(::OrderFormViewModel)
    viewModelOf(::OrderDetailViewModel)
}
