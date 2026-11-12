package com.jellyflix.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jellyflix.tv.data.ImageUrls
import com.jellyflix.tv.ui.components.MediaCard
import com.jellyflix.tv.ui.components.PageBlack
import java.util.Locale
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private val EdgePad = 56.dp
private val TopSectionHeight = 560.dp

/**
 * Title page. Top: backdrop, title, facts, synopsis and Play. Bottom (series only): season chips
 * and a row of episodes. Play starts focused; DPAD-down moves into the seasons and episodes.
 */
@Composable
fun DetailsScreen(
    itemId: String,
    onPlay: (itemId: String, title: String) -> Unit,
    onBack: () -> Unit,
    vm: DetailsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(itemId) { vm.load(itemId) }

    val item = state.item
    val playFocus = remember { FocusRequester() }
    val seasons = remember(state.episodes) {
        state.episodes.mapNotNull { it.parentIndexNumber }.distinct().sorted()
    }
    var season by remember(seasons) { mutableStateOf(seasons.firstOrNull()) }
    val episodesInSeason = remember(state.episodes, season) {
        state.episodes
            .filter { season == null || it.parentIndexNumber == season }
            .sortedBy { it.indexNumber ?: Int.MAX_VALUE }
    }
    val isSeries = item?.type == BaseItemKind.SERIES

    LaunchedEffect(item?.id) {
        if (item != null) runCatching { playFocus.requestFocus() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(PageBlack)) {
            if (item == null) return@Box

            Column(Modifier.fillMaxSize()) {
                // === Top: backdrop, copy and Play ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isSeries) Modifier.height(TopSectionHeight) else Modifier.weight(1f)),
                ) {
                    ImageUrls.backdrop(item, maxWidth = 1920)?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1f to PageBlack,
                            )
                        )
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0f to Color(0xE6141414),
                                0.6f to Color(0x33141414),
                                1f to Color.Transparent,
                            )
                        )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = EdgePad, end = EdgePad, bottom = 20.dp)
                            .widthIn(max = 760.dp),
                    ) {
                        Text(
                            item.name.orEmpty(),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 46.sp,
                            ),
                            color = Color.White,
                            maxLines = 2,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            item.communityRating?.let {
                                Text(
                                    String.format(Locale.US, "★ %.1f", it),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFFFFFFF),
                                )
                            }
                            item.productionYear?.let { Facts(it.toString()) }
                            item.runTimeTicks?.let { ticks ->
                                val minutes = (ticks / 600_000_000L).toInt()
                                if (minutes > 0) Facts(String.format(Locale.US, "%dh %dm", minutes / 60, minutes % 60))
                            }
                            item.officialRating?.takeIf { it.isNotBlank() }?.let { Facts(it, boxed = true) }
                        }
                        item.overview?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFE5E5E5),
                                maxLines = 3,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onPlay(item.id.toString(), item.name.orEmpty()) },
                            modifier = Modifier.height(44.dp).widthIn(min = 150.dp).focusRequester(playFocus),
                            colors = ButtonDefaults.colors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                focusedContainerColor = Color(0xFFDDDDDD),
                                focusedContentColor = Color.Black,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }

                // === Bottom: seasons and episodes ===
                if (isSeries && state.episodes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (seasons.size > 1) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = EdgePad),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(seasons, key = { it }) { number ->
                                    SeasonChip(
                                        label = "Season $number",
                                        selected = number == season,
                                        onClick = { season = number },
                                    )
                                }
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = EdgePad, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(episodesInSeason, key = { it.id.toString() }) { ep ->
                                MediaCard(
                                    item = ep,
                                    widthDp = 260,
                                    heightDp = 146,
                                    label = episodeLabel(ep),
                                    onClick = { onPlay(ep.id.toString(), ep.name.orEmpty()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun episodeLabel(ep: BaseItemDto): String {
    val number = ep.indexNumber?.let { "$it. " }.orEmpty()
    return number + ep.name.orEmpty()
}

@Composable
private fun Facts(text: String, boxed: Boolean = false) {
    val base = Modifier.let { if (boxed) it.padding(horizontal = 0.dp) else it }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFFE5E5E5),
        modifier = base,
    )
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color.White else Color(0x33FFFFFF),
            contentColor = if (selected) Color.Black else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}
