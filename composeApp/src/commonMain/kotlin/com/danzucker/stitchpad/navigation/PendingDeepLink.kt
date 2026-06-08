package com.danzucker.stitchpad.navigation

import kotlinx.coroutines.flow.MutableStateFlow

enum class DeepLinkTarget { INBOX }

/** Single-shot holder for an external (push-tap) navigation target. */
class PendingDeepLinkHolder {
    val target = MutableStateFlow<DeepLinkTarget?>(null)
    fun set(t: DeepLinkTarget) { target.value = t }
    fun clear() { target.value = null }
}
