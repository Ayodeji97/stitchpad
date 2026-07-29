package com.danzucker.stitchpad.core.data.staff

import android.content.Context
import android.content.SharedPreferences
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore

actual class StaffMembershipPrefs(context: Context) : StaffMembershipPrefsStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("staff_prefs", Context.MODE_PRIVATE)

    override suspend fun getWorkshopUid(): String? = prefs.getString(KEY_WORKSHOP_UID, null)

    override suspend fun setWorkshopUid(uid: String) {
        prefs.edit().putString(KEY_WORKSHOP_UID, uid).apply()
    }

    override suspend fun clear() {
        prefs.edit().remove(KEY_WORKSHOP_UID).apply()
    }

    private companion object {
        const val KEY_WORKSHOP_UID = "workshop_uid"
    }
}
