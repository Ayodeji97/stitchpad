package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.analytics.data.AnalyticsIdentitySync
import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsSink
import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsTracker
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.qualifier.named
import org.koin.dsl.module

val analyticsModule = module {
    single<Analytics> { FirebaseAnalyticsTracker(FirebaseAnalyticsSink(Firebase.analytics)) }

    single<CoroutineScope>(qualifier = named("analyticsAppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single(createdAtStart = true) {
        val userIdSource = Firebase.auth.authStateChanged.map { it?.uid }
        val tierSource = get<EntitlementsProvider>().flow.map { tierLabel(it.tier) }
        AnalyticsIdentitySync(
            userIdSource = userIdSource,
            tierSource = tierSource,
            analytics = get(),
            scope = get<CoroutineScope>(qualifier = named("analyticsAppScope")),
        ).also { it.start() }
    }
}

/**
 * Maps a [SubscriptionTier] to the lowercase analytics label sent as the
 * `subscription_tier` user property. Exhaustive `when` — adding a new tier
 * forces a compile error here so the label is never silently dropped.
 */
private fun tierLabel(tier: SubscriptionTier): String = when (tier) {
    SubscriptionTier.FREE -> "free"
    SubscriptionTier.PRO -> "pro"
    SubscriptionTier.ATELIER -> "atelier"
}
