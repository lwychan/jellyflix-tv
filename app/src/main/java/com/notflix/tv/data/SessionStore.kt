package com.notflix.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/**
 * Persists the server URL, user id, and access token.
 * Token lives here rather than in SharedPreferences so it is excluded from cloud backup.
 */
@Singleton
/** The Jellyfin server this app always uses. The address is fixed in the build. */
const val DEFAULT_SERVER_URL = "http://192.168.1.153:8096"

class SessionStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val serverKey = stringPreferencesKey("server_url")
    private val userIdKey = stringPreferencesKey("user_id")
    private val tokenKey = stringPreferencesKey("access_token")
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val usernameKey = stringPreferencesKey("username")

    val session: Flow<Session> = ctx.sessionDataStore.data.map { prefs ->
        Session(
            // The Jellyfin server is fixed. Every read sees this address, so no screen asks for it.
            serverUrl = DEFAULT_SERVER_URL,
            userId = prefs[userIdKey],
            accessToken = prefs[tokenKey],
            deviceId = prefs[deviceIdKey],
            username = prefs[usernameKey],
        )
    }

    suspend fun setServer(url: String) = ctx.sessionDataStore.edit { it[serverKey] = url }

    suspend fun setAuth(userId: String, token: String, username: String) = ctx.sessionDataStore.edit {
        it[userIdKey] = userId
        it[tokenKey] = token
        it[usernameKey] = username
    }

    suspend fun setDeviceId(id: String) = ctx.sessionDataStore.edit { it[deviceIdKey] = id }

    /** Drops the signed-in profile but keeps the server address, so the picker shows next. */
    suspend fun clearAuth() = ctx.sessionDataStore.edit {
        it.remove(userIdKey)
        it.remove(tokenKey)
        it.remove(usernameKey)
    }


    data class Session(
        val serverUrl: String?,
        val userId: String?,
        val accessToken: String?,
        val deviceId: String?,
        val username: String? = null,
    ) {
        val isAuthenticated: Boolean get() = !serverUrl.isNullOrBlank() && !accessToken.isNullOrBlank()
        val hasServer: Boolean get() = !serverUrl.isNullOrBlank()
    }
}
