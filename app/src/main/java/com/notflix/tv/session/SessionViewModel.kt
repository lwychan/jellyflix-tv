package com.notflix.tv.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notflix.tv.data.JellyfinClient
import com.notflix.tv.data.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import timber.log.Timber

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val store: SessionStore,
    private val client: JellyfinClient,
) : ViewModel() {

    enum class Stage { Loading, NeedsServer, NeedsLogin, Authenticated }

    data class State(
        val stage: Stage = Stage.Loading,
        val serverUrl: String? = null,
        val userId: String? = null,
        val username: String? = null,
        /** A one-line message for the profile picker, e.g. why a sign-in did not go through. */
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val s = store.session.first()
        _state.value = State(
            stage = when {
                s.isAuthenticated -> Stage.Authenticated
                s.hasServer -> Stage.NeedsLogin
                else -> Stage.NeedsServer
            },
            serverUrl = s.serverUrl?.takeIf { it.isNotBlank() },
            userId = s.userId,
            username = s.username,
        )
    }

    /** Signs the current profile out and goes to the picker. The server address is kept. */
    fun switchProfile() = viewModelScope.launch {
        store.clearAuth()
        client.invalidate()
        refresh()
    }

    /**
     * Signs a profile in with an empty password (profiles without one) and goes straight to Home.
     * Calls [onFailed] if the server refuses, so the caller can ask for a password instead.
     */
    fun signInProfile(username: String, onFailed: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(notice = null) }
        try {
            val s = store.session.first()
            val baseUrl = requireNotNull(s.serverUrl) { "No server URL set" }
            val result = client.unauthenticated(baseUrl).userApi.authenticateUserByName(
                data = AuthenticateUserByName(username = username, pw = "")
            ).content
            val userId = result.user?.id?.toString() ?: error("No user id")
            val token = result.accessToken ?: error("No access token")
            store.setAuth(userId, token, result.user?.name ?: username)
            client.invalidate()
            refresh()
        } catch (t: Throwable) {
            Timber.w(t, "Profile sign-in failed")
            if (t is IOException) {
                // The server could not be reached. Say so, rather than asking for a password.
                _state.update { it.copy(notice = "Couldn't reach the server to sign in. Check the connection and try again.") }
            } else {
                // The server answered and refused the empty password, so this profile needs one.
                onFailed()
            }
        }
    }


}
