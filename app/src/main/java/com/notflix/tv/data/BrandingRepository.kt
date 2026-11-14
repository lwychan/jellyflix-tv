package com.notflix.tv.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.extensions.brandingApi

/**
 * Loads and caches the server's branding configuration — login disclaimer,
 * custom CSS, splashscreen image. A best-effort parser pulls an accent color
 * out of the custom CSS so the client can respect admin-set theme.
 */
@Singleton
class BrandingRepository @Inject constructor(
    private val client: JellyfinClient,
    private val session: SessionStore,
) {
    data class Branding(
        val serverName: String? = null,
        val splashscreenUrl: String? = null,
        val loginDisclaimer: String? = null,
        val customCss: String? = null,
        /** Parsed from customCss — first #RRGGBB or #RRGGBBAA following an `--accent-color`/`primary` hint. */
        val accentColor: Long? = null,
    )

    private val _state = MutableStateFlow(Branding())
    val state: StateFlow<Branding> = _state.asStateFlow()

    suspend fun refresh() {
        val s = session.session.first()
        val base = s.serverUrl ?: return

        // Try unauthenticated first — branding is public.
        val api = runCatching { client.api() }.getOrNull()
            ?: runCatching { client.unauthenticated(base) }.getOrNull()
            ?: return

        val branding = runCatching {
            api.brandingApi.getBrandingOptions().content
        }.getOrNull()

        val accent = branding?.customCss?.let(::parseAccentColor)
        val splash = if (withContext(Dispatchers.IO) { hasSplashscreen(base) }) "$base/Branding/Splashscreen" else null

        _state.value = Branding(
            serverName = _state.value.serverName,
            splashscreenUrl = splash,
            loginDisclaimer = branding?.loginDisclaimer?.takeIf { it.isNotBlank() },
            customCss = branding?.customCss?.takeIf { it.isNotBlank() },
            accentColor = accent,
        )
    }

    fun setServerName(name: String?) {
        _state.value = _state.value.copy(serverName = name?.takeIf { it.isNotBlank() })
    }

    private fun hasSplashscreen(base: String): Boolean = runCatching {
        // Cheap probe — Jellyfin returns 404 when no splashscreen is configured.
        java.net.URL("$base/Branding/Splashscreen").openConnection().apply {
            connectTimeout = 1_000
            readTimeout = 1_000
        }.getHeaderField(null)?.contains("200") == true
    }.getOrDefault(false)

    private val colorHexRe = Regex("#([0-9a-fA-F]{6,8})")
    private val accentHints = listOf(
        "--accent-color", "--theme-primary-color", "--primary-color",
        "--color-primary", "--brand-color", "primary-background",
    )

    private fun parseAccentColor(css: String): Long? {
        // Look for a color literal on the same line as a known accent hint.
        css.lineSequence().forEach { line ->
            val hinted = accentHints.any { line.contains(it, ignoreCase = true) }
            if (hinted) {
                colorHexRe.find(line)?.value?.let { return parseHex(it) }
            }
        }
        // Fall back: first color literal in the CSS.
        return colorHexRe.find(css)?.value?.let(::parseHex)
    }

    private fun parseHex(hex: String): Long? = runCatching {
        val raw = hex.removePrefix("#")
        val expanded = when (raw.length) {
            6 -> "FF$raw"   // #RRGGBB -> opaque
            8 -> "${raw.substring(6, 8)}${raw.substring(0, 6)}"   // #RRGGBBAA -> AARRGGBB
            else -> return null
        }
        expanded.toLong(16)
    }.getOrNull()
}
