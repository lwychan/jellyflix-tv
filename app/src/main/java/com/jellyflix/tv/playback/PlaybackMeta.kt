package com.jellyflix.tv.playback

import com.jellyflix.tv.data.MediaRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentType

/** A stretch of the video the server has marked, e.g. the intro, in milliseconds. */
data class Span(val type: MediaSegmentType, val startMs: Long, val endMs: Long)

/** Everything the player shows about the title it is playing. */
data class NowPlaying(
    val item: BaseItemDto,
    /** Every episode of the show in season order. Empty for movies. */
    val episodes: List<BaseItemDto>,
    /** The episode after this one, if any. */
    val next: BaseItemDto?,
    /** Intro, recap and credits ranges. */
    val segments: List<Span>,
    val chapterStartsMs: List<Long>,
    /** Show name for an episode, otherwise the title. */
    val heading: String,
    /** "S5: E10 “Chapter 62”" for an episode, otherwise null. */
    val subheading: String?,
)

private val EpisodeOrder = compareBy<BaseItemDto>(
    { it.parentIndexNumber ?: Int.MAX_VALUE },
    { it.indexNumber ?: Int.MAX_VALUE },
)

/** Where playback of [this] should start: its saved position, or 0. */
fun BaseItemDto.startPositionMs(): Long = (userData?.playbackPositionTicks ?: 0L) / 10_000L

/** Loads what the overlay needs for [id]. Returns null if the server can't be reached. */
suspend fun loadNowPlaying(repo: MediaRepository, id: UUID): NowPlaying? {
    val item = repo.detailed(id) ?: return null
    val isEpisode = item.type == BaseItemKind.EPISODE
    val episodes = if (isEpisode) {
        item.seriesId?.let { repo.episodesOf(it) }.orEmpty().sortedWith(EpisodeOrder)
    } else {
        emptyList()
    }
    val at = episodes.indexOfFirst { it.id == id }
    val next = if (at >= 0) episodes.getOrNull(at + 1) else null
    val segments = repo.segments(id)
        .map { Span(it.type, it.startTicks / 10_000L, it.endTicks / 10_000L) }
        .filter { it.endMs > it.startMs }
    val chapters = item.chapters.orEmpty().map { it.startPositionTicks / 10_000L }

    val heading = if (isEpisode) item.seriesName ?: item.name.orEmpty() else item.name.orEmpty()
    val sub = if (isEpisode) {
        val season = item.parentIndexNumber?.let { "S$it" }.orEmpty()
        val number = item.indexNumber?.let { "E$it" }.orEmpty()
        val code = listOf(season, number).filter { it.isNotEmpty() }.joinToString(": ")
        listOfNotNull(code.ifEmpty { null }, item.name?.let { "“$it”" }).joinToString(" ")
    } else {
        null
    }
    return NowPlaying(
        item = item,
        episodes = episodes,
        next = next,
        segments = segments,
        chapterStartsMs = chapters,
        heading = heading,
        subheading = sub?.ifEmpty { null },
    )
}
