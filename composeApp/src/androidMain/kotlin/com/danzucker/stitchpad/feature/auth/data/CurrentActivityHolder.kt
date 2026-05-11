package com.danzucker.stitchpad.feature.auth.data

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * Weak-ref holder for the foreground Activity. MainActivity sets `activity = this`
 * in onCreate and `activity = null` in onDestroy. Read by AndroidSsoCredentialProvider
 * to obtain an Activity context for Credential Manager.
 *
 * Using WeakReference avoids leaking the Activity if onDestroy doesn't run before
 * the process is reused.
 */
class CurrentActivityHolder {
    private var ref: WeakReference<Activity>? = null

    var activity: Activity?
        get() = ref?.get()
        set(value) {
            ref = value?.let { WeakReference(it) }
        }
}
