package com.notflix.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notflix.tv.data.JellyfinClient
import com.notflix.tv.data.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repo: MediaRepository,
    private val client: JellyfinClient,
) : ViewModel() {

    data class State(
        val item: BaseItemDto? = null,
        val episodes: List<BaseItemDto> = emptyList(),
        /** "More like this": titles the server rates as similar. */
        val similar: List<BaseItemDto> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(rawId: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val id = UUID.fromString(rawId)
            val requested = repo.item(id)
            // An episode opens its show's page, as on Netflix.
            val item = if (requested.type == BaseItemKind.EPISODE && requested.seriesId != null) {
                repo.item(requested.seriesId!!)
            } else {
                requested
            }
            // The page shows as soon as the title is known; episodes and similar titles fill in after.
            _state.value = State(item = item, loading = false)
            val episodes = async { if (item.type == BaseItemKind.SERIES) fetchEpisodes(item.id) else emptyList() }
            val similar = async { repo.similar(item.id) }
            _state.update { it.copy(episodes = episodes.await(), similar = similar.await()) }
        } catch (t: Throwable) {
            _state.value = _state.value.copy(loading = false, error = t.localizedMessage)
        }
    }

    private suspend fun fetchEpisodes(seriesId: UUID): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.tvShowsApi.getEpisodes(
            seriesId = seriesId,
            userId = repo.currentUserId(),
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())
}
