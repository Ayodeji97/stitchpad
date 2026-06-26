package com.danzucker.stitchpad.feature.tutorials.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import com.danzucker.stitchpad.feature.tutorials.domain.TutorialUriResolver
import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.repository.TutorialsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val CATALOG_TIMEOUT_MS = 5_000L

class TutorialPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val tutorialsRepository: TutorialsRepository,
    private val mediaResolver: TutorialUriResolver,
    private val onboardingPreferences: OnboardingPreferencesStore,
) : ViewModel() {

    private val tutorialId: String = savedStateHandle["tutorialId"] ?: ""

    private val _state = MutableStateFlow(TutorialPlayerState())
    val state: StateFlow<TutorialPlayerState> = _state.asStateFlow()

    private val _events = Channel<TutorialPlayerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: TutorialPlayerAction) {
        when (action) {
            TutorialPlayerAction.OnClose ->
                viewModelScope.launch { _events.send(TutorialPlayerEvent.NavigateBack) }
            TutorialPlayerAction.OnRetry -> _state.value.tutorial?.let { resolve(it) } ?: load()
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            // The catalog flow emits the bundled fallback first, then remote; wait for the
            // matching tutorial (covers remote-only library clips) with a timeout guard so an
            // unknown id surfaces an error instead of hanging on the hot Firestore flow.
            val tutorial = withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                tutorialsRepository.tutorial(tutorialId).firstOrNull { it != null }
            }
            if (tutorial == null) {
                _state.update { it.copy(isLoading = false, hasError = true) }
                return@launch
            }
            // Opening the player counts as "seen" so the contextual hint collapses to a link.
            tutorial.topic?.let { onboardingPreferences.setTutorialSeen(it.id) }
            _state.update { it.copy(tutorial = tutorial) }
            resolve(tutorial)
        }
    }

    private fun resolve(tutorial: Tutorial) {
        _state.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            val uri = mediaResolver.resolvePlayableUri(tutorial)
            _state.update {
                if (uri == null) {
                    it.copy(isLoading = false, hasError = true)
                } else {
                    it.copy(isLoading = false, playableUri = uri, hasError = false)
                }
            }
        }
    }
}
