package com.masstamilan.app

import com.masstamilan.app.core.util.StringMatcher
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.repository.dedupeSongs
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the tail of unified song search (dedupe → fuzzy rank) without
 * network: the same pure functions [MasstamilanRepository.unifiedSongSearch]
 * applies after fan-out fetching movie pages.
 */
class UnifiedSearchPipelineTest {

    private fun song(
        name: String,
        dlPath: String = "/1/$name-mp3-song",
        movie: String = "M"
    ) = SongResult(name = name, movieName = movie, dlPath = dlPath, id = dlPath.hashCode())

    @Test fun `dedupe keeps first occurrence by dlPath`() {
        val songs = listOf(
            song("Ram Song", dlPath = "/5/ram-song-mp3-song", movie = "A"),
            song("Ram Song", dlPath = "/5/ram-song-mp3-song", movie = "B"),
            song("Other", dlPath = "/6/other-mp3-song", movie = "A")
        )
        val out = dedupeSongs(songs)
        assertEquals(2, out.size)
        assertEquals("A", out[0].movieName)
    }

    @Test fun `dedupe falls back to movie+name key when dlPath blank`() {
        val songs = listOf(
            song("X", dlPath = "", movie = "M"),
            song("X", dlPath = "", movie = "M"),
            song("X", dlPath = "", movie = "N")
        )
        assertEquals(2, dedupeSongs(songs).size)
    }

    @Test fun `dedupe preserves order`() {
        val songs = (1..5).map { song("S$it", dlPath = "/$it/s$it-mp3-song") }
        assertEquals(songs.map { it.dlPath }, dedupeSongs(songs).map { it.dlPath })
    }

    @Test fun `pipeline ranks ram query above unrelated after dedupe`() {
        val scraped = listOf(
            song("Jai Jai Rama", dlPath = "/5/jai-jai-rama-mp3-song", movie = "Ram"),
            song("Jai Jai Rama", dlPath = "/5/jai-jai-rama-mp3-song", movie = "Ram"),
            song("Munbe Vaa", dlPath = "/9/munbe-vaa-mp3-song", movie = "Vaaranam Aayiram"),
            song("Aaruyire", dlPath = "/9/aaruyire-mp3-song", movie = "Guru")
        )
        val ranked = StringMatcher.rankSongs("ram", dedupeSongs(scraped))
        assertTrue(ranked.isNotEmpty())
        assertEquals("Jai Jai Rama", ranked[0].first.name)
        assertFalse(ranked.any { it.first.name == "Munbe Vaa" && ranked.indexOf(it) == 0 })
    }

    @Test fun `pipeline empty in empty out`() {
        assertTrue(dedupeSongs(emptyList()).isEmpty())
        assertTrue(StringMatcher.rankSongs("ram", dedupeSongs(emptyList())).isEmpty())
    }
}
