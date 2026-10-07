package com.masstamilan.app

import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.pagePathOf
import com.masstamilan.app.data.model.songPagePathOf
import com.masstamilan.app.data.model.stepIndex
import com.masstamilan.app.data.model.toQueue
import org.junit.Assert.*
import org.junit.Test

class QueueTrackTest {

    @Test fun `pagePathOf accepts song pages only`() {
        assertEquals("4738/ala-bolelo-mp3-song", pagePathOf("/4738/ala-bolelo-mp3-song"))
        assertEquals("", pagePathOf("/downloader/sig/ts/d128_cdn/45295/abc"))
        assertEquals("", pagePathOf("https://www.masstamilan.dev/4738/x-mp3-song"))
        assertEquals("", pagePathOf(""))
    }

    @Test fun `stepIndex wraps both directions`() {
        assertEquals(1, stepIndex(0, 3, +1))
        assertEquals(0, stepIndex(2, 3, +1)) // last -> first
        assertEquals(2, stepIndex(0, 3, -1)) // first -> last
        assertEquals(0, stepIndex(0, 1, +1)) // single track stays
        assertEquals(-1, stepIndex(0, 0, +1)) // empty queue
        assertEquals(0, stepIndex(-1, 3, +1)) // unknown index starts at first
    }

    @Test fun `toQueue maps direct links and defers song pages`() {
        val songs = listOf(
            SongResult(name = "A", artists = "X", id = 1, dlPath = "/downloader/s/ts/d128/1/a", imageName = "art-1"),
            SongResult(name = "B", artists = "Y", id = 2, dlPath = "/9/b-mp3-song", imageName = "art-2")
        )
        val queue = songs.toQueue("movie-songs")
        assertEquals(2, queue.size)
        assertEquals("https://www.masstamilan.dev/downloader/s/ts/d128/1/a", queue[0].streamUrl)
        assertEquals("", queue[0].songPagePath)
        assertEquals("https://www.masstamilan.dev/i/art-1.jpg", queue[0].artwork)
        assertNull(queue[1].streamUrl)
        assertEquals("9/b-mp3-song", queue[1].songPagePath)
        assertEquals("movie-songs", queue[1].movieSlug)
        assertEquals(2, queue[1].songId)
    }

    @Test fun `songPagePathOf prefers parser pagePath over dlPath`() {
        // Album-JSON case: preview-stream dlPath + anchor pagePath → pagePath wins.
        val fromAlbum = SongResult(
            name = "Ala Bolelo", id = 45295,
            dlPath = "/downloader/sig/ts/p128_cdn/45295/abc",
            pagePath = "4738/ala-bolelo-mp3-song"
        )
        assertEquals("4738/ala-bolelo-mp3-song", songPagePathOf(fromAlbum))
        // Legacy case without pagePath falls back to dlPath derivation.
        assertEquals("9/b-mp3-song", songPagePathOf(SongResult(dlPath = "/9/b-mp3-song")))
        assertEquals("", songPagePathOf(SongResult(dlPath = "/downloader/s/ts/d128/1/a")))
    }

    @Test fun `toQueue keeps anchor pagePath alongside direct stream`() {
        val queue = listOf(
            SongResult(
                name = "Ala Bolelo", artists = "Anirudh", id = 45295,
                dlPath = "/downloader/sig/ts/p128_cdn/45295/abc",
                pagePath = "4738/ala-bolelo-mp3-song"
            )
        ).toQueue("jailer-2-2026-songs")
        assertEquals("https://www.masstamilan.dev/downloader/sig/ts/p128_cdn/45295/abc", queue[0].streamUrl)
        assertEquals("4738/ala-bolelo-mp3-song", queue[0].songPagePath)
    }
}
