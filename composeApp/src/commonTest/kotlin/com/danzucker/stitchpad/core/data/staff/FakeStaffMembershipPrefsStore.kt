package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeStaffMembershipPrefsStore(
    initial: String? = null,
    initialName: String? = null,
) : StaffMembershipPrefsStore {
    private val _workshopUid = MutableStateFlow(initial)
    override val workshopUid: StateFlow<String?> = _workshopUid.asStateFlow()
    private val _workshopName = MutableStateFlow(initialName)
    override val workshopName: StateFlow<String?> = _workshopName.asStateFlow()
    var clearCount = 0

    override suspend fun setWorkshop(uid: String, name: String?) {
        _workshopUid.value = uid
        _workshopName.value = name
    }

    override suspend fun clear() {
        clearCount++
        _workshopUid.value = null
        _workshopName.value = null
    }
}
