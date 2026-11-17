package com.notflix.tv.playback

/**
 * The wall-clock moment (epoch ms) the title ends if it plays on from [positionMs] at normal
 * speed, or null while the length is not known yet. Past the end counts as "now".
 */
fun finishTimeMs(nowMs: Long, positionMs: Long, durationMs: Long): Long? {
    if (durationMs <= 0) return null
    return nowMs + (durationMs - positionMs).coerceAtLeast(0)
}
