package com.notflix.tv.data

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/**
 * Builds image URLs for items. Avoids depending on a connected ApiClient so
 * it can be called from composables without suspending. Auth token is included
 * as `api_key` query param — Jellyfin servers configured to require auth for
 * images will serve a low-res placeholder without it.
 */
object ImageUrls {
    @Volatile var baseUrl: String? = null
    @Volatile var accessToken: String? = null

    private fun auth(): String = accessToken?.let { "&api_key=$it" } ?: ""

    fun primary(item: BaseItemDto, maxWidth: Int = 480): String? {
        val base = baseUrl ?: return null
        val tag = item.imageTags?.get(ImageType.PRIMARY) ?: return null
        return "$base/Items/${item.id}/Images/Primary?maxWidth=$maxWidth&quality=90&tag=$tag${auth()}"
    }

    fun backdrop(item: BaseItemDto, maxWidth: Int = 1920): String? {
        val base = baseUrl ?: return null
        val tag = item.backdropImageTags?.firstOrNull()
            ?: item.parentBackdropImageTags?.firstOrNull()
            ?: return null
        val id = item.backdropImageTags?.firstOrNull()?.let { item.id }
            ?: item.parentBackdropItemId
            ?: item.id
        return "$base/Items/$id/Images/Backdrop?maxWidth=$maxWidth&quality=85&tag=$tag${auth()}"
    }

    fun logo(item: BaseItemDto, maxWidth: Int = 640): String? {
        val base = baseUrl ?: return null
        val tag = item.imageTags?.get(ImageType.LOGO) ?: return null
        return "$base/Items/${item.id}/Images/Logo?maxWidth=$maxWidth&quality=90&tag=$tag${auth()}"
    }
}
