package com.masstamilan.app

import com.masstamilan.app.data.entity.FavoriteEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import com.masstamilan.app.data.model.QueueTrack
import com.masstamilan.app.data.repository.dedupeKey
import com.masstamilan.app.data.repository.directStreamUrl
import com.masstamilan.app.data.repository.queueTrackFor
import com.masstamilan.app.data.repository.shuffledQueueWithStart
import com.masstamilan.app.data.repository.toQueueTrack
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies library rows map to playable queue entries with the song-page
 * path preserved for lazy stream resolution.
 */
class LibraryMapperTest {

    @Test fun `playlist row maps to queue track`() {
        val row = PlaylistSongEntity(
            playlistId = 1, position = 0, songId = 42,
            name = "Munbe Vaa", artists = "Naresh Iyer",
            movieSlug = "vaaranam-aayiram-songs",
            songPagePath = "101/munbe-vaa-mp3-song",
            imageName = "https://www.masstamilan.dev/i/vaa.jpg"
        )
        val t = row.toQueueTrack()
        assertEquals("Munbe Vaa", t.title)
        assertEquals("Naresh Iyer", t.artist)
        assertEquals("101/munbe-vaa-mp3-song", t.songPagePath)
        assertEquals("vaaranam-aayiram-songs", t.movieSlug)
        assertEquals(42, t.songId)
        assertNull(t.streamUrl) // page-path rows resolve lazily via StreamResolver
        assertEquals("https://www.masstamilan.dev/i/vaa.jpg", t.artwork)
    }

    @Test fun `playlist row preserves direct stream url`() {
        val row = PlaylistSongEntity(
            playlistId = 1, position = 0, songId = 43,
            name = "Direct", artists = "A",
            movieSlug = "m-songs", songPagePath = "",
            imageName = "art",
            streamUrl = "https://www.masstamilan.dev/downloader/abc.mp3"
        )
        val t = row.toQueueTrack()
        assertEquals("https://www.masstamilan.dev/downloader/abc.mp3", t.streamUrl)
        assertEquals("", t.songPagePath)
    }

    @Test fun `favorite row maps to queue track`() {
        val fav = FavoriteEntity(
            songKey = "k", songId = 7, name = "Ram", artists = "Anirudh",
            movieSlug = "ram-songs", songPagePath = "5/ram-mp3-song", imageName = "art"
        )
        val t = fav.toQueueTrack()
        assertEquals("Ram", t.title)
        assertEquals("5/ram-mp3-song", t.songPagePath)
        assertEquals(7, t.songId)
        assertNull(t.streamUrl)
    }

    @Test fun `favorite row preserves direct stream url`() {
        val fav = FavoriteEntity(
            songKey = "k2", songId = 8, name = "Direct", artists = "A",
            movieSlug = "m", songPagePath = "", imageName = "art",
            streamUrl = "https://cdn.example.com/x.mp3"
        )
        assertEquals("https://cdn.example.com/x.mp3", fav.toQueueTrack().streamUrl)
    }

    @Test fun `queueTrackFor builds from song parts`() {
        val t = queueTrackFor(9, "A", "B", "art", "9/a-mp3-song", "m-songs")
        assertEquals(9, t.songId)
        assertEquals("A", t.title)
        assertEquals("9/a-mp3-song", t.songPagePath)
        assertEquals("m-songs", t.movieSlug)
    }

    @Test fun `queueTrackFor carries direct stream url`() {
        val t = queueTrackFor(
            10, "A", "B", "art", "", "m-songs",
            streamUrl = "https://www.masstamilan.dev/downloader/d.mp3"
        )
        assertEquals("https://www.masstamilan.dev/downloader/d.mp3", t.streamUrl)
    }

    @Test fun `directStreamUrl maps downloader and http links`() {
        assertEquals(
            "https://www.masstamilan.dev/downloader/abc.mp3",
            directStreamUrl("/downloader/abc.mp3")
        )
        assertEquals("https://cdn.example.com/x.mp3", directStreamUrl("https://cdn.example.com/x.mp3"))
        assertNull(directStreamUrl("/5/song-mp3-song"))
        assertNull(directStreamUrl(""))
    }

    @Test fun `shuffledQueueWithStart keeps all tracks and points at selection`() {
        val tracks = listOf(
            QueueTrack(title = "A", songId = 1),
            QueueTrack(title = "B", songId = 2),
            QueueTrack(title = "C", songId = 3),
            QueueTrack(title = "D", songId = 4)
        )
        val (shuffled, newIndex) = shuffledQueueWithStart(tracks, 2, java.util.Random(42))
        assertEquals(4, shuffled.size)
        assertEquals(tracks.map { it.songId }.toSet(), shuffled.map { it.songId }.toSet())
        assertEquals(3, shuffled[newIndex].songId)
    }

    @Test fun `shuffledQueueWithStart empty returns empty`() {
        val (shuffled, idx) = shuffledQueueWithStart(emptyList(), 0)
        assertTrue(shuffled.isEmpty())
        assertEquals(0, idx)
    }

    @Test fun `dedupeKey prefers dlPath`() {
        val withPath = com.masstamilan.app.data.model.SongResult(
            name = "X", movieName = "M", dlPath = "/5/x-mp3-song"
        )
        assertEquals("/5/x-mp3-song", dedupeKey(withPath))
        val withoutPath = com.masstamilan.app.data.model.SongResult(name = "X", movieName = "M")
        assertEquals("M|X", dedupeKey(withoutPath))
    }
}
