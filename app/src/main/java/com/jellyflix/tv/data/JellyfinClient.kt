package com.jellyflix.tv.data

import android.content.Context
import android.os.Build
import com.jellyflix.tv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

/**
 * Thin wrapper around the Jellyfin Kotlin SDK that binds session state
 * (server URL, access token) to a single reusable ApiClient.
 */
@Singleton
class JellyfinClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionStore,
) {
    private val jellyfin: Jellyfin = run {
        val appCtx = context
        createJellyfin {
            clientInfo = ClientInfo(name = BuildConfig.CLIENT_NAME, version = BuildConfig.CLIENT_VERSION)
            // jellyfin-core's Android target demands a Context at init time. We set it
            // directly rather than via jellyfin-platform-android, whose 1.0.3 version
            // is incompatible with core 1.6.x (stale DiscoveryBroadcastAddressesProvider
            // reference).
            this.context = appCtx
        }
    }

    @Volatile private var cached: ApiClient? = null

    suspend fun api(): ApiClient {
        cached?.let { return it }
        val s = session.session.first()
        val client = jellyfin.createApi(
            baseUrl = s.serverUrl,
            accessToken = s.accessToken,
            deviceInfo = DeviceInfo(id = s.deviceId ?: defaultDeviceId(), name = "Jellyflix-${Build.MODEL}"),
        )
        cached = client
        ImageUrls.baseUrl = s.serverUrl
        ImageUrls.accessToken = s.accessToken
        return client
    }

    fun invalidate() { cached = null }

    /**
     * The Authorization header the SDK sends on API calls, for requests made outside the SDK
     * (the video player). Playback URLs that carry only api_key in the query are refused by
     * the server with 401, so the player sends the same header instead.
     */
    suspend fun playbackAuthHeader(): String? {
        val s = session.session.first()
        val token = s.accessToken ?: return null
        val id = s.deviceId ?: defaultDeviceId()
        return "MediaBrowser Client=\"Jellyflix\", Device=\"Jellyflix-${Build.MODEL}\", " +
            "DeviceId=\"$id\", Version=\"0.1.0\", Token=\"$token\""
    }

    private fun defaultDeviceId(): String = "jellyflix-${Build.MODEL}-${Build.ID}".take(64)

    /** Builder used before auth — the server URL is known but no token yet. */
    suspend fun unauthenticated(baseUrl: String): ApiClient =
        jellyfin.createApi(baseUrl = baseUrl)
}
