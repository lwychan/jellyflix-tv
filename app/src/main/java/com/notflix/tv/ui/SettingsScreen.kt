package com.notflix.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.notflix.tv.data.SettingsStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    serverUrl: String?,
    username: String?,
    onSwitchProfile: () -> Unit,
    onAddProfile: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val prefs by vm.prefs.collectAsState(initial = SettingsStore.Prefs())

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(bottom = 24.dp))
            }

            item { SectionHeader("Playback") }
            item {
                ToggleRow(
                    label = "Prefer direct play",
                    subtitle = "Skip the server transcoder when the TV can decode the source.",
                    checked = prefs.preferDirectPlay,
                    onCheckedChange = vm::setPreferDirectPlay,
                )
            }
            item {
                ToggleRow(
                    label = "Tunneled decoding",
                    subtitle = "Lower latency. Disable if playback flickers on your TV.",
                    checked = prefs.tunneledPlayback,
                    onCheckedChange = vm::setTunneledPlayback,
                )
            }
            item {
                ToggleRow(
                    label = "Match display refresh rate",
                    subtitle = "Switches the panel to 24 / 25 / 50 Hz on matching content.",
                    checked = prefs.matchRefreshRate,
                    onCheckedChange = vm::setMatchRefreshRate,
                )
            }

            item { SectionHeader("Server and profile") }
            item {
                Text(
                    "Server: ${serverUrl ?: "not set"}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Profile: ${username ?: "none signed in"}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    androidx.tv.material3.Button(onClick = onSwitchProfile) {
                        Text("Switch profile", style = MaterialTheme.typography.titleLarge)
                    }
                    androidx.tv.material3.Button(onClick = onAddProfile) {
                        Text("Add profile", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    // Small caps label over a hairline, so each group reads as a section, not another setting row.
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp)) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
            color = Color(0xFFB3B3B3),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
    }
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


