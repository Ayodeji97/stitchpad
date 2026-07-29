package com.danzucker.stitchpad

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import com.danzucker.stitchpad.feature.notification.push.PUSH_ORDER_ID_EXTRA
import com.danzucker.stitchpad.feature.notification.push.PUSH_TARGET_EXTRA
import com.danzucker.stitchpad.navigation.DeepLinkParser
import com.danzucker.stitchpad.navigation.DeepLinkTarget
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import com.danzucker.stitchpad.navigation.PushTargetParser
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val currentActivityHolder: CurrentActivityHolder by inject()
    private val pendingDeepLink: PendingDeepLinkHolder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        currentActivityHolder.activity = this
        handlePushIntent(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    override fun onDestroy() {
        if (currentActivityHolder.activity === this) {
            currentActivityHolder.activity = null
        }
        super.onDestroy()
    }

    private fun handlePushIntent(intent: Intent?) {
        intent ?: return
        handlePushTapExtras(intent)
        // Renewal-reminder email "Renew" button. Accepts both the https App Link
        // (https://link.getstitchpad.com/upgrade?tier=&cadence=, the form the email uses)
        // and the legacy custom scheme (stitchpad://upgrade?...), via the shared parser.
        val preselect = if (intent.action == Intent.ACTION_VIEW) {
            DeepLinkParser.parseUpgrade(intent.dataString)
        } else {
            null
        }
        if (preselect != null) {
            pendingDeepLink.setUpgrade(tier = preselect.tier, cadence = preselect.cadence)
            // Consume so a recreate (e.g. rotation) doesn't re-fire the deep link.
            intent.data = null
            setIntent(intent)
            return
        }
        // Gift-claim email link (https://link.getstitchpad.com/claim?code=, also the
        // stitchpad://claim?code= custom scheme), via the shared parser.
        val claimCode = if (intent.action == Intent.ACTION_VIEW) {
            DeepLinkParser.parseClaimGift(intent.dataString)
        } else {
            null
        }
        if (claimCode != null) {
            pendingDeepLink.setClaimGift(claimCode)
            // Consume so a recreate (e.g. rotation) doesn't re-fire the deep link.
            intent.data = null
            setIntent(intent)
        }
        // Staff invite link (https://link.getstitchpad.com/join?code=, also the
        // stitchpad://join?code= custom scheme), via the shared parser.
        val joinCode = if (intent.action == Intent.ACTION_VIEW) {
            DeepLinkParser.parseStaffInvite(intent.dataString)
        } else {
            null
        }
        if (joinCode != null) {
            pendingDeepLink.setJoinWorkshop(joinCode)
            // Consume so a recreate (e.g. rotation) doesn't re-fire the deep link.
            intent.data = null
            setIntent(intent)
        }
        // Referral App Link (https://link.getstitchpad.com/r/&lt;code&gt;, also stitchpad://r).
        // Silent capture — no navigation target; the coordinator submits after signup.
        val referralCode = if (intent.action == Intent.ACTION_VIEW) {
            DeepLinkParser.parseReferral(intent.dataString)
        } else {
            null
        }
        if (referralCode != null) {
            pendingDeepLink.setReferralCode(referralCode)
            intent.data = null
            setIntent(intent)
        }
    }

    /**
     * Dispatches a push notification tap carrying `target`/`orderId` extras (set by
     * StitchPadMessagingService on the tap PendingIntent) via the shared [PushTargetParser].
     */
    private fun handlePushTapExtras(intent: Intent) {
        val targetExtra = intent.getStringExtra(PUSH_TARGET_EXTRA) ?: return
        val data = buildMap {
            put(PushTargetParser.TARGET_KEY, targetExtra)
            intent.getStringExtra(PUSH_ORDER_ID_EXTRA)?.let { put(PushTargetParser.ORDER_ID_KEY, it) }
        }
        val parsed = PushTargetParser.parse(data)
        when (parsed?.target) {
            DeepLinkTarget.ORDER -> parsed.orderId?.let { pendingDeepLink.setOrder(it) }
            DeepLinkTarget.TO_COLLECT -> pendingDeepLink.set(DeepLinkTarget.TO_COLLECT)
            DeepLinkTarget.INBOX -> pendingDeepLink.set(DeepLinkTarget.INBOX)
            DeepLinkTarget.UPGRADE, DeepLinkTarget.CLAIM_GIFT, DeepLinkTarget.JOIN_WORKSHOP, null -> Unit
        }
        // Consume the extras so a later recreate (e.g. rotation) doesn't re-fire the
        // deep link and yank the user back to it.
        intent.removeExtra(PUSH_TARGET_EXTRA)
        intent.removeExtra(PUSH_ORDER_ID_EXTRA)
        setIntent(intent)
    }
}
