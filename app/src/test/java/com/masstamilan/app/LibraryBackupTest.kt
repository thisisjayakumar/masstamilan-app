package com.masstamilan.app

import com.masstamilan.app.data.repository.BackupPlaylist
import com.masstamilan.app.data.repository.BackupSong
import com.masstamilan.app.data.repository.LibraryBackup
import com.masstamilan.app.data.repository.decodeLibraryBackup
import com.masstamilan.app.data.repository.encodeLibraryBackup
import org.junit.Assert.*
import org.junit.Test

class LibraryBackupTest {

    private fun sample() = LibraryBackup(
        exportedAt = 1728288000000,
        favorites = listOf(
            BackupSong(
                songId = 45295, name = "Ala Bolelo", artists = "Anirudh Ravichander",
                movieName = "Jailer 2", movieSlug = "jailer-2-2026-songs",
                songPagePath = "4738/ala-bolelo-mp3-song",
                imageName = "jailer-2-tamil-2026", addedAt = 1728288000000
            ),
            BackupSong(
                songId = 101, name = "கண்ணே", artists = "Ilaiyaraaja",
                movieName = "M", movieSlug = "m-songs", songPagePath = "",
                imageName = "art", addedAt = 1728288001000
            )
        ),
        playlists = listOf(
            BackupPlaylist(
                name = "Gym", createdAt = 1728288000000, updatedAt = 1728288100000,
                songs = listOf(
                    BackupSong(
                        songId = 45295, name = "Ala Bolelo", artists = "Anirudh",
                        movieSlug = "jailer-2-2026-songs",
                        songPagePath = "4738/ala-bolelo-mp3-song", addedAt = 45295
                    )
                )
            )
        )
    )

    @Test fun `round trip preserves metadata`() {
        val restored = decodeLibraryBackup(encodeLibraryBackup(sample())).getOrThrow()
        assertEquals(2, restored.favorites.size)
        assertEquals("Ala Bolelo", restored.favorites[0].name)
        assertEquals("4738/ala-bolelo-mp3-song", restored.favorites[0].songPagePath)
        assertEquals("கண்ணே", restored.favorites[1].name)
        assertEquals(1, restored.playlists.size)
        assertEquals("Gym", restored.playlists[0].name)
        assertEquals(45295, restored.playlists[0].songs[0].songId)
        assertEquals(1728288000000, restored.exportedAt)
    }

    @Test fun `backup carries no expiring stream urls`() {
        val json = encodeLibraryBackup(sample())
        assertFalse(json.contains("downloader"))
        assertFalse(json.contains("streamUrl"))
    }

    @Test fun `corrupt input fails without throwing`() {
        assertTrue(decodeLibraryBackup("").isFailure)
        assertTrue(decodeLibraryBackup("not json").isFailure)
        assertTrue(decodeLibraryBackup("{}").isFailure)
        assertTrue(decodeLibraryBackup("[1,2]").isFailure)
    }

    @Test fun `blank names skipped, rest restored`() {
        val json = """{"favorites":[{"name":""},{"name":"Ok","songId":5}],"playlists":[]}"""
        val restored = decodeLibraryBackup(json).getOrThrow()
        assertEquals(1, restored.favorites.size)
        assertEquals("Ok", restored.favorites[0].name)
        assertEquals(5, restored.favorites[0].songId)
    }
}
