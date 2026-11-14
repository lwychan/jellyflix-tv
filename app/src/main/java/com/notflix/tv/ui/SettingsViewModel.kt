package com.notflix.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notflix.tv.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {

    val prefs: Flow<SettingsStore.Prefs> = store.prefs

    fun setPreferDirectPlay(v: Boolean) = viewModelScope.launch { store.setPreferDirectPlay(v) }
    fun setTunneledPlayback(v: Boolean) = viewModelScope.launch { store.setTunneledPlayback(v) }
    fun setMatchRefreshRate(v: Boolean) = viewModelScope.launch { store.setMatchRefreshRate(v) }
}
