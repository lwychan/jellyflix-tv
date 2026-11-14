package com.notflix.tv.playback

import android.content.Context
import android.media.MediaCodecList
import com.notflix.tv.data.JellyfinClient
import com.notflix.tv.data.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlaybackInfoDto

/** How the player fetches the title. */
enum class StreamRoute {
    /** The raw file, byte for byte. Only when the server says the TV can play it as it is. */
    DIRECT_FILE,

    /**
     * The server's own HLS stream: a remux when only the container needs changing, or a
     * transcode when a codec has to change. Used whenever the raw file is not safe to play.
     */
    SERVER_HLS;

    companion object {
        /**
         * Raw file only when direct play is allowed and the server says the file plays as it is.
         * A server that offers only DirectStream (remux or audio conversion) gets SERVER_HLS: the
         * raw file would be served, and a TV that can't decode one stream stalls after the first frame.
         */
        fun choose(allowDirect: Boolean, supportsDirectPlay: Boolean): StreamRoute =
            if (allowDirect && supportsDirectPlay) DIRECT_FILE else SERVER_HLS

        /** The URL for a route. [transcodingUrl] is the server's own path, if it gave one. */
        fun url(
            route: StreamRoute,
            baseUrl: String,
            itemId: UUID,
            mediaSourceId: String?,
            token: String,
            transcodingUrl: String?,
        ): String = when (route) {
            DIRECT_FILE ->
                "$baseUrl/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId&api_key=$token"
            SERVER_HLS -> {
                val path = transcodingUrl
                    ?: "/Videos/$itemId/main.m3u8?mediaSourceId=$mediaSourceId&api_key=$token"
                if (path.startsWith("http")) path else baseUrl + path
            }
        }
    }
}

/**
 * Asks the Jellyfin server for a playback manifest and picks the stream route.
 * The raw file is used only when direct play is allowed and the server confirms it.
 */
@Singleton
class StreamUrlResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: JellyfinClient,
    private val session: SessionStore,
) {
    data class Stream(val url: String, val mediaSource: MediaSourceInfo, val isDirectPlay: Boolean)

    /**
     * @param allowDirect false when the user has turned direct play off, or after a stall
     *   fallback. The server is then asked for HLS only.
     */
    suspend fun resolve(itemId: UUID, allowDirect: Boolean = true): Stream {
        val api = client.api()
        val s = session.session.first()
        val userId = UUID.fromString(requireNotNull(s.userId))
        val profile = buildDeviceProfile(allowDirect)

        val info = api.mediaInfoApi.getPostedPlaybackInfo(
            itemId = itemId,
            data = PlaybackInfoDto(
                userId = userId,
                autoOpenLiveStream = true,
                enableDirectPlay = allowDirect,
                enableDirectStream = true,
                enableTranscoding = true,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                deviceProfile = profile,
            ),
        ).content

        val chosen = info.mediaSources?.firstOrNull() ?: error("No media sources for $itemId")
        val route = StreamRoute.choose(allowDirect, chosen.supportsDirectPlay == true)

        val baseUrl = requireNotNull(s.serverUrl)
        val token = requireNotNull(s.accessToken)
        val url = StreamRoute.url(
            route = route,
            baseUrl = baseUrl,
            itemId = itemId,
            mediaSourceId = chosen.id,
            token = token,
            transcodingUrl = chosen.transcodingUrl,
        )
        // Log the shape of the URL with the token redacted, to diagnose playback failures on-device.
        android.util.Log.d(
            "NotflixPlayer",
            "stream route=$route allowDirect=$allowDirect supportsDirectPlay=${chosen.supportsDirectPlay} " +
                "supportsDirectStream=${chosen.supportsDirectStream} url=" + url.replace(token, "<token>"),
        )
        return Stream(url = url, mediaSource = chosen, isDirectPlay = route == StreamRoute.DIRECT_FILE)
    }

    /**
     * Builds a device profile from the hardware video decoders and all audio decoders the
     * device reports. Software decoders are left out, because they are not a reliable
     * reason to serve a file raw.
     */
    private fun buildDeviceProfile(allowDirect: Boolean): DeviceProfile {
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        val videoCodecs = mutableSetOf<String>()
        val audioCodecs = mutableSetOf<String>()
        codecs.filterNot { it.isEncoder }.forEach { info ->
            val software = isSoftwareDecoder(info.name)
            info.supportedTypes.forEach { t ->
                when {
                    t.startsWith("video/") && !software -> videoCodecs += t.removePrefix("video/").normalizeCodec()
                    t.startsWith("audio/") -> audioCodecs += t.removePrefix("audio/").normalizeCodec()
                }
            }
        }

        val containers = listOf("mp4", "mkv", "webm", "ts", "m4v", "mov")
        val directPlay = if (!allowDirect) emptyList() else containers.map {
            DirectPlayProfile(
                type = DlnaProfileType.VIDEO,
                container = it,
                videoCodec = videoCodecs.joinToString(","),
                audioCodec = audioCodecs.joinToString(","),
            )
        }

        return DeviceProfile(
            name = "Notflix",
            maxStaticBitrate = 120_000_000,
            maxStreamingBitrate = 120_000_000,
            musicStreamingTranscodingBitrate = 384_000,
            directPlayProfiles = directPlay,
            transcodingProfiles = emptyList(),
            containerProfiles = emptyList(),
            codecProfiles = emptyList(),
            subtitleProfiles = emptyList(),
        )
    }

    /** Google's and the Codec2 software decoders. Other names are the device's own hardware codecs. */
    private fun isSoftwareDecoder(name: String): Boolean =
        name.startsWith("OMX.google.") || name.startsWith("c2.android.")

    private fun String.normalizeCodec(): String = when (this) {
        "avc" -> "h264"
        "hevc" -> "hevc"
        "x-vnd.on2.vp9" -> "vp9"
        "av01" -> "av1"
        "mp4a-latm" -> "aac"
        "eac3-joc" -> "eac3"
        else -> this
    }
}
