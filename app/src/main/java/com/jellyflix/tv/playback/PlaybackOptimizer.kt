package com.jellyflix.tv.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import timber.log.Timber

/**
 * Centralizes the knobs that make playback *feel* native on a TV:
 *  - tunneled MediaCodec decoding (lower latency, lower CPU) via TrackSelectionParameters
 *  - display refresh rate matching for judder-free 24 / 25 / 50 / 60 fps video
 *  - Atmos / DTS / TrueHD passthrough when the TV or receiver supports it
 *  - preferred codec / HDR / audio channel counts for the selected device
 */
@OptIn(UnstableApi::class)
object PlaybackOptimizer {

    /** Build an ExoPlayer RenderersFactory configured for the best TV defaults. */
    fun renderers(context: Context): DefaultRenderersFactory =
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableAudioFloatOutput(true)

    /**
     * Base track-selection parameters. Max audio channels comes from what the
     * system reports so Atmos / DTS:X are offered when the path supports them.
     *
     * Tunneled playback is configured via DefaultTrackSelector.Parameters (not
     * the base TrackSelectionParameters); enable it there when the player is
     * wired with a DefaultTrackSelector.
     */
    fun trackParameters(context: Context): TrackSelectionParameters {
        val caps = AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null)
        return TrackSelectionParameters.Builder(context)
            .setMaxAudioChannelCount(if (caps.maxChannelCount > 0) caps.maxChannelCount else 8)
            // Subtitles start off, as on Netflix: don't auto-select "forced" subtitle tracks.
            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /**
     * Match the display refresh rate to the source frame rate. On AndroidTV this removes
     * the 3:2 pulldown judder from 24 fps movies on 60 Hz panels.
     */
    fun matchRefreshRate(window: Window, context: Context, sourceFormat: Format?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val fps = sourceFormat?.frameRate?.takeIf { it > 0f } ?: return
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display: Display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val best = display.supportedModes.minByOrNull { mode ->
            val d = kotlin.math.abs(mode.refreshRate - fps)
            val m = kotlin.math.abs(mode.refreshRate - fps * 2)
            val n = kotlin.math.abs(mode.refreshRate - fps * 2.5f)
            minOf(d, m, n)
        } ?: return

        val params: WindowManager.LayoutParams = window.attributes
        params.preferredDisplayModeId = best.modeId
        params.preferredRefreshRate = best.refreshRate
        window.attributes = params
        Timber.d("Matched refresh: src=%.2f → display=%.2fHz (modeId=%d)", fps, best.refreshRate, best.modeId)
    }
}
