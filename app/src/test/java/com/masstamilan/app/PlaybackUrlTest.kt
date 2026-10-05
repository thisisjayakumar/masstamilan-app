package com.masstamilan.app

import com.masstamilan.app.core.media.PlaybackManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies playback URL handling: only https MP3 / downloader URLs play.
 * Covers the "verify playback" test case — no Android framework needed.
 */
class PlaybackUrlTest {

    @Test fun `valid downloader urls are playable`() {
        assertTrue(
            PlaybackManager.isPlayableUrl(
                "https://www.masstamilan.dev/downloader/sig/ts/d320_cdn/101/xyz"
            )
        )
        assertTrue(
            PlaybackManager.isPlayableUrl("https://cdn.example.com/song.mp3")
        )
    }

    @Test fun `http and blank urls rejected`() {
        assertFalse(PlaybackManager.isPlayableUrl(""))
        assertFalse(PlaybackManager.isPlayableUrl("   "))
        assertFalse(
            PlaybackManager.isPlayableUrl(
                "http://www.masstamilan.dev/downloader/sig/ts/d128_cdn/1/x"
            )
        )
        assertFalse(PlaybackManager.isPlayableUrl("https://example.com/page with spaces.mp3"))
    }

    @Test fun `non-media https pages rejected`() {
        assertFalse(PlaybackManager.isPlayableUrl("https://www.masstamilan.dev/ram-songs"))
        assertFalse(PlaybackManager.isPlayableUrl("https://www.masstamilan.dev/"))
    }

    @Test fun `absoluteStreamUrl builds from downloader path`() {
        assertEquals(
            "https://www.masstamilan.dev/downloader/a/b",
            PlaybackManager.absoluteStreamUrl("/downloader/a/b")
        )
    }

    @Test fun `absoluteStreamUrl passes through http`() {
        val full = "https://cdn.example.com/x.mp3"
        assertEquals(full, PlaybackManager.absoluteStreamUrl(full))
    }

    @Test fun `absoluteStreamUrl rejects song pages and blanks`() {
        assertNull(PlaybackManager.absoluteStreamUrl("/555/song-mp3-song"))
        assertNull(PlaybackManager.absoluteStreamUrl(""))
    }

    @Test fun `local download uris are playable offline`() {
        assertTrue(PlaybackManager.isPlayableUrl("content://media/external/audio/media/42"))
        assertTrue(PlaybackManager.isPlayableUrl("file:///storage/emulated/0/Music/x.mp3"))
    }

    @Test fun `local uris with spaces rejected`() {
        assertFalse(PlaybackManager.isPlayableUrl("content://media/external/my song.mp3"))
    }

    @Test fun `320 preferred over 128 in parser`() {
        val html = """
            <a class="dlink" href="/downloader/s/128" rel="nofollow"
               title="Download X 128kbps"> 128kbps (1 MB)</a>
            <a class="dlink" href="/downloader/s/320" rel="nofollow"
               title="Download X 320kbps"> 320kbps (2 MB)</a>
        """.trimIndent()
        val url = com.masstamilan.app.data.remote.MasstamilanParsers
            .extractStreamUrl(html, prefer320 = true)
        assertNotNull(url)
        assertTrue(url!!.contains("/320"))
        val url128 = com.masstamilan.app.data.remote.MasstamilanParsers
            .extractStreamUrl(html, prefer320 = false)
        assertTrue(url128!!.contains("/128"))
    }
}
