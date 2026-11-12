package com.jellyflix.tv.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.jellyflix.tv.data.ImageUrls
import org.jellyfin.sdk.model.api.BaseItemDto

private val CardShape = RoundedCornerShape(12.dp)
private const val FocusScale = 1.15f
private const val FocusShadowDp = 18f

/**
 * Netflix-style poster card.
 *
 * Focus behaviour:
 * - scales to [FocusScale] on a spring, so it grows past its neighbours,
 * - lifts above them (zIndex) with a soft shadow, so nothing is clipped,
 * - shows a subtle white outline.
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
) {
    // Request at ~3x logical width so the TV's pixel density doesn't over-sample.
    val posterUrl = remember(item.id, widthDp) { ImageUrls.primary(item, maxWidth = widthDp * 3) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    LaunchedEffect(focused, item.id) {
        onFocusChange?.invoke(item, focused)
    }

    val scale by animateFloatAsState(
        targetValue = if (focused) FocusScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card-scale",
    )
    val outlineAlpha by animateFloatAsState(
        targetValue = if (focused) 0.9f else 0f,
        animationSpec = tween(150),
        label = "card-outline",
    )
    val shadow by animateFloatAsState(
        targetValue = if (focused) FocusShadowDp else 0f,
        animationSpec = tween(200),
        label = "card-shadow",
    )

    Column(
        modifier = modifier
            .width(widthDp.dp)
            // Lift the focused card above its neighbours so the enlarged poster overlaps them.
            .zIndex(if (focused) 1f else 0f)
            .onPreviewKeyEvent { event ->
                when {
                    onDpadUp != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                        onDpadUp()
                        true
                    }
                    blockRight && event.key == Key.DirectionRight -> true
                    else -> false
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = shadow
                shape = CardShape
                clip = false
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
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
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
            // Netflix-style resume bar along the bottom edge of partly watched titles.
            item.userData?.playedPercentage
                ?.takeIf { it > 0.0 && it < 100.0 }
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
        androidx.tv.material3.Text(
            text = label ?: item.name.orEmpty(),
            // Small posters get the smaller label so more of the title fits.
            style = (if (widthDp < 160) androidx.tv.material3.MaterialTheme.typography.labelMedium else androidx.tv.material3.MaterialTheme.typography.labelLarge)
                .copy(fontWeight = FontWeight.Medium),
            color = if (focused) Color.White else Color(0xFFE5E5E5),
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp).width(widthDp.dp),
        )
        subtitle?.let {
            androidx.tv.material3.Text(
                text = it,
                style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                color = Color(0xFFB3B3B3),
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp).width(widthDp.dp),
            )
        }
    }
}
