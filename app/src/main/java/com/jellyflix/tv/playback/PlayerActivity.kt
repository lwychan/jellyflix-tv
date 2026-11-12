package com.jellyflix.tv.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.jellyflix.tv.data.JellyfinClient
import com.jellyflix.tv.plugin.PluginHost
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import com.jellyflix.tv.ui.theme.JellyflixTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** Which track picker is open over the controls. */
private enum class TrackKind { SUBTITLES, AUDIO }

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject lateinit var resolver: StreamUrlResolver
    @Inject lateinit var pluginHost: PluginHost
    @Inject lateinit var client: JellyfinClient

    private var httpFactory: DefaultHttpDataSource.Factory? = null

    private var player: ExoPlayer? = null
    private var startMs: Long = 0L

    // Playback reporting, so Jellyfin knows where the viewer got to (Continue watching, resume).
    private var itemIdForReport: UUID? = null
    private var mediaSourceForReport: String? = null
    private var playMethodForReport: PlayMethod = PlayMethod.DIRECT_PLAY
    private var progressJob: Job? = null
    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bumped on every key press so the overlay restarts its idle timer. */
    private var inputTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)?.let(UUID::fromString)
            ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        startMs = intent.getLongExtra(EXTRA_START_MS, 0L)
        itemIdForReport = itemId

        setContent { JellyflixTheme { PlayerSurface(itemId, title) } }
    }

    @Composable
    private fun PlayerSurface(itemId: UUID, title: String) {
        val ctx = this@PlayerActivity
        var format by remember { mutableStateOf<Format?>(null) }
        var playbackError by remember { mutableStateOf<String?>(null) }
        var firstFrameShown by remember { mutableStateOf(false) }
        val openedAt = remember { android.os.SystemClock.elapsedRealtime() }
        var exoState by remember { mutableStateOf<ExoPlayer?>(null) }
        val ui = remember { PlaybackUi() }

        var controlsVisible by remember { mutableStateOf(true) }
        var openPanel by remember { mutableStateOf<TrackKind?>(null) }
        var panelChoices by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
        val playFocus = remember { FocusRequester() }
        val panelFocus = remember { FocusRequester() }

        DisposableEffect(itemId) {
            val exo = buildPlayer(ctx)
            player = exo
            exoState = exo
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    PlaybackOptimizer.matchRefreshRate(window, ctx, exo.videoFormat)
                    format = exo.videoFormat
                }

                override fun onRenderedFirstFrame() {
                    firstFrameShown = true
                    Log.d(TAG, "first frame after ${android.os.SystemClock.elapsedRealtime() - openedAt} ms")
                }

                override fun onPlayerError(error: PlaybackException) {
                    val message = "${error.errorCodeName}: ${error.message}"
                    Log.e(TAG, "Player error $message", error)
                    playbackError = message
                }
            }
            exo.addListener(listener)

            lifecycleScope.launch {
                runCatching {
                    client.playbackAuthHeader()?.let { header ->
                        httpFactory?.setDefaultRequestProperties(mapOf("Authorization" to header))
                    }
                    val stream = resolver.resolve(itemId)
                    pluginHost.onPlaybackStart(itemId, stream)
                    exo.setMediaItem(
                        MediaItem.Builder()
                            .setUri(stream.url)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                            .build(),
                        startMs,
                    )
                    mediaSourceForReport = stream.mediaSource.id
                    playMethodForReport = if (stream.isDirectPlay) PlayMethod.DIRECT_PLAY else PlayMethod.TRANSCODE
                    exo.prepare()
                    exo.playWhenReady = true
                    reportStart(exo.currentPosition)
                    progressJob = lifecycleScope.launch {
                        while (true) {
                            delay(10_000)
                            reportProgress(exo.currentPosition, paused = !exo.isPlaying)
                        }
                    }
                }.onFailure {
                    Timber.e(it, "Playback start failed")
                    Log.e(TAG, "Playback start failed", it)
                    playbackError = "Couldn't start playback: ${it.message ?: it.javaClass.simpleName}"
                }
            }

            onDispose {
                progressJob?.cancel()
                reportStop(exo.currentPosition)
                exo.removeListener(listener)
                exo.release()
                player = null
                exoState = null
            }
        }

        PlaybackPoller(player = exoState, ui = ui)

        // Controls stay up while paused or while a picker is open. While playing they hide
        // after CONTROLS_IDLE_MS with no input. Any key press restarts the timer.
        LaunchedEffect(inputTick, openPanel, ui.playing) {
            controlsVisible = true
            if (ui.playing && openPanel == null) {
                delay(CONTROLS_IDLE_MS)
                controlsVisible = false
            }
        }

        // When the bar appears, focus Play so DPAD-centre works on it straight away.
        LaunchedEffect(controlsVisible) {
            if (controlsVisible && openPanel == null) runCatching { playFocus.requestFocus() }
        }

        LaunchedEffect(openPanel) {
            if (openPanel != null) runCatching { panelFocus.requestFocus() }
        }

        BackHandler {
            if (openPanel != null) openPanel = null else finish()
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        // Spinner from the moment playback starts, not only once it is playing.
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        // The Compose overlay replaces the built-in controller.
                        useController = false
                    }
                },
                update = { it.player = player },
            )

            PlayerControlsOverlay(
                visible = controlsVisible,
                title = title,
                ui = ui,
                onPlayPause = { togglePlay() },
                onOpenSubtitles = {
                    exoState?.let { panelChoices = trackChoices(it, C.TRACK_TYPE_TEXT, offLabel = "Off") }
                    openPanel = TrackKind.SUBTITLES
                },
                onOpenAudio = {
                    exoState?.let { panelChoices = trackChoices(it, C.TRACK_TYPE_AUDIO, offLabel = null) }
                    openPanel = TrackKind.AUDIO
                },
                onSeekStep = { forward ->
                    inputTick++
                    exoState?.let { if (forward) it.seekForward() else it.seekBack() }
                },
                playFocus = playFocus,
                modifier = Modifier.fillMaxSize(),
            )

            if (!firstFrameShown && playbackError == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.tv.material3.Text(
                        "Loading…",
                        style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                        color = Color(0xB3FFFFFF),
                    )
                }
            }

            playbackError?.let { message ->
                Box(Modifier.fillMaxSize().padding(horizontal = 56.dp), contentAlignment = Alignment.Center) {
                    androidx.tv.material3.Text(
                        message,
                        style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }

            openPanel?.let { kind ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    TrackPanel(
                        title = if (kind == TrackKind.SUBTITLES) "Subtitles" else "Audio",
                        choices = panelChoices,
                        firstItemFocus = panelFocus,
                        onPick = { choice ->
                            exoState?.let { exo ->
                                selectTrack(
                                    exo,
                                    if (kind == TrackKind.SUBTITLES) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO,
                                    choice,
                                )
                            }
                            openPanel = null
                        },
                        onDismiss = { openPanel = null },
                        modifier = Modifier.padding(end = 56.dp, bottom = 140.dp),
                    )
                }
            }
        }
    }

    private fun togglePlay() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun buildPlayer(ctx: Context): ExoPlayer {
        val renderers = PlaybackOptimizer.renderers(ctx)
        val http = DefaultHttpDataSource.Factory().also { httpFactory = it }
        val sourceFactory = DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http))
        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(sourceFactory)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
                trackSelectionParameters = PlaybackOptimizer.trackParameters(ctx)
                playWhenReady = true
            }
    }

    /**
     * Remote keys that reach the activity: the overlay's focusable controls consume DPAD
     * when the bar is up, so these run when it is hidden or when a key has no focus target
     * (for example, left/right at the edge of the bar seeks).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        inputTick++
        val p = player ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                togglePlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT -> { p.seekBack(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT -> { p.seekForward(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.let { p ->
            p.pause()
            reportProgress(p.currentPosition, paused = true)
        }
    }

    private fun reportStart(positionMs: Long) = reportScope.launch {
        val id = itemIdForReport ?: return@launch
        runCatching {
            client.api().playStateApi.reportPlaybackStart(
                data = PlaybackStartInfo(
                    itemId = id,
                    mediaSourceId = mediaSourceForReport,
                    positionTicks = positionMs * 10_000L,
                    playMethod = playMethodForReport,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }.onFailure { Timber.w(it, "Reporting playback start failed") }
    }

    private fun reportProgress(positionMs: Long, paused: Boolean) = reportScope.launch {
        val id = itemIdForReport ?: return@launch
        runCatching {
            client.api().playStateApi.reportPlaybackProgress(
                data = PlaybackProgressInfo(
                    itemId = id,
                    mediaSourceId = mediaSourceForReport,
                    positionTicks = positionMs * 10_000L,
                    isPaused = paused,
                    playMethod = playMethodForReport,
                    canSeek = true,
                    isMuted = false,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }.onFailure { Timber.w(it, "Reporting playback progress failed") }
    }

    private fun reportStop(positionMs: Long) = reportScope.launch {
        val id = itemIdForReport ?: return@launch
        runCatching {
            client.api().playStateApi.reportPlaybackStopped(
                data = PlaybackStopInfo(
                    itemId = id,
                    mediaSourceId = mediaSourceForReport,
                    positionTicks = positionMs * 10_000L,
                    failed = false,
                ),
            )
        }.onFailure { Timber.w(it, "Reporting playback stop failed") }
    }

    companion object {
        private const val TAG = "JellyflixPlayer"
        private const val EXTRA_ITEM_ID = "itemId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_START_MS = "startMs"
        fun intent(ctx: Context, itemId: UUID, title: String, startMs: Long = 0L) =
            Intent(ctx, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId.toString())
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_START_MS, startMs)
            }
    }
}
