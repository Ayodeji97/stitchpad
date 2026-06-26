package com.danzucker.stitchpad.feature.tutorials.presentation.hint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.domain.repository.TutorialsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives a single contextual "Watch how it works" empty-state card for one [TutorialTopic].
 * One instance per surface (keyed by topic id in [TutorialHintRoot]); the seen flag is read once
 * at init so the card starts expanded only on first visit and collapses to a quiet link after
 * watch/dismiss (and on every later visit, since the flag persists).
 */
class TutorialHintViewModel(
    private val topicId: String,
    private val tutorialsRepository: TutorialsRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TutorialHintUiState())
    val state: StateFlow<TutorialHintUiState> = _state.asStateFlow()

    private val _events = Channel<TutorialHintEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val topic = TutorialTopic.fromId(topicId)
        if (topic == null) {
            _state.update { it.copy(resolved = true) }
        } else {
            viewModelScope.launch {
                val expanded = !onboardingPreferences.hasSeenTutorial(topicId)
                tutorialsRepository.forTopic(topic).collect { tutorial ->
                    _state.update { it.copy(tutorial = tutorial, expanded = expanded, resolved = true) }
                }
            }
        }
    }

    fun onAction(action: TutorialHintAction) {
        when (action) {
            TutorialHintAction.OnWatch -> {
                val id = _state.value.tutorial?.id ?: return
                markSeenAndCollapse()
                viewModelScope.launch { _events.send(TutorialHintEvent.NavigateToPlayer(id)) }
            }
            TutorialHintAction.OnDismiss -> markSeenAndCollapse()
        }
    }

    private fun markSeenAndCollapse() {
        _state.update { it.copy(expanded = false) }
        viewModelScope.launch { onboardingPreferences.setTutorialSeen(topicId) }
    }
}
