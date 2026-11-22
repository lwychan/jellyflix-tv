package com.notflix.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioTracksTest {
    private fun track(index: Int, language: String, descriptive: Boolean = false) =
        ServerAudio(index, language, "$language $index", descriptive)

    @Test
    fun startsInEnglishWhenTheFileDefaultsToAnotherLanguage() {
        // Nocturne S1E5: Portuguese flagged default, English second.
        val tracks = listOf(track(1, "por"), track(2, "eng"))
        assertEquals(2, preferredAudioIndex(tracks, serverDefault = 1))
    }

    @Test
    fun skipsDescriptiveEnglishForTheRealEnglishTrack() {
        val tracks = listOf(
            track(1, "fra"), track(2, "fra", descriptive = true),
            track(3, "eng"), track(4, "eng", descriptive = true),
        )
        assertEquals(3, preferredAudioIndex(tracks, serverDefault = 1))
    }

    @Test
    fun fallsBackToTheServerDefaultWithoutEnglish() {
        val tracks = listOf(track(1, "por"), track(2, "fra"))
        assertEquals(1, preferredAudioIndex(tracks, serverDefault = 1))
        assertNull(preferredAudioIndex(emptyList(), serverDefault = null))
    }

    @Test
    fun onlyDescriptiveEnglishIsNotPreferred() {
        val tracks = listOf(track(1, "fra"), track(2, "eng", descriptive = true))
        assertEquals(1, preferredAudioIndex(tracks, serverDefault = 1))
    }

    @Test
    fun addsTheAudioIndexToAnHlsUrl() {
        assertEquals(
            "http://s/Videos/x/main.m3u8?mediaSourceId=m&api_key=t&AudioStreamIndex=2",
            withAudioStreamIndex("http://s/Videos/x/main.m3u8?mediaSourceId=m&api_key=t", 2),
        )
        assertEquals("http://s/v.m3u8?AudioStreamIndex=2", withAudioStreamIndex("http://s/v.m3u8", 2))
    }

    @Test
    fun replacesAnAudioIndexAlreadyInTheUrl() {
        assertEquals(
            "http://s/v.m3u8?a=1&c=3&AudioStreamIndex=2",
            withAudioStreamIndex("http://s/v.m3u8?a=1&AudioStreamIndex=1&c=3", 2),
        )
    }
}
