package com.danzucker.stitchpad.feature.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.data.sync.SyncStatusObserver
import com.danzucker.stitchpad.core.domain.model.SyncStatus
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.workshopUidOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single collection site for [SyncStatusObserver.observe] — that flow is cold with no
 * sharing, so every collector opens its own Firestore listener. Collecting it here, once,
 * and exposing a hot [state] downstream keeps the whole app to one listener regardless of
 * how many tabs render [com.danzucker.stitchpad.ui.components.SyncStatusBanner].
 */
class SyncStatusViewModel(
    private val syncStatusObserver: SyncStatusObserver,
    private val activeWorkshopProvider: ActiveWorkshopProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncStatus.SYNCED)
    val state: StateFlow<SyncStatus> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = activeWorkshopProvider.workshopUidOrNull() ?: return@launch
            syncStatusObserver.observe(userId).collect { _state.value = it }
        }
    }
}
