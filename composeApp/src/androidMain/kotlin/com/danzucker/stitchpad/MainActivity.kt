package com.danzucker.stitchpad

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.danzucker.stitchpad.feature.auth.data.CurrentActivityHolder
import com.danzucker.stitchpad.feature.notification.push.PUSH_TARGET_EXTRA
import com.danzucker.stitchpad.feature.notification.push.PUSH_TARGET_INBOX
import com.danzucker.stitchpad.navigation.DeepLinkParser
import com.danzucker.stitchpad.navigation.DeepLinkTarget
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
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
        if (intent.getStringExtra(PUSH_TARGET_EXTRA) == PUSH_TARGET_INBOX) {
            pendingDeepLink.set(DeepLinkTarget.INBOX)
            // Consume the extra so a later recreate (e.g. rotation) doesn't re-fire the
            // deep link and yank the user back to the inbox.
            intent.removeExtra(PUSH_TARGET_EXTRA)
            setIntent(intent)
        }
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
        }
    }
}
