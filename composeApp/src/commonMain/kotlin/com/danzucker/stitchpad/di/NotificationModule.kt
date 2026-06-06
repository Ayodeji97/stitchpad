package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.feature.notification.data.FirebaseNotificationRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationDataModule = module {
    singleOf(::FirebaseNotificationRepository) bind NotificationRepository::class
}
