package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.feature.notification.data.FirebaseNotificationRepository
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationDataModule = module {
    singleOf(::FirebaseNotificationRepository) bind NotificationRepository::class
}

val notificationPresentationModule = module {
    viewModelOf(::NotificationsInboxViewModel)
}
