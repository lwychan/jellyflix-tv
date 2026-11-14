package com.notflix.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notflix.tv.data.BrandingRepository
import com.notflix.tv.data.JellyfinClient
import com.notflix.tv.data.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi

@HiltViewModel
class UserPickerViewModel @Inject constructor(
    private val client: JellyfinClient,
    private val store: SessionStore,
    private val branding: BrandingRepository,
) : ViewModel() {

    data class PublicUser(
        val id: UUID,
        val name: String,
        val avatarUrl: String?,
        val hasPassword: Boolean,
    )

    data class State(
        val serverName: String? = null,
        val splashscreenUrl: String? = null,
        val accentColor: Long? = null,
        val users: List<PublicUser> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            branding.refresh()
            branding.state.collect { b ->
                _state.value = _state.value.copy(
                    serverName = b.serverName ?: _state.value.serverName,
                    splashscreenUrl = b.splashscreenUrl,
                    accentColor = b.accentColor,
                )
            }
        }
    }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        val s = store.session.first()
        val baseUrl = s.serverUrl ?: return@launch fail("No server configured")
        try {
            val api = client.unauthenticated(baseUrl)
            val info = runCatching { api.systemApi.getPublicSystemInfo().content }.getOrNull()
            info?.serverName?.let(branding::setServerName)

            val users = runCatching { api.userApi.getPublicUsers().content }
                .getOrDefault(emptyList())
                .map { u ->
                    val id = u.id
                    val tag = u.primaryImageTag
                    PublicUser(
                        id = id,
                        name = u.name.orEmpty().ifBlank { "User" },
                        avatarUrl = tag?.let { "$baseUrl/Users/$id/Images/Primary?tag=$it&quality=85&maxWidth=320" },
                        hasPassword = u.hasPassword == true,
                    )
                }
            _state.value = _state.value.copy(
                serverName = info?.serverName,
                users = users,
                loading = false,
            )
        } catch (t: Throwable) {
            fail(t.localizedMessage ?: "Failed to reach server")
        }
    }

    private fun fail(msg: String) {
        _state.value = _state.value.copy(loading = false, error = msg)
    }
}
