package com.danzucker.stitchpad.feature.auth.data

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.auth.domain.RemoteSyncGate
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * [RemoteSyncGate] over Firestore's network layer (`disableNetwork`/`enableNetwork`).
 *
 * The gate reopens itself when the next user signs in ([FirebaseAuth.authStateChanged]
 * emits non-null): by then the previous session's screens — and their listeners —
 * are gone, so the reconnect has nothing the server could reject. Sign-out
 * failure reopens it explicitly in [com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase]
 * because the still-authenticated user stays in the app and needs live data.
 *
 * A fresh process always starts with the network enabled (`disableNetwork` is not
 * persisted), so a crash or force-kill while gated can never strand the app offline.
 */
class FirebaseRemoteSyncGate(
    private val firestore: FirebaseFirestore,
    auth: FirebaseAuth,
    scope: CoroutineScope,
) : RemoteSyncGate {

    init {
        scope.launch {
            auth.authStateChanged
                .filterNotNull()
                .collect {
                    // Idempotent when already enabled (app start, normal sign-in).
                    runCatching { firestore.enableNetwork() }
                        .onFailure { AppLogger.w { "enableNetwork on sign-in failed: ${it.message}" } }
                }
        }
    }

    override suspend fun quiesce() {
        firestore.disableNetwork()
    }

    override suspend fun resume() {
        firestore.enableNetwork()
    }
}
