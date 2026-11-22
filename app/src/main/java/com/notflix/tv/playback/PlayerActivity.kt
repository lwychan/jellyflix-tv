package com.notflix.tv.playback

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.notflix.tv.data.JellyfinClient
import com.notflix.tv.data.MediaRepository
import com.notflix.tv.data.SettingsStore
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.notflix.tv.ui.theme.NotflixTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay10
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

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
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var client: JellyfinClient
    @Inject lateinit var repo: MediaRepository

    private lateinit var exo: ExoPlayer

    /** Settings read once when the player opens. */
    private lateinit var prefs: SettingsStore.Prefs

    /** The title that has already had its one stall fallback to server HLS. */
    private var fallbackFor: UUID? = null
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

    /**
     * The title's audio tracks on the server, set only while it plays as server HLS. That stream
     * holds one track, so the player alone has nothing to switch between; the Audio picker lists
     * these instead and asks the server for a new stream. [serverAudioIndex] is the one playing.
     */
    private var serverAudio by mutableStateOf<List<ServerAudio>>(emptyList())
    private var serverAudioIndex by mutableStateOf<Int?>(null)

    /** Read by [onKeyDown]; false while a panel owns left/right. */
    private var seekKeysAllowed = true

    /** The seek burst on screen: the running total in seconds, negative for back. Null when hidden. */
    private val seekHint = mutableStateOf<SeekHint?>(null)
    private val seekHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clearSeekHint = Runnable { seekHint.value = null }

    /** Shows the jump just made. Quick presses add up, and the badge clears after a short pause. */
    private fun flashSeek(forward: Boolean) {
        // Matches the player's increments: 30 s forward, 10 s back (see the builder below).
        val step = if (forward) 30 else -10
        seekHint.value = SeekHint((seekHint.value?.seconds ?: 0) + step)
        seekHandler.removeCallbacks(clearSeekHint)
        seekHandler.postDelayed(clearSeekHint, 900)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)?.let(UUID::fromString)
            ?: run { finish(); return }
        fallbackTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)

        prefs = runBlocking { settings.prefs.first() }
        exo = buildPlayer(this, prefs)
        startStallWatchdog()
        openedAt = android.os.SystemClock.elapsedRealtime()
        exo.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (prefs.matchRefreshRate) {
                    PlaybackOptimizer.matchRefreshRate(window, this@PlayerActivity, exo.videoFormat)
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameShown = true
                Log.d(TAG, "first frame after ${android.os.SystemClock.elapsedRealtime() - openedAt} ms")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended = true
                updateKeepScreenOn()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateKeepScreenOn()
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = "${error.errorCodeName}: ${error.message}"
                Log.e(TAG, "Player error $message", error)
                playbackError = message
            }
        })

        target = Target(itemId, startMs, switches)
        setContent { NotflixTheme { PlayerSurface() } }
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
                val stream = resolver.resolve(current.id, allowDirect = prefs.preferDirectPlay)
                val s = ReportSession(
                    itemId = current.id,
                    mediaSourceId = stream.mediaSource.id,
                    playMethod = if (stream.isDirectPlay) PlayMethod.DIRECT_PLAY else PlayMethod.TRANSCODE,
                )
                session = s
                serverAudio = if (stream.isDirectPlay) emptyList() else stream.audioTracks
                serverAudioIndex = stream.audioIndex
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
                        // Spinner only when playback stalls mid-play, as Netflix does. Start-up stays clean.
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        // The Compose overlay replaces the built-in controller.
                        useController = false
                    }
                },
                update = { it.player = exo },
            )

            // Touch: a tap on the picture brings the controls up when they are hidden and hides
            // them when they are up. This layer sits under the overlay, so a tap on one of the
            // overlay's buttons goes to that button and never reaches it.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (controlsVisible) controlsVisible = false else inputTick++
                        })
                    },
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
                        // No subtitle tracks: nothing to choose, so the picker stays closed.
                        val subs = subtitleChoicesOrNone(exo)
                        if (subs.isNotEmpty()) {
                            panelChoices = listOf(trackChoices(exo, C.TRACK_TYPE_TEXT, offLabel = "Off").first()) + subs
                            openPanel = TrackKind.SUBTITLES
                        }
                    },
                    onOpenAudio = {
                        if (serverAudio.size > 1) {
                            panelChoices = serverAudio.map {
                                TrackChoice(it.label, group = null, trackIndex = it.index, selected = it.index == serverAudioIndex)
                            }
                            openPanel = TrackKind.AUDIO
                        } else {
                            val audio = audioChoicesOrNone(exo)
                            if (audio.size > 1) {
                                panelChoices = audio
                                openPanel = TrackKind.AUDIO
                            }
                        }
                    },
                    onSkip = { span -> skip(span) },
                    onPickEpisode = { ep ->
                        episodesOpen = false
                        switchTo(ep.id, ep.startPositionMs(), ep.name.orEmpty())
                    },
                    onSeekStep = { forward ->
                        inputTick++
                        if (forward) exo.seekForward() else exo.seekBack()
                        flashSeek(forward)
                    },
                    onSeekTo = { positionMs ->
                        inputTick++
                        exo.seekTo(positionMs.coerceAtLeast(0))
                    },
                ),
                focus = focus,
                modifier = Modifier.fillMaxSize(),
            )

            seekHint.value?.let { SeekBadge(it.seconds) }

            if (!firstFrameShown && playbackError == null) StartupLogo()

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
                            if (kind == TrackKind.AUDIO && serverAudio.size > 1) {
                                switchServerAudio(choice.trackIndex)
                            } else {
                                selectTrack(
                                    exo,
                                    if (kind == TrackKind.SUBTITLES) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO,
                                    choice,
                                )
                            }
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
        serverAudio = emptyList()
        serverAudioIndex = null
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

    /**
     * Watches for a stall: buffering with no change in position for [STALL_MS]. Then the title
     * switches once to the server's HLS stream at the same point. Only one fallback per title.
     */
    private fun startStallWatchdog() {
        lifecycleScope.launch {
            var lastPos = -1L
            var stillSince = android.os.SystemClock.elapsedRealtime()
            while (true) {
                delay(1_000)
                val id = target?.id ?: continue
                val stalled = exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING
                val pos = exo.currentPosition
                val nowMs = android.os.SystemClock.elapsedRealtime()
                if (!stalled || pos != lastPos) {
                    lastPos = pos
                    stillSince = nowMs
                    continue
                }
                if (nowMs - stillSince >= STALL_MS && fallbackFor != id) {
                    fallbackFor = id
                    fallBackToHls(id, pos)
                }
            }
        }
    }

    /**
     * Keeps the TV from starting its screensaver, and pausing the film, while the title plays or
     * buffers. Nothing keeps the screen on by itself here, so a Fire TV started its own
     * screensaver a few minutes into a film. A paused title lets the screen sleep as usual.
     */
    private fun updateKeepScreenOn() {
        val active = exo.playWhenReady &&
            exo.playbackState != Player.STATE_IDLE &&
            exo.playbackState != Player.STATE_ENDED
        if (active) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Plays the same title on from here with server audio track [index], as a new HLS stream. */
    private fun switchServerAudio(index: Int) {
        val id = target?.id ?: return
        val atMs = exo.currentPosition
        lifecycleScope.launch {
            try {
                val stream = resolver.resolve(id, allowDirect = false, audioIndex = index)
                serverAudioIndex = stream.audioIndex
                exo.setMediaItem(MediaItem.fromUri(stream.url), atMs)
                exo.prepare()
                exo.playWhenReady = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Audio switch failed", e)
                playbackError = "Couldn't change audio: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun fallBackToHls(id: UUID, atMs: Long) {
        Log.w(TAG, "No progress for ${STALL_MS / 1000} s at ${atMs} ms; switching to server HLS")
        lifecycleScope.launch {
            try {
                val stream = resolver.resolve(id, allowDirect = false)
                session = session?.copy(playMethod = PlayMethod.TRANSCODE)
                exo.setMediaItem(MediaItem.fromUri(stream.url), atMs)
                exo.prepare()
                exo.playWhenReady = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "HLS fallback failed", e)
                playbackError = "Couldn't start playback: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun buildPlayer(ctx: Context, prefs: SettingsStore.Prefs): ExoPlayer {
        val renderers = PlaybackOptimizer.renderers(ctx)
        val http = DefaultHttpDataSource.Factory().also { httpFactory = it }
        val sourceFactory = DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http))
        // Tunneling is a track-selector setting, so the selector is built here with the saved choice.
        val selector = DefaultTrackSelector(ctx).apply {
            parameters = buildUponParameters().setTunnelingEnabled(prefs.tunneledPlayback).build()
        }
        return ExoPlayer.Builder(ctx, renderers)
            .setTrackSelector(selector)
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
            // Centre key, plus the buttons a gamepad or a keyboard sends in its place.
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SPACE -> {
                togglePlay()
                return true
            }
            // Gamepad B is Back: closes a panel first, then the player.
            KeyEvent.KEYCODE_BUTTON_B -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            // Remotes with separate Play and Pause buttons.
            KeyEvent.KEYCODE_MEDIA_PLAY -> { exo.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { exo.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { exo.seekBack(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { exo.seekForward(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (seekKeysAllowed) { exo.seekBack(); flashSeek(false); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (seekKeysAllowed) { exo.seekForward(); flashSeek(true); return true }
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
        private const val TAG = "NotflixPlayer"
        /** How long buffering may sit with no movement before the player falls back to server HLS. */
        private const val STALL_MS = 15_000L
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

/** A seek burst for [SeekBadge]: the total jump in seconds. */
private data class SeekHint(val seconds: Int)

/** Brief "+30s" or "−10s" badge in the centre of the picture. */
@Composable
private fun SeekBadge(seconds: Int) {
    val label = (if (seconds >= 0) "+" else "\u2212") + "${kotlin.math.abs(seconds)}s"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xB3000000))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (seconds >= 0) Icons.Filled.Forward30 else Icons.Filled.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}
