package com.danzucker.stitchpad.feature.tutorials.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.feature.tutorials.domain.repository.TutorialsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HelpTutorialsViewModel(
    private val tutorialsRepository: TutorialsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HelpTutorialsState())
    val state: StateFlow<HelpTutorialsState> = _state.asStateFlow()

    private val _events = Channel<HelpTutorialsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            tutorialsRepository.tutorials.collect { list ->
                _state.update { it.copy(isLoading = false, tutorials = list) }
            }
        }
    }

    fun onAction(action: HelpTutorialsAction) {
        when (action) {
            HelpTutorialsAction.OnBack ->
                viewModelScope.launch { _events.send(HelpTutorialsEvent.NavigateBack) }
            is HelpTutorialsAction.OnTutorialClick ->
                viewModelScope.launch { _events.send(HelpTutorialsEvent.NavigateToPlayer(action.tutorialId)) }
        }
    }
}
