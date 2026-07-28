package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.data.staff.CloudFunctionsInviteRedemptionRepository
import com.danzucker.stitchpad.core.data.staff.CloudFunctionsStaffRepository
import com.danzucker.stitchpad.core.domain.staff.repository.InviteRedemptionRepository
import com.danzucker.stitchpad.core.domain.staff.repository.StaffRepository
import org.koin.dsl.module

// Owner + Staff client repositories. FirebaseFunctions is provided by
// smartDataModule (Firebase.functions("europe-west1")); FirebaseFirestore by coreModule.
val staffModule = module {
    single<StaffRepository> { CloudFunctionsStaffRepository(functions = get(), firestore = get()) }
    single<InviteRedemptionRepository> { CloudFunctionsInviteRedemptionRepository(functions = get()) }
}
