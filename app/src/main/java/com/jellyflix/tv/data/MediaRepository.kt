package com.jellyflix.tv.data

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.SortOrder

@Singleton
class MediaRepository @Inject constructor(
    private val client: JellyfinClient,
    private val session: SessionStore,
) {

    suspend fun currentUserId(): UUID {
        val s = session.session.first()
        return UUID.fromString(requireNotNull(s.userId) { "Not authenticated" })
    }

    suspend fun libraries(): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.userViewsApi.getUserViews(userId = currentUserId()).content.items.orEmpty()
    }.getOrDefault(emptyList())

    suspend fun continueWatching(limit: Int = 20): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.itemsApi.getResumeItems(
            userId = currentUserId(),
            limit = limit,
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())

    suspend fun nextUp(limit: Int = 20): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.tvShowsApi.getNextUp(
            userId = currentUserId(),
            limit = limit,
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())

    suspend fun itemsIn(
        libraryId: UUID,
        start: Int = 0,
        limit: Int = 60,
        kinds: List<BaseItemKind> = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
    ): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.itemsApi.getItems(
            userId = currentUserId(),
            parentId = libraryId,
            startIndex = start,
            limit = limit,
            includeItemTypes = kinds,
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())

    /** The signed-in user's favorites, shown as "My List". */
    suspend fun favorites(limit: Int = 200): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.itemsApi.getItems(
            userId = currentUserId(),
            isFavorite = true,
            recursive = true,
            limit = limit,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())

    /** Every movie or every series in the library, sorted by name, for the Movies and TV Shows tabs. */
    suspend fun allOfKind(kind: BaseItemKind, limit: Int = 1000): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.itemsApi.getItems(
            userId = currentUserId(),
            recursive = true,
            limit = limit,
            includeItemTypes = listOf(kind),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())

    /** Library-wide title search (movies and series). */
    suspend fun search(term: String, limit: Int = 60): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.itemsApi.getItems(
            userId = currentUserId(),
            searchTerm = term,
            recursive = true,
            limit = limit,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())

    suspend fun item(id: UUID): BaseItemDto {
        val api = client.api()
        return api.userLibraryApi.getItem(userId = currentUserId(), itemId = id).content
    }

    /** One item with the fields the player needs: chapters and synopsis. */
    suspend fun detailed(id: UUID): BaseItemDto? = runCatching {
        val api = client.api()
        api.itemsApi.getItems(
            userId = currentUserId(),
            ids = listOf(id),
            fields = listOf(ItemFields.CHAPTERS, ItemFields.OVERVIEW),
        ).content.items.orEmpty().firstOrNull()
    }.getOrNull()

    /** Every episode of a series, with synopses. The caller sorts them. */
    suspend fun episodesOf(seriesId: UUID): List<BaseItemDto> = runCatching {
        val api = client.api()
        api.tvShowsApi.getEpisodes(
            seriesId = seriesId,
            userId = currentUserId(),
            fields = listOf(ItemFields.OVERVIEW),
        ).content.items.orEmpty()
    }.getOrDefault(emptyList())

    /** Intro, outro and recap ranges the server has detected (Intro Skipper or similar). */
    suspend fun segments(itemId: UUID): List<MediaSegmentDto> = runCatching {
        client.api().mediaSegmentsApi.getItemSegments(itemId = itemId).content.items.orEmpty()
    }.getOrDefault(emptyList())
}
