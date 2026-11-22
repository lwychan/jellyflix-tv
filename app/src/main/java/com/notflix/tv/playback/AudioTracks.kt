package com.notflix.tv.playback

/** One audio track of a title on the server, by its server stream index. */
data class ServerAudio(
    val index: Int,
    val language: String?,
    val label: String,
    /** Narration for the blind and visually impaired. Never the default choice. */
    val descriptive: Boolean,
)

/**
 * The server audio track to start with: the first English track that is not descriptive, else
 * the server's own default. Null when there is nothing to choose, so the server picks.
 */
fun preferredAudioIndex(tracks: List<ServerAudio>, serverDefault: Int?): Int? =
    tracks.firstOrNull { it.language == "eng" && !it.descriptive }?.index ?: serverDefault

/** [url] with `AudioStreamIndex=[index]` in its query, replacing any value already there. */
fun withAudioStreamIndex(url: String, index: Int): String {
    val base = url.substringBefore('?')
    val query = url.substringAfter('?', "")
    val params = query.split('&')
        .filter { it.isNotEmpty() && !it.startsWith("AudioStreamIndex=") } + "AudioStreamIndex=$index"
    return "$base?${params.joinToString("&")}"
}
