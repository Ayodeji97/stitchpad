package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsSink
import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsTracker
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import org.koin.dsl.module

val analyticsModule = module {
    single<Analytics> { FirebaseAnalyticsTracker(FirebaseAnalyticsSink(Firebase.analytics)) }
}
