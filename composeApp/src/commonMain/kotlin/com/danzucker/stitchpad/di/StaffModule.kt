package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.staff.CloudFunctionsInviteRedemptionRepository
import com.danzucker.stitchpad.core.data.staff.CloudFunctionsStaffRepository
import com.danzucker.stitchpad.core.domain.staff.repository.InviteRedemptionRepository
import com.danzucker.stitchpad.core.domain.staff.repository.StaffRepository
import com.danzucker.stitchpad.feature.staff.presentation.pending.StaffPendingViewModel
import com.danzucker.stitchpad.feature.staff.presentation.redeem.RedeemInviteViewModel
import com.danzucker.stitchpad.feature.staff.presentation.team.TeamViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// Owner + Staff client repositories. FirebaseFunctions is provided by
// smartDataModule (Firebase.functions("europe-west1")); FirebaseFirestore by coreModule.
val staffModule = module {
    single<StaffRepository> { CloudFunctionsStaffRepository(functions = get(), firestore = get()) }
    single<InviteRedemptionRepository> { CloudFunctionsInviteRedemptionRepository(functions = get()) }

    viewModelOf(::RedeemInviteViewModel)
    // Lambda factory (not viewModelOf) because TeamViewModel takes a default-value
    // nowMillis parameter — viewModelOf can't skip constructor defaults.
    viewModel { TeamViewModel(get(), get()) }
    // workshopName (display) + fromRedeem (flag) are nav args passed via parametersOf.
    viewModel { params ->
        StaffPendingViewModel(params.get(), params.get(), get(), get(), get(), get())
    }
}
