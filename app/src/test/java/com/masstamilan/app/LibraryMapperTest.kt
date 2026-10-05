package com.masstamilan.app

import com.masstamilan.app.data.entity.FavoriteEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import com.masstamilan.app.data.repository.dedupeKey
import com.masstamilan.app.data.repository.queueTrackFor
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
        assertNull(t.streamUrl) // resolves lazily via StreamResolver
        assertEquals("https://www.masstamilan.dev/i/vaa.jpg", t.artwork)
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

    @Test fun `queueTrackFor builds from song parts`() {
        val t = queueTrackFor(9, "A", "B", "art", "9/a-mp3-song", "m-songs")
        assertEquals(9, t.songId)
        assertEquals("A", t.title)
        assertEquals("9/a-mp3-song", t.songPagePath)
        assertEquals("m-songs", t.movieSlug)
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
