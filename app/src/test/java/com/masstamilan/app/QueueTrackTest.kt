package com.masstamilan.app

import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.pagePathOf
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
}
