package com.jellyflix.tv.playback

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jellyflix.tv.data.ImageUrls
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSegmentType

/** How long the controls stay up with no input while playing. */
const val CONTROLS_IDLE_MS = 3_000L

private val Netflix = Color(0xFFE50914)
private val Muted = Color(0xB3FFFFFF)

/** One selectable audio or subtitle track. [group] is null for "Off". */
data class TrackChoice(
    val label: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val selected: Boolean,
)

/** Subtitle or audio choices for [trackType], with "Off" first for subtitles. */
@OptIn(UnstableApi::class)
fun trackChoices(player: Player, trackType: Int, offLabel: String?): List<TrackChoice> {
    val choices = mutableListOf<TrackChoice>()
    val groups = player.currentTracks.groups.filter { it.type == trackType && it.isSupported }
    val anySelected = groups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
    if (offLabel != null) {
        choices += TrackChoice(offLabel, group = null, trackIndex = -1, selected = !anySelected)
    }
    groups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            // Transcoded streams often carry no label or language, so fall back to the channel layout.
            val channels = when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> null
            }
            val label = format.label
                ?: format.language?.takeIf { it != "und" }?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
                ?: channels
                ?: "Track ${choices.size + 1}"
            choices += TrackChoice(label, group, i, selected = group.isTrackSelected(i))
        }
    }
    return choices
}

/** Applies [choice] for [trackType]. A null group disables that track type. */
@OptIn(UnstableApi::class)
fun selectTrack(player: ExoPlayer, trackType: Int, choice: TrackChoice) {
    val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(trackType)
    val group = choice.group
    if (group == null) {
        builder.setTrackTypeDisabled(trackType, true)
    } else {
        builder.setTrackTypeDisabled(trackType, false)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
    }
    player.trackSelectionParameters = builder.build()
}

/** m:ss, or h:mm:ss past an hour. */
fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Live playback values the overlay draws. Refreshed by [PlaybackPoller]. */
class PlaybackUi {
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var playing by mutableStateOf(false)
    var subtitleLabel by mutableStateOf("Off")
    var audioLabel by mutableStateOf("Default")

    /** The intro, recap or credits range playback is inside right now, if any. */
    var activeSegment by mutableStateOf<Span?>(null)
}

/** Focus targets the host needs to reach from outside the overlay. */
class ControlFocus {
    val play = FocusRequester()
    val skip = FocusRequester()
    val episode = FocusRequester()
}

/** Everything the overlay can ask the player to do. */
class PlayerActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onReplay: () -> Unit,
    val onToggleEpisodes: () -> Unit,
    val onOpenSubtitles: () -> Unit,
    val onOpenAudio: () -> Unit,
    val onSkip: (Span) -> Unit,
    val onPickEpisode: (BaseItemDto) -> Unit,
    /** Left/right seek. True is forward. */
    val onSeekStep: (forward: Boolean) -> Unit,
)

/** Keeps [ui] in step with [player] while the overlay exists. */
@Composable
fun PlaybackPoller(player: ExoPlayer?, ui: PlaybackUi, segments: List<Span>) {
    LaunchedEffect(player, segments) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            val position = p.currentPosition
            ui.positionMs = position
            ui.durationMs = p.duration.coerceAtLeast(0)
            ui.playing = p.isPlaying
            // No prompt in the last second of a range, or once the video has ended: credits that
            // run to the very end would otherwise leave "Skip Credits" on a finished episode.
            ui.activeSegment = if (p.playbackState == Player.STATE_ENDED) {
                null
            } else {
                segments.firstOrNull { position >= it.startMs && position < it.endMs - 1_000 }
            }
            ui.subtitleLabel = trackChoices(p, C.TRACK_TYPE_TEXT, offLabel = "Off")
                .firstOrNull { it.selected }?.label ?: "Off"
            ui.audioLabel = trackChoices(p, C.TRACK_TYPE_AUDIO, offLabel = null)
                .firstOrNull { it.selected }?.label ?: "Default"
            delay(250)
        }
    }
}

/**
 * Netflix-style player controls.
 *
 * - Top: back arrow and title on the left; Episodes, Subtitles and Audio on the right.
 * - Bottom: play/pause, replay 10s, the scrub bar with chapter ticks, time remaining.
 * - Skip Intro, Skip Recap or Next Episode, bottom right, whenever the server has marked one.
 * - Episodes opens a row of episodes, with synopses, in place of the bottom controls.
 *
 * [visible] is owned by the caller, which hides the bars after [CONTROLS_IDLE_MS] of inactivity.
 * The skip button is independent of [visible].
 * Left/right seek unless focus is in the top bar or a panel is open ([seekEnabled] false).
 */
@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    now: NowPlaying?,
    fallbackTitle: String,
    ui: PlaybackUi,
    episodesOpen: Boolean,
    seekEnabled: Boolean,
    actions: PlayerActions,
    focus: ControlFocus,
    modifier: Modifier = Modifier,
) {
    var topFocused by remember { mutableStateOf(false) }
    val chapterFractions = remember(now?.chapterStartsMs, ui.durationMs) {
        val duration = ui.durationMs
        if (duration <= 0) {
            emptyList()
        } else {
            now?.chapterStartsMs.orEmpty().map { it.toFloat() / duration }.filter { it > 0.005f && it < 1f }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val seek = seekEnabled && !topFocused && event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                if (seek) actions.onSeekStep(event.key == Key.DirectionRight)
                seek
            },
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Shades, so white text reads over bright pictures.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.verticalGradient(0f to Color(0xCC000000), 1f to Color.Transparent)),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(if (episodesOpen) 0.62f else 0.36f)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.4f to Color(0xB3000000),
                                1f to Color(0xE6000000),
                            ),
                        ),
                )

                TopBar(
                    heading = now?.heading ?: fallbackTitle,
                    subheading = now?.subheading,
                    hasEpisodes = now?.episodes?.isNotEmpty() == true,
                    actions = actions,
                    onFocusChange = { topFocused = it },
                    modifier = Modifier.align(Alignment.TopStart),
                )

                if (episodesOpen && now != null && now.episodes.isNotEmpty()) {
                    EpisodesPanel(
                        now = now,
                        onPick = actions.onPickEpisode,
                        focus = focus,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                } else {
                    BottomControls(
                        ui = ui,
                        actions = actions,
                        focus = focus,
                        chapterFractions = chapterFractions,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        }

        val segment = ui.activeSegment
        if (segment != null && !episodesOpen) {
            SkipButton(
                span = segment,
                hasNext = now?.next != null,
                onClick = { actions.onSkip(segment) },
                focus = focus,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 56.dp, bottom = 96.dp),
            )
        }
    }
}

@Composable
private fun TopBar(
    heading: String,
    subheading: String?,
    hasEpisodes: Boolean,
    actions: PlayerActions,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, top = 24.dp)
            .onFocusChanged { onFocusChange(it.hasFocus) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(onClick = actions.onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        Spacer(Modifier.width(16.dp))
        // Title and episode line share what the buttons leave, and each ellipsizes if it must.
        Row(Modifier.weight(1f).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                heading,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            subheading?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = Color(0xFFE5E5E5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // Icons only, as on Netflix. The pickers show the current choice when opened.
        if (hasEpisodes) {
            PillButton(onClick = actions.onToggleEpisodes, icon = Icons.AutoMirrored.Filled.List, contentDescription = "Episodes")
            Spacer(Modifier.width(10.dp))
        }
        PillButton(onClick = actions.onOpenSubtitles, icon = Icons.Filled.Subtitles, contentDescription = "Subtitles")
        Spacer(Modifier.width(10.dp))
        PillButton(onClick = actions.onOpenAudio, icon = Icons.Filled.VolumeUp, contentDescription = "Audio")
    }
}

@Composable
private fun BottomControls(
    ui: PlaybackUi,
    actions: PlayerActions,
    focus: ControlFocus,
    chapterFractions: List<Float>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(
            onClick = actions.onPlayPause,
            icon = if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (ui.playing) "Pause" else "Play",
            modifier = Modifier.focusRequester(focus.play),
        )
        Spacer(Modifier.width(10.dp))
        PillButton(onClick = actions.onReplay, icon = Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
        Spacer(Modifier.width(20.dp))
        ScrubBar(
            positionMs = ui.positionMs,
            durationMs = ui.durationMs,
            chapterFractions = chapterFractions,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(20.dp))
        Text(
            formatTime(ui.durationMs - ui.positionMs),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Red progress bar with the current time above the knob and a tick at each chapter. */
@Composable
private fun ScrubBar(
    positionMs: Long,
    durationMs: Long,
    chapterFractions: List<Float>,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(modifier.height(44.dp)) {
        val barWidth = maxWidth
        Text(
            formatTime(positionMs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = barWidth * fraction - 36.dp)
                .width(72.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x66FFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(Netflix),
            )
        }
        // Chapter ticks sit above the bar so the bar itself stays a clean line.
        chapterFractions.forEach { f ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = barWidth * f - 1.dp, y = (-4).dp)
                    .width(2.dp)
                    .height(6.dp)
                    .background(Color(0xB3FFFFFF)),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = barWidth * fraction - 7.dp, y = 5.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(Netflix),
        )
    }
}

/** Dark grey pill, bottom right, as on Netflix. Turns white when focused. */
@Composable
private fun SkipButton(
    span: Span,
    hasNext: Boolean,
    onClick: () -> Unit,
    focus: ControlFocus,
    modifier: Modifier = Modifier,
) {
    val label = when (span.type) {
        MediaSegmentType.INTRO -> "Skip Intro"
        MediaSegmentType.RECAP -> "Skip Recap"
        MediaSegmentType.OUTRO -> if (hasNext) "Next Episode" else "Skip Credits"
        else -> "Skip"
    }
    Button(
        onClick = onClick,
        modifier = modifier.focusRequester(focus.skip).height(44.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xCC333333),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

/** A row of the show's episodes, one season at a time, each with its synopsis below. */
@Composable
private fun EpisodesPanel(
    now: NowPlaying,
    onPick: (BaseItemDto) -> Unit,
    focus: ControlFocus,
    modifier: Modifier = Modifier,
) {
    val seasons = remember(now.episodes) {
        now.episodes.mapNotNull { it.parentIndexNumber }.distinct().sorted()
    }
    var season by remember { mutableIntStateOf(now.item.parentIndexNumber ?: seasons.firstOrNull() ?: 1) }
    val inSeason = remember(now.episodes, season) { now.episodes.filter { it.parentIndexNumber == season } }
    val focusIndex = remember(inSeason, now.item.id) {
        inSeason.indexOfFirst { it.id == now.item.id }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex)

    // Land on the episode that is playing.
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.episode.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.25f to Color(0xF2141414),
                    1f to Color(0xF2141414),
                ),
            )
            .padding(top = 28.dp, bottom = 20.dp),
    ) {
        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(seasons) { _, number ->
                    SeasonChip(label = "Season $number", selected = number == season, onClick = { season = number })
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(inSeason, key = { _, ep -> ep.id.toString() }) { index, ep ->
                EpisodeCard(
                    ep = ep,
                    isCurrent = ep.id == now.item.id,
                    onClick = { onPick(ep) },
                    modifier = if (index == focusIndex) Modifier.focusRequester(focus.episode) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color(0x55E50914) else Color(0x22FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun EpisodeCard(
    ep: BaseItemDto,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val thumb = remember(ep.id) { ImageUrls.primary(ep, maxWidth = 520) }
    val shape = RoundedCornerShape(8.dp)

    Column(modifier.width(260.dp)) {
        Box(
            Modifier
                .width(260.dp)
                .height(146.dp)
                .clip(shape)
                .background(Color(0xFF2F2F2F))
                .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = thumb,
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCurrent) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Playing", tint = Color.White)
                }
            }
            ep.userData?.playedPercentage?.takeIf { it > 0.0 && it < 100.0 }?.let { played ->
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
                            .background(Netflix),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ep.indexNumber?.let { "$it. " }.orEmpty()}${ep.name.orEmpty()}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (focused) Color.White else Color(0xFFE5E5E5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ep.runTimeTicks?.let { ticks ->
                Text(
                    "${ticks / 600_000_000L} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB3B3B3),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Text(
            ep.overview.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB3B3B3),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Focusable pill used for every control. Turns white when focused, as the menus do. */
@Composable
private fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    contentDescription: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = if (label == null) 10.dp else 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = contentDescription)
                if (label != null) Spacer(Modifier.width(8.dp))
            }
            label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            }
        }
    }
}

/** Picker for subtitle or audio tracks, shown under the top bar. */
@Composable
fun TrackPanel(
    title: String,
    choices: List<TrackChoice>,
    onPick: (TrackChoice) -> Unit,
    onDismiss: () -> Unit,
    firstItemFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE6141414))
            .padding(18.dp)
            .width(340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        if (choices.isEmpty()) {
            Text("No tracks available", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        choices.forEachIndexed { index, choice ->
            Button(
                onClick = { onPick(choice) },
                modifier = (if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.colors(
                    containerColor = if (choice.selected) Color(0x55E50914) else Color(0x22FFFFFF),
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    Box(Modifier.weight(1f))
                    if (choice.selected) Text("✓", color = Color.White)
                }
            }
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Muted,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        ) {
            Text("Close", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
