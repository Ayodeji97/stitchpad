package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

actual class StaffMembershipPrefs : StaffMembershipPrefsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    // Seeded from disk so a returning pending staffer enters the window on launch;
    // updated in-process on set/clear so the session provider re-resolves at redeem
    // time without an auth-token change.
    private val _workshopUid = MutableStateFlow(defaults.stringForKey(KEY_WORKSHOP_UID))
    override val workshopUid: StateFlow<String?> = _workshopUid.asStateFlow()

    private val _workshopName = MutableStateFlow(defaults.stringForKey(KEY_WORKSHOP_NAME))
    override val workshopName: StateFlow<String?> = _workshopName.asStateFlow()

    override suspend fun setWorkshop(uid: String, name: String?) {
        defaults.setObject(uid, forKey = KEY_WORKSHOP_UID)
        if (name != null) {
            defaults.setObject(name, forKey = KEY_WORKSHOP_NAME)
        } else {
            defaults.removeObjectForKey(KEY_WORKSHOP_NAME)
        }
        _workshopUid.value = uid
        _workshopName.value = name
    }

    override suspend fun clear() {
        defaults.removeObjectForKey(KEY_WORKSHOP_UID)
        defaults.removeObjectForKey(KEY_WORKSHOP_NAME)
        _workshopUid.value = null
        _workshopName.value = null
    }

    private companion object {
        const val KEY_WORKSHOP_UID = "staff_workshop_uid"
        const val KEY_WORKSHOP_NAME = "staff_workshop_name"
    }
}
