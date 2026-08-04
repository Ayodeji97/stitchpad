package com.danzucker.stitchpad.feature.review.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import com.danzucker.stitchpad.feature.review.domain.StoreReviewLauncher
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val PACKAGE = "com.danzucker.stitchpad"
private const val MARKET_URL = "market://details?id=$PACKAGE"
private const val WEB_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

class AndroidStoreReviewLauncher(
    private val activityHolder: CurrentActivityHolder,
    private val context: Context,
) : StoreReviewLauncher {

    override suspend fun requestInAppReview() {
        val activity = activityHolder.activity ?: return
        try {
            // Callers may invoke this from a background dispatcher (e.g. ReviewController's
            // Dispatchers.Default scope). launchReviewFlow reads the Activity's window/decor
            // view and starts the review Activity, so it must run on main — same pattern as
            // IosStoreReviewLauncher.requestInAppReview().
            withContext(Dispatchers.Main) {
                val manager = ReviewManagerFactory.create(context)
                val info = manager.requestReviewFlow().await()
                manager.launchReviewFlow(activity, info).await()
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = "StoreReview", throwable = e) { "in-app review flow failed" }
        }
    }

    override fun openStoreListing() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            AppLogger.e(tag = "StoreReview", throwable = e) { "Play app missing; falling back to web" }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
