package com.notflix.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.notflix.tv.data.ImageUrls
import org.jellyfin.sdk.model.api.BaseItemDto

private val CardShape = RoundedCornerShape(12.dp)

/** Which artwork a card shows: portrait poster, or 16:9 backdrop (falls back to the poster). */
enum class CardArt { Poster, Backdrop }

/**
 * Netflix-style card.
 *
 * Focus behaviour: a white outline only. No zoom or shadow, which were the main cost of
 * sideways moves on the TV hardware (see the Home row timing in the 0.1.0-b11 notes).
 *
 * The caption sits below the art, so long titles wrap rather than
 * being cut off by the enlarged card. [captionOnFocusOnly] hides the caption until focus lands
 * on the card, as Netflix does on rows of artwork.
 *
 * [onDpadUp] lets a row take over DPAD-up, e.g. to scroll the page to the hero first.
 */
@Composable
fun MediaCard(
    item: BaseItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    widthDp: Int = 120,
    heightDp: Int = 180,
    /** Reports focus gain (true) and loss (false) for this card's item. */
    onFocusChange: ((BaseItemDto, Boolean) -> Unit)? = null,
    /** Called on DPAD-up. When set, the key is consumed. */
    onDpadUp: (() -> Unit)? = null,
    /** When true, DPAD-right is consumed here, so focus stops at the end of a carousel row. */
    blockRight: Boolean = false,
    /** Text under the card. Defaults to the item name. */
    label: String? = null,
    /** Smaller grey line under the label, e.g. "S1 E4 · Episode title". */
    subtitle: String? = null,
    /** Poster (default) or 16:9 backdrop artwork. */
    art: CardArt = CardArt.Poster,
    /** Hide the caption until the card is focused. The space is still reserved, so rows don't jump. */
    captionOnFocusOnly: Boolean = false,
    /** Rotten Tomatoes critic score (0–100) shown under the caption. Null hides the line. */
    rating: Int? = null,
) {
    // Request at ~3x logical width so the TV's pixel density doesn't over-sample.
    val artUrl = remember(item.id, widthDp, art) {
        when (art) {
            CardArt.Poster -> ImageUrls.primary(item, maxWidth = widthDp * 3)
            CardArt.Backdrop -> ImageUrls.backdrop(item, maxWidth = widthDp * 3)
                ?: ImageUrls.primary(item, maxWidth = widthDp * 3)
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    LaunchedEffect(focused, item.id) {
        onFocusChange?.invoke(item, focused)
    }

    val outlineAlpha by animateFloatAsState(
        targetValue = if (focused) 0.9f else 0f,
        animationSpec = tween(150),
        label = "card-outline",
    )
    val captionAlpha by animateFloatAsState(
        targetValue = if (captionOnFocusOnly && !focused) 0f else 1f,
        animationSpec = tween(150),
        label = "card-caption",
    )

    Column(
        modifier = modifier
            .width(widthDp.dp)
            .onPreviewKeyEvent { event ->
                when {
                    onDpadUp != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                        onDpadUp()
                        true
                    }
                    blockRight && event.key == Key.DirectionRight -> true
                    else -> false
                }
            },
    ) {
        Box(
            modifier = Modifier
                .width(widthDp.dp)
                .height(heightDp.dp)
                .clip(CardShape)
                .background(Color(0xFF2F2F2F))
                .border(width = 1.5.dp, color = Color.White.copy(alpha = outlineAlpha), shape = CardShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                androidx.tv.material3.Text(
                    item.name.orEmpty().take(1).uppercase(),
                    style = androidx.tv.material3.MaterialTheme.typography.displayLarge,
                    color = Color(0xFFB3B3B3),
                )
            }
            // Netflix-style bar along the bottom edge: partly watched shows how far. A finished item with
            // no percentage from the server shows full. A percentage always wins, because the server can
            // flag an episode played while it is still part-watched.
            item.userData
                ?.let { it.playedPercentage ?: if (it.played == true) 100.0 else null }
                ?.takeIf { it > 0.0 }
                ?.let { played ->
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66FFFFFF)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((played / 100.0).toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFFE50914)),
                        )
                    }
                }
        }

        // Room for the focused card's growth below its edge, plus a small gap.
        Spacer(Modifier.height(8.dp))

        Column(Modifier.width(widthDp.dp).alpha(captionAlpha)) {
            androidx.tv.material3.Text(
                text = label ?: item.name.orEmpty(),
                // Small posters get the smaller label so more of the title fits.
                style = (if (widthDp < 160) androidx.tv.material3.MaterialTheme.typography.labelMedium else androidx.tv.material3.MaterialTheme.typography.labelLarge)
                    .copy(fontWeight = FontWeight.Medium),
                color = if (focused) Color.White else Color(0xFFE5E5E5),
                // Two lines so a long title wraps instead of being cut off.
                maxLines = 2,
            )
            subtitle?.let {
                androidx.tv.material3.Text(
                    text = it,
                    style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB3B3B3),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            rating?.let { pct ->
                // Red dot for Rotten Tomatoes. A text emoji would show as a box on some TV fonts.
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(Color(0xFFFA320A), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    androidx.tv.material3.Text(
                        text = "$pct%",
                        style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB3B3B3),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

