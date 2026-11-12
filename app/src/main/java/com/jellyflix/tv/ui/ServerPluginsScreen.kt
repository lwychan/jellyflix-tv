package com.jellyflix.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellyflix.tv.data.ServerInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PluginInfo

@HiltViewModel
class ServerPluginsViewModel @Inject constructor(
    private val repo: ServerInfoRepository,
) : ViewModel() {
    data class State(
        val plugins: List<PluginInfo> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        repo.serverPlugins().fold(
            onSuccess = { _state.value = State(plugins = it, loading = false) },
            onFailure = {
                // A 403 means the account is not a server administrator, which is the usual cause.
                val message = if (it.localizedMessage?.contains("403") == true) {
                    "Only server administrators can list plugins."
                } else {
                    it.localizedMessage ?: "Failed"
                }
                _state.value = State(loading = false, error = message)
            },
        )
    }
}

@Composable
fun ServerPluginsScreen(vm: ServerPluginsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Jellyfin server plugins", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "These plugins run on your Jellyfin server. They're separate from Jellyflix TV plugins — you manage them from the server's web dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            when {
                state.loading -> item {
                    Text("Loading…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error != null -> item {
                    Text(
                        "Couldn't load plugins: ${state.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Most servers require admin privileges to list plugins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                state.plugins.isEmpty() -> item {
                    Text("No plugins installed on the server.", style = MaterialTheme.typography.bodyLarge)
                }
                else -> items(state.plugins, key = { it.id.toString() }) { p -> PluginRow(p) }
            }
        }
    }
}

@Composable
private fun PluginRow(p: PluginInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text("${p.name}  v${p.version}", style = MaterialTheme.typography.titleLarge)
        p.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Status: ${p.status?.name?.lowercase() ?: "unknown"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
