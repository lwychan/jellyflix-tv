package com.jellyflix.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyflix.tv.data.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: MediaRepository,
) : ViewModel() {

    data class State(
        val continueWatching: List<BaseItemDto> = emptyList(),
        val nextUp: List<BaseItemDto> = emptyList(),
        val libraries: List<BaseItemDto> = emptyList(),
        val movies: List<BaseItemDto> = emptyList(),
        val shows: List<BaseItemDto> = emptyList(),
        val recentShows: List<BaseItemDto> = emptyList(),
        val recentMovies: List<BaseItemDto> = emptyList(),
        val searchQuery: String = "",
        val searchResults: List<BaseItemDto> = emptyList(),
        val searching: Boolean = false,
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { load() }

    fun load() = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(loading = true, error = null) }
        try {
            // Fire all server calls in parallel — on the Chromecast puck
            // the serial round-trip cost was ~900ms on a cold home.
            val libsJob = async { runCatching { repo.libraries() }.getOrDefault(emptyList()) }
            val cwJob = async { runCatching { repo.continueWatching() }.getOrDefault(emptyList()) }
            val nuJob = async { runCatching { repo.nextUp() }.getOrDefault(emptyList()) }
            val moviesJob = async { repo.allOfKind(BaseItemKind.MOVIE) }
            val showsJob = async { repo.allOfKind(BaseItemKind.SERIES) }
            val recentShowsJob = async { repo.recentlyAdded(BaseItemKind.SERIES) }
            val recentMoviesJob = async { repo.recentlyAdded(BaseItemKind.MOVIE) }
            _state.update {
                it.copy(
                    continueWatching = cwJob.await(),
                    nextUp = nuJob.await(),
                    libraries = libsJob.await(),
                    movies = moviesJob.await(),
                    shows = showsJob.await(),
                    recentShows = recentShowsJob.await(),
                    recentMovies = recentMoviesJob.await(),
                    loading = false,
                )
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to load home")
            _state.update { it.copy(loading = false, error = t.localizedMessage) }
        }
    }

    /** Updates the query immediately and searches after a short pause in typing. */
    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            if (query.isBlank()) {
                _state.update { it.copy(searchResults = emptyList(), searching = false) }
                return@launch
            }
            delay(350)
            _state.update { it.copy(searching = true) }
            val results = runCatching { repo.search(query.trim()) }.getOrDefault(emptyList())
            _state.update { it.copy(searchResults = results, searching = false) }
        }
    }
}
