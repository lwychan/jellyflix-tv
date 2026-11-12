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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.media3.common.C
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
import com.jellyflix.tv.data.MediaRepository
import com.jellyflix.tv.plugin.PluginHost
import com.jellyflix.tv.ui.theme.JellyflixTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import timber.log.Timber

/** Which track picker is open over the controls. */
private enum class TrackKind { SUBTITLES, AUDIO }

/** What to play. [seq] changes on every request, so picking the same title again restarts it. */
private data class Target(val id: UUID, val startMs: Long, val seq: Int)

/** What Jellyfin needs to hear about the title being played. */
private data class ReportSession(val itemId: UUID, val mediaSourceId: String?, val playMethod: PlayMethod)

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject lateinit var resolver: StreamUrlResolver
    @Inject lateinit var pluginHost: PluginHost
    @Inject lateinit var client: JellyfinClient
    @Inject lateinit var repo: MediaRepository

    private lateinit var exo: ExoPlayer
    private var httpFactory: DefaultHttpDataSource.Factory? = null

    // Playback reporting, so Jellyfin knows where the viewer got to (Continue watching, resume).
    private var session: ReportSession? = null
    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var fallbackTitle = ""
    private var switches = 0

    /** When the current title was requested, for the time-to-first-frame log. */
    private var openedAt = 0L

    // UI state the overlay and the player listener share.
    private var target by mutableStateOf<Target?>(null)
    private var now by mutableStateOf<NowPlaying?>(null)
    private var inputTick by mutableIntStateOf(0)
    private var playbackError by mutableStateOf<String?>(null)
    private var firstFrameShown by mutableStateOf(false)
    private var ended by mutableStateOf(false)
    private var controlsVisible by mutableStateOf(true)
    private var openPanel by mutableStateOf<TrackKind?>(null)
    private var panelChoices by mutableStateOf<List<TrackChoice>>(emptyList())
    private var episodesOpen by mutableStateOf(false)

    /** Read by [onKeyDown]; false while a panel owns left/right. */
    private var seekKeysAllowed = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)?.let(UUID::fromString)
            ?: run { finish(); return }
        fallbackTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)

        exo = buildPlayer(this)
        openedAt = android.os.SystemClock.elapsedRealtime()
        exo.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                PlaybackOptimizer.matchRefreshRate(window, this@PlayerActivity, exo.videoFormat)
            }

            override fun onRenderedFirstFrame() {
                firstFrameShown = true
                Log.d(TAG, "first frame after ${android.os.SystemClock.elapsedRealtime() - openedAt} ms")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended = true
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = "${error.errorCodeName}: ${error.message}"
                Log.e(TAG, "Player error $message", error)
                playbackError = message
            }
        })

        target = Target(itemId, startMs, switches)
        setContent { JellyflixTheme { PlayerSurface() } }
    }

    @Composable
    private fun PlayerSurface() {
        val current = target ?: return
        val ui = remember { PlaybackUi() }
        val focus = remember { ControlFocus() }
        val panelFocus = remember { FocusRequester() }

        // Details for the overlay: show name, episodes, intro/credits ranges, chapters.
        LaunchedEffect(current.id) {
            val loaded = loadNowPlaying(repo, current.id)
            if (loaded != null) now = loaded
        }

        // Resolve the stream and start playing whenever the target changes.
        LaunchedEffect(current) {
            try {
                client.playbackAuthHeader()?.let { header ->
                    httpFactory?.setDefaultRequestProperties(mapOf("Authorization" to header))
                }
                val stream = resolver.resolve(current.id)
                pluginHost.onPlaybackStart(current.id, stream)
                val s = ReportSession(
                    itemId = current.id,
                    mediaSourceId = stream.mediaSource.id,
                    playMethod = if (stream.isDirectPlay) PlayMethod.DIRECT_PLAY else PlayMethod.TRANSCODE,
                )
                session = s
                exo.setMediaItem(
                    MediaItem.Builder()
                        .setUri(stream.url)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(now?.item?.name ?: fallbackTitle).build())
                        .build(),
                    current.startMs,
                )
                exo.prepare()
                exo.playWhenReady = true
                reportStart(s, current.startMs)
                while (true) {
                    delay(10_000)
                    reportProgress(s, exo.currentPosition, paused = !exo.isPlaying)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "Playback start failed")
                Log.e(TAG, "Playback start failed", e)
                playbackError = "Couldn't start playback: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        // A finished episode rolls on into the next, as on Netflix. After the last one, or a
        // movie, the player closes and the title page is back.
        LaunchedEffect(ended) {
            if (!ended) return@LaunchedEffect
            ended = false
            val next = now?.next
            if (next != null) switchTo(next.id, 0L, next.name.orEmpty()) else finish()
        }

        PlaybackPoller(player = exo, ui = ui, segments = now?.segments.orEmpty())

        SideEffect { seekKeysAllowed = openPanel == null && !episodesOpen }

        // Controls stay up while paused or while a panel is open. While playing they hide
        // after CONTROLS_IDLE_MS with no input. Any key press restarts the timer.
        LaunchedEffect(inputTick, openPanel, episodesOpen, ui.playing) {
            controlsVisible = true
            if (ui.playing && openPanel == null && !episodesOpen) {
                delay(CONTROLS_IDLE_MS)
                controlsVisible = false
            }
        }

        // When the bar appears, focus Play so DPAD-centre works on it straight away.
        // A skip prompt on screen keeps focus instead.
        LaunchedEffect(controlsVisible, episodesOpen, openPanel) {
            if (controlsVisible && !episodesOpen && openPanel == null && ui.activeSegment == null) {
                runCatching { focus.play.requestFocus() }
            }
        }

        // A skip prompt takes focus when it appears, so one press of OK skips.
        LaunchedEffect(ui.activeSegment) {
            if (ui.activeSegment != null && !episodesOpen && openPanel == null) {
                runCatching { focus.skip.requestFocus() }
            }
        }

        LaunchedEffect(openPanel) {
            if (openPanel != null) runCatching { panelFocus.requestFocus() }
        }

        BackHandler {
            when {
                openPanel != null -> openPanel = null
                episodesOpen -> episodesOpen = false
                else -> finish()
            }
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
                update = { it.player = exo },
            )

            PlayerControlsOverlay(
                visible = controlsVisible,
                now = now,
                fallbackTitle = fallbackTitle,
                ui = ui,
                episodesOpen = episodesOpen,
                seekEnabled = openPanel == null && !episodesOpen,
                actions = PlayerActions(
                    onBack = { finish() },
                    onPlayPause = { togglePlay() },
                    onReplay = { exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0)) },
                    onToggleEpisodes = { episodesOpen = !episodesOpen },
                    onOpenSubtitles = {
                        panelChoices = trackChoices(exo, C.TRACK_TYPE_TEXT, offLabel = "Off")
                        openPanel = TrackKind.SUBTITLES
                    },
                    onOpenAudio = {
                        panelChoices = trackChoices(exo, C.TRACK_TYPE_AUDIO, offLabel = null)
                        openPanel = TrackKind.AUDIO
                    },
                    onSkip = { span -> skip(span) },
                    onPickEpisode = { ep ->
                        episodesOpen = false
                        switchTo(ep.id, ep.startPositionMs(), ep.name.orEmpty())
                    },
                    onSeekStep = { forward ->
                        inputTick++
                        if (forward) exo.seekForward() else exo.seekBack()
                    },
                ),
                focus = focus,
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
                // Under the Subtitles and Audio buttons, top right.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    TrackPanel(
                        title = if (kind == TrackKind.SUBTITLES) "Subtitles" else "Audio",
                        choices = panelChoices,
                        firstItemFocus = panelFocus,
                        onPick = { choice ->
                            selectTrack(
                                exo,
                                if (kind == TrackKind.SUBTITLES) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO,
                                choice,
                            )
                            openPanel = null
                        },
                        onDismiss = { openPanel = null },
                        modifier = Modifier.padding(end = 40.dp, top = 84.dp),
                    )
                }
            }
        }
    }

    private fun togglePlay() {
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    /** Stops reporting the current title and starts [id] from [startMs]. */
    private fun switchTo(id: UUID, startMs: Long, title: String) {
        session?.let { reportStop(it, exo.currentPosition) }
        session = null
        playbackError = null
        firstFrameShown = false
        // Drop the old title's skip ranges and next-episode link until the new ones load.
        now = null
        fallbackTitle = title
        openedAt = android.os.SystemClock.elapsedRealtime()
        target = Target(id, startMs, ++switches)
    }

    /** Intro, recap and credits buttons. Credits go to the next episode when there is one. */
    private fun skip(span: Span) {
        val next = now?.next
        if (span.type == MediaSegmentType.OUTRO && next != null) {
            switchTo(next.id, 0L, next.name.orEmpty())
        } else {
            exo.seekTo(span.endMs)
        }
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
     * when the bar is up, so these run when it is hidden or when a key has no focus target.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        inputTick++
        if (!::exo.isInitialized) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                togglePlay()
                return true
            }
            // Remotes with separate Play and Pause buttons.
            KeyEvent.KEYCODE_MEDIA_PLAY -> { exo.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { exo.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { exo.seekBack(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { exo.seekForward(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (seekKeysAllowed) { exo.seekBack(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (seekKeysAllowed) { exo.seekForward(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        if (!::exo.isInitialized) return
        exo.pause()
        session?.let { reportProgress(it, exo.currentPosition, paused = true) }
    }

    override fun onDestroy() {
        if (::exo.isInitialized) {
            session?.let { reportStop(it, exo.currentPosition) }
            session = null
            exo.release()
        }
        super.onDestroy()
    }

    private fun reportStart(s: ReportSession, positionMs: Long) = reportScope.launch {
        runCatching {
            client.api().playStateApi.reportPlaybackStart(
                data = PlaybackStartInfo(
                    itemId = s.itemId,
                    mediaSourceId = s.mediaSourceId,
                    positionTicks = positionMs * 10_000L,
                    playMethod = s.playMethod,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }.onFailure { Timber.w(it, "Reporting playback start failed") }
    }

    private fun reportProgress(s: ReportSession, positionMs: Long, paused: Boolean) = reportScope.launch {
        runCatching {
            client.api().playStateApi.reportPlaybackProgress(
                data = PlaybackProgressInfo(
                    itemId = s.itemId,
                    mediaSourceId = s.mediaSourceId,
                    positionTicks = positionMs * 10_000L,
                    isPaused = paused,
                    playMethod = s.playMethod,
                    canSeek = true,
                    isMuted = false,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }.onFailure { Timber.w(it, "Reporting playback progress failed") }
    }

    private fun reportStop(s: ReportSession, positionMs: Long) = reportScope.launch {
        runCatching {
            client.api().playStateApi.reportPlaybackStopped(
                data = PlaybackStopInfo(
                    itemId = s.itemId,
                    mediaSourceId = s.mediaSourceId,
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
