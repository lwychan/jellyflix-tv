package com.notflix.tv.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts a long-lived MediaSession so Now-Playing controls appear in the Android TV
 * launcher and on Assistant-enabled remotes, plus lets us resume playback after the
 * player Activity is torn down.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this, PlaybackOptimizer.renderers(this)).build()
        exo.trackSelectionParameters = PlaybackOptimizer.trackParameters(this)
        player = exo
        session = MediaSession.Builder(this, exo).build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player ?: return
        if (!p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        player = null
        super.onDestroy()
    }
}
