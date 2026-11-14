package com.notflix.tv.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRouteTest {

    private val id = UUID.fromString("ea2898af-2f37-8e84-bc09-759b7838c158")
    private val base = "http://192.168.1.153:8096"
    private val token = "tok123"

    @Test
    fun directFileOnlyWhenServerSaysDirectPlayAndAllowed() {
        assertEquals(StreamRoute.DIRECT_FILE, StreamRoute.choose(allowDirect = true, supportsDirectPlay = true))
    }

    @Test
    fun directStreamOnlyGoesToServerHls() {
        // The server can remux or convert audio, but the raw file is not safe to serve.
        assertEquals(StreamRoute.SERVER_HLS, StreamRoute.choose(allowDirect = true, supportsDirectPlay = false))
    }

    @Test
    fun directPlayTurnedOffGoesToServerHlsEvenWhenSupported() {
        assertEquals(StreamRoute.SERVER_HLS, StreamRoute.choose(allowDirect = false, supportsDirectPlay = true))
    }

    @Test
    fun directFileUrlIsRawStream() {
        val url = StreamRoute.url(StreamRoute.DIRECT_FILE, base, id, "ms1", token, transcodingUrl = null)
        assertEquals("$base/Videos/$id/stream?static=true&mediaSourceId=ms1&api_key=$token", url)
    }

    @Test
    fun serverHlsUsesTheServersRelativeTranscodingPath() {
        val url = StreamRoute.url(
            StreamRoute.SERVER_HLS, base, id, "ms1", token,
            transcodingUrl = "/videos/$id/master.m3u8?MediaSourceId=ms1",
        )
        assertEquals("$base/videos/$id/master.m3u8?MediaSourceId=ms1", url)
    }

    @Test
    fun serverHlsKeepsAnAbsoluteTranscodingUrl() {
        val absolute = "http://example.invalid/videos/$id/master.m3u8"
        assertEquals(absolute, StreamRoute.url(StreamRoute.SERVER_HLS, base, id, "ms1", token, absolute))
    }

    @Test
    fun serverHlsFallsBackToMainM3u8WhenServerGaveNoPath() {
        val url = StreamRoute.url(StreamRoute.SERVER_HLS, base, id, "ms1", token, transcodingUrl = null)
        assertEquals("$base/Videos/$id/main.m3u8?mediaSourceId=ms1&api_key=$token", url)
    }
}
