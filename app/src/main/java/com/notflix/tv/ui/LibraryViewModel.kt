package com.notflix.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notflix.tv.data.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: MediaRepository,
) : ViewModel() {

    data class State(val title: String = "", val items: List<BaseItemDto> = emptyList(), val loading: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(libraryId: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        val id = UUID.fromString(libraryId)
        val items = runCatching { repo.itemsIn(id) }.getOrDefault(emptyList())
        val title = runCatching { repo.item(id).name }.getOrNull().orEmpty()
        _state.value = State(title = title, items = items, loading = false)
    }
}
