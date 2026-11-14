package com.notflix.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notflix.tv.data.BrandingRepository
import com.notflix.tv.data.JellyfinClient
import com.notflix.tv.data.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import timber.log.Timber

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val client: JellyfinClient,
    private val store: SessionStore,
    brandingRepo: BrandingRepository,
) : ViewModel() {

    val branding: Flow<BrandingRepository.Branding> = brandingRepo.state

    sealed interface State {
        data object Idle : State
        data object Submitting : State
        data class Error(val message: String) : State
        data object Success : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun signIn(username: String, password: String) = viewModelScope.launch {
        _state.value = State.Submitting
        try {
            val s = store.session.first()
            val baseUrl = requireNotNull(s.serverUrl) { "No server URL set" }
            val api = client.unauthenticated(baseUrl)
            val result = api.userApi.authenticateUserByName(
                data = AuthenticateUserByName(username = username, pw = password)
            ).content

            val userId = result.user?.id?.toString()
                ?: return@launch fail("Server did not return a user id")
            val token = result.accessToken
                ?: return@launch fail("Server did not return an access token")

            store.setAuth(userId, token, result.user?.name ?: username)
            client.invalidate()
            _state.value = State.Success
        } catch (t: Throwable) {
            Timber.w(t, "Login failed")
            fail(t.localizedMessage ?: "Sign-in failed")
        }
    }

    private fun fail(msg: String) { _state.value = State.Error(msg) }
}
