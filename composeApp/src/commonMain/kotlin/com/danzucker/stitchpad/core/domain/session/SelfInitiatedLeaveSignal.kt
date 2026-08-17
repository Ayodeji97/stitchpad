package com.danzucker.stitchpad.core.domain.session

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory latch marking a staff session that is ending because the user asked
 * for it ("Leave this workshop"), as opposed to an owner revoking them.
 *
 * Both look identical on [ActiveWorkshopProvider.flow]: an ACTIVE staff session
 * becomes owner-of-self. But the global demotion redirect must only own the
 * *involuntary* case. `cancelStaffMembership` flips the membership doc to
 * `revoked` before the callable returns, so during a self-initiated leave the
 * client demotes while `SignOutUseCase` is still in flight — and the redirect's
 * `popUpTo(graph.id)` would dispose `MainRoot`, clear the SettingsViewModel, and
 * cancel the sign-out half-finished. The user would be left on Workshop Setup,
 * still authenticated, with Firestore's network possibly still disabled.
 *
 * Held only for the duration of the leave sequence, which owns its own navigation.
 * A process death mid-leave clears it, which is the safe default: the next launch
 * resolves the session from the server.
 */
class SelfInitiatedLeaveSignal {
    private val leaving = MutableStateFlow(false)

    /** True while a user-initiated leave is between its callable and its sign-out. */
    val isLeaving: Boolean get() = leaving.value

    fun begin() {
        leaving.value = true
    }

    fun end() {
        leaving.value = false
    }
}
