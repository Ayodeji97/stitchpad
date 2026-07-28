package com.danzucker.stitchpad.core.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test double for [ActiveWorkshopProvider]. Defaults to an owner-of-self session
 * on `"test-uid"` (matching FakeAuthRepository's default id) so existing
 * ViewModel tests keep resolving the same data-scoping id after the workshopUid
 * refactor. Use [setSession] to simulate a staff or signed-out session.
 */
class FakeActiveWorkshopProvider(
    initial: WorkshopSession = WorkshopSession.ownerOfSelf("test-uid"),
) : ActiveWorkshopProvider {

    private val _flow = MutableStateFlow(initial)
    override val flow: StateFlow<WorkshopSession> = _flow.asStateFlow()

    private var hydrated = true

    override fun current(): WorkshopSession = _flow.value

    override fun hasHydrated(): Boolean = hydrated

    override suspend fun awaitHydrated(): WorkshopSession = _flow.value

    fun setSession(session: WorkshopSession) {
        _flow.value = session
    }

    fun setHydrated(value: Boolean) {
        hydrated = value
    }
}
