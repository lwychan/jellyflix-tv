package com.notflix.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
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
import com.notflix.tv.data.ImageUrls
import com.notflix.tv.ui.components.CardArt
import com.notflix.tv.ui.components.MediaCard
import com.notflix.tv.ui.components.PageBlack
import java.util.Locale
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PersonKind

private val EdgePad = 56.dp
/** Hero heights. A movie's hero fills the screen; a series keeps room for its episodes under it. */
private val MovieHeroHeight = 480.dp
private val SeriesHeroHeight = 360.dp
private val CardGap = 16.dp

/**
 * Title page, scrolling as one list. The hero holds the art, title, facts, synopsis and Play; below
 * it come seasons and episodes for series, then "More like this", as on Netflix.
 * Play starts focused; DPAD-down moves into the rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(
    itemId: String,
    onPlay: (itemId: String, title: String, startMs: Long) -> Unit,
    onOpenDetails: (String) -> Unit,
    onBack: () -> Unit,
    vm: DetailsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(itemId) { vm.load(itemId) }
    // Back from the player: reload so the played state and progress are current.
    OnScreenResumed { vm.load(itemId) }

    val detail = state.item
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
    val isSeries = detail?.type == BaseItemKind.SERIES

    LaunchedEffect(detail?.id) {
        if (detail != null) runCatching { playFocus.requestFocus() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(PageBlack)) {
            if (detail == null) return@Box

            // Scroll only when focus nears the bottom edge. The default rule scrolled the hero
            // up so Play sat low on screen, hiding the title art.
            val bottomPx = with(LocalDensity.current) { 150.dp.toPx() }
            val pageSpec = remember(bottomPx) { EdgeBringIntoViewSpec(0f, bottomPx) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "hero") {
                    DetailsHero(
                        detail = detail,
                        isSeries = isSeries,
                        nextEpisode = if (isSeries) nextEpisode(state.episodes) else null,
                        playFocus = playFocus,
                        onPlay = onPlay,
                        onBack = onBack,
                    )
                }

                if (isSeries && state.episodes.isNotEmpty()) {
                    item(key = "seasons") {
                        if (seasons.isNotEmpty()) {
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
                    }
                    item(key = "episodes") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = EdgePad, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(episodesInSeason, key = { it.id.toString() }) { ep ->
                                MediaCard(
                                    item = ep,
                                    widthDp = 200,
                                    heightDp = 113,
                                    label = episodeLabel(ep),
                                    onClick = { onPlay(ep.id.toString(), ep.name.orEmpty(), ep.resumeMs()) },
                                )
                            }
                        }
                    }
                }

                if (state.similar.isNotEmpty()) {
                    item(key = "similar") {
                        MoreLikeThis(items = state.similar, onOpen = onOpenDetails)
                    }
                }
            }
            }
        }
    }
}

/** Art, title, facts, genres, cast, synopsis and the Play and Back buttons. */
@Composable
private fun DetailsHero(
    detail: BaseItemDto,
    isSeries: Boolean,
    nextEpisode: BaseItemDto?,
    playFocus: FocusRequester,
    onPlay: (itemId: String, title: String, startMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    // A series plays its next episode; a movie resumes if it was part-watched.
    val target = if (isSeries) nextEpisode ?: detail else detail
    val resuming = target.resumeMs() > 0L && target.userData?.played != true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isSeries) SeriesHeroHeight else MovieHeroHeight),
    ) {
        ImageUrls.backdrop(detail, maxWidth = 1920)?.let {
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
                    0.35f to Color(0xB3141414),
                    0.7f to Color(0x1A141414),
                    1f to Color.Transparent,
                )
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = EdgePad, end = EdgePad, bottom = 20.dp)
                // Text stays in the left third, leaving the art visible on the right as Netflix does.
                .widthIn(max = 480.dp),
        ) {
            // Title art when the server has it, as Netflix shows; otherwise the name.
            ImageUrls.logo(detail, maxWidth = 800)?.let { logo ->
                AsyncImage(
                    model = logo,
                    contentDescription = detail.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height(if (isSeries) 60.dp else 80.dp).width(260.dp),
                )
            } ?: Text(
                detail.name.orEmpty(),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                color = Color.White,
                maxLines = 2,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                detail.communityRating?.let {
                    Text(
                        String.format(Locale.US, "★ %.1f", it),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFFFFFF),
                    )
                }
                detail.productionYear?.let { Facts(it.toString()) }
                detail.runTimeTicks?.let { ticks ->
                    val minutes = (ticks / 600_000_000L).toInt()
                    if (minutes > 0) Facts(String.format(Locale.US, "%dh %dm", minutes / 60, minutes % 60))
                }
                detail.officialRating?.takeIf { it.isNotBlank() }?.let { Facts(it, boxed = true) }
            }

            detail.genres?.take(3)?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB3B3B3),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            detail.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = Color(0xFFE5E5E5),
                    maxLines = 3,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            detail.people?.filter { it.type == PersonKind.ACTOR }?.mapNotNull { it.name }?.take(3)
                ?.takeIf { it.isNotEmpty() }?.let { cast ->
                    Text(
                        "Starring " + cast.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB3B3B3),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

            Spacer(Modifier.height(18.dp))
            // Stacked, as Netflix does: Play above the secondary actions.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Play and Back share one size and shape, as the hero's buttons do.
                Button(
                    onClick = { onPlay(target.id.toString(), target.name.orEmpty(), target.resumeMs()) },
                    modifier = Modifier.heightIn(min = 36.dp).width(200.dp).focusRequester(playFocus),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    // No focus growth, so Play is the same width as Back when highlighted.
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (resuming) "Resume" else "Play",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        )
                    }
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 36.dp).width(200.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xB36D6D6E),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

/** "More like this": 16:9 cards for titles the server rates as similar. */
@Composable
private fun MoreLikeThis(items: List<BaseItemDto>, onOpen: (String) -> Unit) {
    Column {
        Text(
            "More like this",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(start = EdgePad, end = EdgePad, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = EdgePad, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            items(items, key = { it.id.toString() }) { sim ->
                MediaCard(
                    item = sim,
                    onClick = { onOpen(sim.id.toString()) },
                    widthDp = 240,
                    heightDp = 135,
                    art = CardArt.Backdrop,
                    label = sim.name.orEmpty(),
                    subtitle = sim.productionYear?.toString(),
                )
            }
        }
    }
}

/** The episode Play should start: one part-watched, else the first unwatched, else the first. */
private fun nextEpisode(episodes: List<BaseItemDto>): BaseItemDto? {
    val ordered = episodes.sortedWith(compareBy({ it.parentIndexNumber ?: Int.MAX_VALUE }, { it.indexNumber ?: Int.MAX_VALUE }))
    return ordered.firstOrNull { (it.userData?.playbackPositionTicks ?: 0L) > 0L && it.userData?.played != true }
        ?: ordered.firstOrNull { it.userData?.played != true }
        ?: ordered.firstOrNull()
}

private fun BaseItemDto.resumeMs(): Long = (userData?.playbackPositionTicks ?: 0L) / 10_000L

private fun episodeLabel(ep: BaseItemDto): String {
    val number = ep.indexNumber?.let { "$it. " }.orEmpty()
    return number + ep.name.orEmpty()
}

@Composable
private fun Facts(text: String, boxed: Boolean = false) {
    if (boxed) {
        Box(Modifier.border(1.dp, Color(0x99E5E5E5), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color(0xFFE5E5E5))
        }
    } else {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE5E5E5))
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.colors(
            // Selected is a grey pill; focus is the solid white one, so the two never look the same.
            containerColor = if (selected) Color(0x59FFFFFF) else Color(0x33FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}
