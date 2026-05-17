package com.danzucker.stitchpad.core.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Coroutine scope tied to the application's lifetime, not any ViewModel's.
 *
 * Use this for fire-and-forget writes that must outlive the screen that triggered them —
 * e.g., Firestore mutations whose local cache write is synchronous but whose
 * suspend-until-server-ACK hangs offline. ViewModels that `await` these calls block their
 * own coroutine until the network returns; launching in ApplicationScope lets the write
 * proceed in Firestore's local mutation queue and survive ViewModel destruction while the
 * UI navigates away immediately.
 *
 * SupervisorJob so one failed child coroutine doesn't cancel siblings.
 */
class ApplicationScope(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : CoroutineScope by scope
