package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import platform.Foundation.NSUserDefaults

actual class StaffMembershipPrefs : StaffMembershipPrefsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun getWorkshopUid(): String? = defaults.stringForKey(KEY_WORKSHOP_UID)

    override suspend fun setWorkshopUid(uid: String) {
        defaults.setObject(uid, forKey = KEY_WORKSHOP_UID)
    }

    override suspend fun clear() {
        defaults.removeObjectForKey(KEY_WORKSHOP_UID)
    }

    private companion object {
        const val KEY_WORKSHOP_UID = "staff_workshop_uid"
    }
}
