package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeStaffMembershipPrefsStore(initial: String? = null) : StaffMembershipPrefsStore {
    private val _workshopUid = MutableStateFlow(initial)
    override val workshopUid: StateFlow<String?> = _workshopUid.asStateFlow()
    var clearCount = 0

    override suspend fun setWorkshopUid(uid: String) {
        _workshopUid.value = uid
    }

    override suspend fun clear() {
        clearCount++
        _workshopUid.value = null
    }
}
