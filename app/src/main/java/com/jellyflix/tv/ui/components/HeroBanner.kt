package com.jellyflix.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jellyflix.tv.data.ImageUrls
import org.jellyfin.sdk.model.api.BaseItemDto

/** Page background. The hero's bottom gradient dissolves into this colour. */
val PageBlack = Color(0xFF141414)

/** Full-width hero height, measured from the very top of the screen. */
val HeroBannerHeight = 480.dp

private val HeroPad = 56.dp
private const val HeroCrossFadeMs = 500
private const val TicksPerMinute = 600_000_000L

/**
 * Netflix-style hero for the currently focused title.
 *
 * - Full-width backdrop at high resolution, cross-fading when [item] changes.
 * - A linear gradient fades the bottom of the backdrop into [PageBlack].
 * - Title logo (falls back to text), metadata, and synopsis, then Play and More Info.
 * - [playFocusRequester] is attached to Play, so the rows above can route DPAD-up to it.
 * - [onFocusWithinChanged] reports whether focus is anywhere inside the hero.
 */
@Composable
fun HeroBanner(
    item: BaseItemDto?,
    onPlay: (BaseItemDto) -> Unit,
    onMoreInfo: (BaseItemDto) -> Unit,
    playFocusRequester: FocusRequester,
    onFocusWithinChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** False hides Play and More Info, e.g. for library tiles that have nothing to play. */
    showActions: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroBannerHeight)
            .onFocusChanged { onFocusWithinChanged(it.hasFocus) },
    ) {
        // === Backdrop ===
        AnimatedContent(
            targetState = item,
            contentKey = { it?.id },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(HeroCrossFadeMs)) togetherWith fadeOut(tween(HeroCrossFadeMs)) },
            label = "hero-backdrop",
        ) { target ->
            // Use the lambda's own target so the outgoing frame keeps its image while fading.
            val url = target?.let { ImageUrls.backdrop(it, maxWidth = 1920) }
            Box(Modifier.fillMaxSize().background(PageBlack)) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // === Gradients ===
        // Linear, top to bottom: a light scrim under the top rail, then a fade into PageBlack.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x99141414),
                    0.2f to Color(0x00141414),
                    0.45f to Color(0x00141414),
                    1f to PageBlack,
                )
            )
        )
        // Left-side scrim keeps the copy legible over bright art.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xCC141414),
                    0.5f to Color(0x33141414),
                    1f to Color.Transparent,
                )
            )
        )

        // === Copy and actions, bottom-left ===
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = HeroPad, end = HeroPad, bottom = 28.dp)
                .widthIn(max = 760.dp),
        ) {
            AnimatedContent(
                targetState = item,
                contentKey = { it?.id },
                transitionSpec = { fadeIn(tween(HeroCrossFadeMs)) togetherWith fadeOut(tween(HeroCrossFadeMs)) },
                label = "hero-copy",
            ) { target ->
                // HeroCopy emits several children; AnimatedContent would stack them, so wrap in a Column.
                if (target != null) Column { HeroCopy(target) }
            }

            Spacer(Modifier.height(20.dp))

            if (showActions) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroActionButton(
                    label = "Play",
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    enabled = item != null,
                    onClick = { item?.let(onPlay) },
                    modifier = Modifier.focusRequester(playFocusRequester),
                )
                HeroActionButton(
                    label = "More Info",
                    icon = Icons.Filled.Info,
                    primary = false,
                    enabled = item != null,
                    onClick = { item?.let(onMoreInfo) },
                )
            }
        }
    }
}

/** Title, metadata and synopsis for one item. Animated as a unit when the item changes. */
@Composable
private fun HeroCopy(item: BaseItemDto) {
    val title = item.seriesName ?: item.name.orEmpty()
    val logoUrl = ImageUrls.logo(item, maxWidth = 800)

    if (logoUrl != null) {
        AsyncImage(
            model = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.height(120.dp).width(480.dp),
        )
    } else {
        Text(
            title,
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 52.sp),
            color = Color.White,
            maxLines = 2,
        )
    }

    // Episode name, when it differs from the show title.
    item.name?.takeIf { it.isNotBlank() && it != item.seriesName }?.let {
        Text(
            it,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFFE5E5E5),
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    HeroMetadata(item, modifier = Modifier.padding(top = 12.dp))

    item.overview?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFE5E5E5),
            maxLines = 3,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Community rating, official rating badge, year and runtime. Renders nothing if all are missing. */
@Composable
private fun HeroMetadata(item: BaseItemDto, modifier: Modifier = Modifier) {
    val rating = item.communityRating?.let { "%.1f".format(it) }
    val year = item.productionYear?.toString()
    val runtime = item.runTimeTicks?.let { formatRuntime(it) }
    if (rating == null && year == null && runtime == null && item.officialRating == null) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        rating?.let {
            Text(
                "★ $it",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFFFFFFF),
            )
        }
        year?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE5E5E5)) }
        runtime?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE5E5E5)) }
        item.officialRating?.takeIf { it.isNotBlank() }?.let {
            Box(
                Modifier
                    .border(1.dp, Color(0x99E5E5E5), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(it, style = MaterialTheme.typography.labelLarge, color = Color(0xFFE5E5E5))
            }
        }
    }
}

private fun formatRuntime(ticks: Long): String? {
    val minutes = ticks / TicksPerMinute
    if (minutes <= 0) return null
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

/** Play is solid white; More Info is translucent grey. Both turn fully white when focused. */
@Composable
private fun HeroActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp).widthIn(min = 150.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Color.White else Color(0xB36D6D6E),
            contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            disabledContainerColor = Color(0x33646464),
            disabledContentColor = Color(0x66FFFFFF),
        ),
    ) {
        // Sized to its content: a fill-size box here stretched Play across the whole hero column.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}
