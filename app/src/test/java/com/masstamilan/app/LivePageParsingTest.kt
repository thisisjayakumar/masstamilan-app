package com.masstamilan.app

import com.masstamilan.app.data.remote.MasstamilanParsers
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end parser checks against real pages captured from masstamilan.dev
 * with the same OkHttp client/headers the app uses (200 OK, no challenge).
 */
class LivePageParsingTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("live/$name")) { "missing fixture $name" }
            .readText()

    @Test fun `home page yields movie cards`() {
        val cards = MasstamilanParsers.parseMovieCards(fixture("home.html"))
        assertTrue("expected >= 8 cards, got ${cards.size}", cards.size >= 8)
        assertEquals("Jailer 2", cards[0].name)
        assertEquals("/jailer-2-2026-songs", cards[0].slug)
        assertTrue(cards[0].poster.startsWith("/i/") || cards[0].poster.startsWith("/w/"))
        assertTrue(cards[0].starring.isNotBlank())
        assertTrue(cards.all { it.slug.contains("-songs") })
    }

    @Test fun `search page yields movie cards with query stripped`() {
        val results = MasstamilanParsers.parseSearchMovies(fixture("search.html"))
        assertTrue("expected >= 3 results, got ${results.size}", results.size >= 3)
        val jailer = results.firstOrNull { it.name == "Jailer" }
        assertNotNull("Jailer card missing", jailer)
        assertEquals("/jailer-songs-3", jailer!!.slug)
        assertFalse(jailer.slug.contains("ref=search"))
        assertTrue(jailer.image.startsWith("/i/"))
    }

    @Test fun `movie page yields all album tracks`() {
        val songs = MasstamilanParsers.parseMovieTracks(fixture("movie.html"))
        assertEquals(13, songs.size)
        assertEquals("Ala Bolelo", songs[0].name)
        assertEquals("Jailer 2", songs[0].movieName)
        assertTrue(songs.all { it.dlPath.startsWith("/downloader/") })
        assertTrue(songs.all { it.name.isNotBlank() })
    }

    @Test fun `movie page tracks carry song page paths for quality resolution`() {
        val songs = MasstamilanParsers.parseMovieTracks(fixture("movie.html"))
        assertTrue(
            "expected every track to have a page path, missing: " +
                songs.filter { it.pagePath.isBlank() }.map { it.name },
            songs.all { it.pagePath.isNotBlank() }
        )
        assertEquals(
            "4738/ala-bolelo-mp3-song",
            songs.first { it.name == "Ala Bolelo" }.pagePath
        )
    }

    @Test fun `song page exposes both download qualities`() {
        val links = MasstamilanParsers.extractDownloadLinks(fixture("song.html"))
        assertTrue("expected 128kbps, got ${links.keys}", links.containsKey("128kbps"))
        assertTrue("expected 320kbps, got ${links.keys}", links.containsKey("320kbps"))
    }

    @Test fun `song page resolves 320kbps stream url`() {
        val url = MasstamilanParsers.extractStreamUrl(fixture("song.html"), prefer320 = true)
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://www.masstamilan.dev/downloader/"))
        assertTrue("expected d320 in $url", url.contains("d320_cdn"))
        assertTrue(url.contains("45295"))
    }

    @Test fun `movie name extracted from h1`() {
        assertEquals("Jailer 2", MasstamilanParsers.extractMovieName(fixture("movie.html")))
    }
}
