package com.notflix.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User-tunable playback prefs. Each toggle is read by the player; default ON, so users
 * with a quirky TV can turn one off.
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val preferDirectPlayKey = booleanPreferencesKey("prefer_direct_play")
    private val tunneledPlaybackKey = booleanPreferencesKey("tunneled_playback")
    private val matchRefreshRateKey = booleanPreferencesKey("match_refresh_rate")

    data class Prefs(
        val preferDirectPlay: Boolean = true,
        val tunneledPlayback: Boolean = false,
        val matchRefreshRate: Boolean = true,
    )

    val prefs: Flow<Prefs> = ctx.settingsDataStore.data.map { p ->
        Prefs(
            preferDirectPlay = p[preferDirectPlayKey] ?: true,
            tunneledPlayback = p[tunneledPlaybackKey] ?: false,
            matchRefreshRate = p[matchRefreshRateKey] ?: true,
        )
    }

    suspend fun setPreferDirectPlay(v: Boolean) = ctx.settingsDataStore.edit { it[preferDirectPlayKey] = v }
    suspend fun setTunneledPlayback(v: Boolean) = ctx.settingsDataStore.edit { it[tunneledPlaybackKey] = v }
    suspend fun setMatchRefreshRate(v: Boolean) = ctx.settingsDataStore.edit { it[matchRefreshRateKey] = v }
}
