package com.jellyflix.tv.playback

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

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
            val label = format.label
                ?: format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
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
}

/** Keeps [ui] in step with [player] while the overlay exists. */
@Composable
fun PlaybackPoller(player: ExoPlayer?, ui: PlaybackUi) {
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            ui.positionMs = p.currentPosition
            ui.durationMs = p.duration.coerceAtLeast(0)
            ui.playing = p.isPlaying
            ui.subtitleLabel = trackChoices(p, C.TRACK_TYPE_TEXT, offLabel = "Off")
                .firstOrNull { it.selected }?.label ?: "Off"
            ui.audioLabel = trackChoices(p, C.TRACK_TYPE_AUDIO, offLabel = null)
                .firstOrNull { it.selected }?.label ?: "Default"
            delay(250)
        }
    }
}

/**
 * Bottom-third controls: a dark fade, a scrub bar with times, then one row of
 * play/pause on the left and subtitle and audio choosers on the right.
 *
 * [visible] is owned by the caller, which hides the bar after [CONTROLS_IDLE_MS] of inactivity.
 */
@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    title: String,
    ui: PlaybackUi,
    onPlayPause: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
    /** Left/right seek. The bar never uses them for focus; up/down move between the two rows. */
    onSeekStep: (forward: Boolean) -> Unit,
    playFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val seek = event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                    if (seek) onSeekStep(event.key == Key.DirectionRight)
                    seek
                },
        ) {
            // Bottom third only: the top two-thirds of the picture stay clear.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.34f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color(0xB3000000),
                            1f to Color(0xE6000000),
                        )
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 56.dp, bottom = 40.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                    color = Color.White,
                    maxLines = 1,
                )
                Box(Modifier.height(16.dp))
                ScrubBar(positionMs = ui.positionMs, durationMs = ui.durationMs)
                Box(Modifier.height(16.dp))
                // Upper row: track choosers, right-aligned. Up from Play lands here.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    ControlButton(label = "Subtitles: ${ui.subtitleLabel}", onClick = onOpenSubtitles)
                    Box(Modifier.width(12.dp))
                    ControlButton(label = "Audio: ${ui.audioLabel}", onClick = onOpenAudio)
                }
                Box(Modifier.height(12.dp))
                // Lower row: play/pause and time.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(
                        icon = if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (ui.playing) "Pause" else "Play",
                        onClick = onPlayPause,
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    Text(
                        "${formatTime(ui.positionMs)} / ${formatTime(ui.durationMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Muted,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrubBar(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
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
}

/** Focusable pill used for every control. Turns white when focused, as the menus do. */
@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            icon?.let {
                androidx.tv.material3.Icon(it, contentDescription = null)
                Box(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Picker for subtitle or audio tracks, shown above the control bar. */
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
