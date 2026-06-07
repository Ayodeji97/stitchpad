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
        if (intent?.getStringExtra(PUSH_TARGET_EXTRA) == PUSH_TARGET_INBOX) {
            pendingDeepLink.set(DeepLinkTarget.INBOX)
        }
    }
}
