package com.danzucker.stitchpad.core.data.staff

import android.content.Context
import android.content.SharedPreferences
import com.danzucker.stitchpad.core.domain.staff.StaffMembershipPrefsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class StaffMembershipPrefs(context: Context) : StaffMembershipPrefsStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("staff_prefs", Context.MODE_PRIVATE)

    // Seeded from disk so a returning pending staffer enters the window on launch;
    // updated in-process on set/clear so the session provider re-resolves at redeem
    // time without an auth-token change.
    private val _workshopUid = MutableStateFlow(prefs.getString(KEY_WORKSHOP_UID, null))
    override val workshopUid: StateFlow<String?> = _workshopUid.asStateFlow()

    private val _workshopName = MutableStateFlow(prefs.getString(KEY_WORKSHOP_NAME, null))
    override val workshopName: StateFlow<String?> = _workshopName.asStateFlow()

    override suspend fun setWorkshop(uid: String, name: String?) {
        prefs.edit().putString(KEY_WORKSHOP_UID, uid).putString(KEY_WORKSHOP_NAME, name).apply()
        _workshopUid.value = uid
        _workshopName.value = name
    }

    override suspend fun clear() {
        prefs.edit().remove(KEY_WORKSHOP_UID).remove(KEY_WORKSHOP_NAME).apply()
        _workshopUid.value = null
        _workshopName.value = null
    }

    private companion object {
        const val KEY_WORKSHOP_UID = "workshop_uid"
        const val KEY_WORKSHOP_NAME = "workshop_name"
    }
}
