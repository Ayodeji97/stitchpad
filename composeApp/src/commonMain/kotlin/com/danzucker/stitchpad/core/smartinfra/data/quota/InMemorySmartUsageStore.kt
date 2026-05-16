package com.danzucker.stitchpad.core.smartinfra.data.quota

import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Process-local cache of the signed-in user's remaining free-tier Smart
 * quota. Resets to `null` whenever the active user changes (including
 * sign-out) so a previous user's quota chip / "out of free drafts" hint
 * never leaks into the next user's session in the same process.
 */
internal class InMemorySmartUsageStore(
    auth: FirebaseAuth,
    scope: CoroutineScope,
) : SmartUsageStore {
    private val state = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = state.asStateFlow()

    init {
        scope.launch {
            auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { state.value = null }
        }
    }

    override fun update(remaining: Int?) {
        state.value = remaining
    }
}
