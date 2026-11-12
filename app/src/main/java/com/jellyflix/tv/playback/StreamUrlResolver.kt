package com.jellyflix.tv.playback

import android.content.Context
import android.media.MediaCodecList
import com.jellyflix.tv.data.JellyfinClient
import com.jellyflix.tv.data.SessionStore
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

/**
 * Asks the Jellyfin server for a playback manifest and picks the best MediaSource.
 * Direct play is preferred when the client can decode the container/codecs natively;
 * otherwise the server transcodes to HLS/DASH.
 */
@Singleton
class StreamUrlResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: JellyfinClient,
    private val session: SessionStore,
) {
    data class Stream(val url: String, val mediaSource: MediaSourceInfo, val isDirectPlay: Boolean)

    suspend fun resolve(itemId: UUID): Stream {
        val api = client.api()
        val s = session.session.first()
        val userId = UUID.fromString(requireNotNull(s.userId))
        val profile = buildDeviceProfile()

        val info = api.mediaInfoApi.getPostedPlaybackInfo(
            itemId = itemId,
            data = PlaybackInfoDto(
                userId = userId,
                autoOpenLiveStream = true,
                enableDirectPlay = true,
                enableDirectStream = true,
                enableTranscoding = true,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                deviceProfile = profile,
            ),
        ).content

        val chosen = info.mediaSources?.firstOrNull() ?: error("No media sources for $itemId")
        val direct = chosen.supportsDirectPlay == true || chosen.supportsDirectStream == true

        val baseUrl = requireNotNull(s.serverUrl)
        val token = requireNotNull(s.accessToken)
        val url = if (direct) {
            "$baseUrl/Videos/$itemId/stream?static=true&mediaSourceId=${chosen.id}&api_key=$token"
        } else {
            val transcodingUrl = chosen.transcodingUrl
                ?: "/Videos/$itemId/main.m3u8?mediaSourceId=${chosen.id}&api_key=$token"
            if (transcodingUrl.startsWith("http")) transcodingUrl else baseUrl + transcodingUrl
        }
        // Log the shape of the URL with the token redacted, to diagnose playback failures on-device.
        android.util.Log.d("JellyflixPlayer", "stream direct=$direct url=" + url.replace(token, "<token>"))
        return Stream(url = url, mediaSource = chosen, isDirectPlay = direct)
    }

    /**
     * Builds a device profile from what the device's MediaCodec list actually reports.
     * This is how we get accurate direct-play decisions instead of the generic Android profile.
     */
    private fun buildDeviceProfile(): DeviceProfile {
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        val videoCodecs = mutableSetOf<String>()
        val audioCodecs = mutableSetOf<String>()
        codecs.filterNot { it.isEncoder }.forEach { info ->
            info.supportedTypes.forEach { t ->
                when {
                    t.startsWith("video/") -> videoCodecs += t.removePrefix("video/").normalizeCodec()
                    t.startsWith("audio/") -> audioCodecs += t.removePrefix("audio/").normalizeCodec()
                }
            }
        }

        val containers = listOf("mp4", "mkv", "webm", "ts", "m4v", "mov")
        val directPlay = containers.map {
            DirectPlayProfile(
                type = DlnaProfileType.VIDEO,
                container = it,
                videoCodec = videoCodecs.joinToString(","),
                audioCodec = audioCodecs.joinToString(","),
            )
        }

        return DeviceProfile(
            name = "Jellyflix",
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
