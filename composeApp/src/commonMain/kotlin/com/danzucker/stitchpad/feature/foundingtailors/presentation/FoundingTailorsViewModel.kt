package com.danzucker.stitchpad.feature.foundingtailors.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.founding_tailors_share_message

private const val LINK_BASE_URL = "https://link.getstitchpad.com/r/"
private const val LEADERBOARD_BASE_URL = "https://getstitchpad.com/founding-tailors?code="

/**
 * Founding Tailors screen: surfaces the signed-in tailor's own referral link
 * (self-serve, server-minted on first use) plus a share action and a link out to
 * the public leaderboard.
 */
class FoundingTailorsViewModel(
    private val referralRepository: ReferralRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    // Defaulted so ViewModel unit tests (plain JVM, no Robolectric) can substitute a
    // resource-free fake — the production resolver calls getString(), which throws
    // "Method getSystem in android.content.res.Resources not mocked" otherwise. Mirrors
    // MeasurementDetailViewModel's shareLabelsResolver. Koin can't supply a default for
    // a constructor-ref registration, so this VM is registered via an explicit
    // `viewModel { ... }` lambda that omits this arg (see referralModule).
    private val shareMessageResolver: suspend (String) -> String = { url ->
        getString(Res.string.founding_tailors_share_message, url)
    },
) : ViewModel() {

    private val _state = MutableStateFlow(FoundingTailorsState())
    val state = _state.asStateFlow()

    private val _events = Channel<FoundingTailorsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The resolved referral code backing the current [FoundingTailorsState.referralUrl]. */
    private var code: String? = null

    fun onAction(action: FoundingTailorsAction) {
        when (action) {
            FoundingTailorsAction.LoadLink -> loadLink()
            FoundingTailorsAction.ShareLink -> shareLink()
            FoundingTailorsAction.OpenLeaderboard -> openLeaderboard()
        }
    }

    private fun loadLink() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val uid = authRepository.getCurrentUser()?.id
            if (uid == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            // A code already minted on the user doc is used directly, no network call
            // needed — getOrCreateMyReferralLink() only runs the first time, before the
            // server has ever minted one for this tailor.
            val existingCode = userRepository.observeUser(uid).first()?.referralCode
            if (existingCode != null) {
                code = existingCode
                _state.update {
                    it.copy(isLoading = false, referralUrl = LINK_BASE_URL + existingCode)
                }
                loadStanding(existingCode)
                return@launch
            }

            when (val result = referralRepository.getOrCreateMyReferralLink()) {
                is Result.Success -> {
                    code = result.data.code
                    _state.update { it.copy(isLoading = false, referralUrl = result.data.url) }
                    loadStanding(result.data.code)
                }
                is Result.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.error.toFoundingTailorsUiText()) }
                }
            }
        }
    }

    /**
     * Best-effort secondary load of the tailor's own month + lifetime points. A
     * failure is swallowed (the card stays hidden) so it never blocks the link,
     * share, or leaderboard actions.
     */
    private suspend fun loadStanding(code: String) {
        _state.update { it.copy(isStandingLoading = true) }
        when (val result = referralRepository.getFoundingTailorsStanding(code)) {
            is Result.Success -> _state.update { it.copy(isStandingLoading = false, standing = result.data) }
            // Leave standing null and never surface an error; just stop the placeholder.
            is Result.Error -> _state.update { it.copy(isStandingLoading = false) }
        }
    }

    private fun shareLink() {
        val url = _state.value.referralUrl ?: return
        viewModelScope.launch {
            _events.send(FoundingTailorsEvent.ShareText(shareMessageResolver(url)))
        }
    }

    private fun openLeaderboard() {
        val currentCode = code ?: return
        viewModelScope.launch {
            _events.send(FoundingTailorsEvent.OpenUrl(LEADERBOARD_BASE_URL + currentCode))
        }
    }
}
