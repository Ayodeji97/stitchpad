package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.gift.data.CloudFunctionsGiftRepository
import com.danzucker.stitchpad.feature.gift.domain.GiftRepository
import com.danzucker.stitchpad.feature.gift.presentation.redeem.RedeemGiftViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val giftModule = module {
    single<GiftRepository> { CloudFunctionsGiftRepository(functions = get()) }
    viewModelOf(::RedeemGiftViewModel)
}
