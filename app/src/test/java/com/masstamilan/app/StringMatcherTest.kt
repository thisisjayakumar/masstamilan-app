package com.masstamilan.app

import com.masstamilan.app.core.util.StringMatcher
import com.masstamilan.app.data.model.SongResult
import org.junit.Assert.*
import org.junit.Test

class StringMatcherTest {

    private fun song(name: String, artists: String = "", movie: String = "") =
        SongResult(name = name, artists = artists, movieName = movie, id = name.hashCode())

    // ---- Search quality: exact / prefix / substring ordering ----

    @Test fun `exact song title ranks first`() {
        val songs = listOf(
            song("Jai Jai Rama"),
            song("Rama Rama", artists = "Anirudh"),
            song("Other Song", movie = "Ram")
        )
        val ranked = StringMatcher.rankSongs("jai jai rama", songs)
        assertEquals("Jai Jai Rama", ranked[0].first.name)
    }

    @Test fun `prefix beats substring`() {
        val songs = listOf(
            song("Saravana Rama Song"),
            song("Ram"),
            song("Jai Jai Rama")
        )
        val ranked = StringMatcher.rankSongs("ram", songs)
        assertEquals("Ram", ranked[0].first.name)
    }

    @Test fun `word prefix matches - mun finds Munbe Vaa`() {
        val songs = listOf(
            song("Unrelated Song"),
            song("Munbe Vaa", artists = "Naresh Iyer", movie = "Vaaranam Aayiram")
        )
        val ranked = StringMatcher.rankSongs("mun", songs)
        assertEquals(1, ranked.size)
        assertEquals("Munbe Vaa", ranked[0].first.name)
    }

    @Test fun `song title beats same token in movie name`() {
        val songs = listOf(
            song("Unrelated", movie = "Ram"),
            song("Ram ram")
        )
        val ranked = StringMatcher.rankSongs("ram", songs)
        assertEquals("Ram ram", ranked[0].first.name)
    }

    @Test fun `irrelevant songs filtered out`() {
        val songs = listOf(song("Xyz Abc Def"), song("Qwerty Zxcv"))
        assertTrue(StringMatcher.rankSongs("ram", songs).isEmpty())
    }

    @Test fun `blank query returns empty`() {
        assertTrue(StringMatcher.rankSongs("", listOf(song("Ram"))).isEmpty())
        assertTrue(StringMatcher.rankSongs("  ", listOf(song("Ram"))).isEmpty())
    }

    // ---- Typo tolerance ----

    @Test fun `typo tolerance - kannae finds kanne`() {
        val songs = listOf(song("Kanne Kalaimane"), song("Totally Different"))
        val ranked = StringMatcher.rankSongs("kannae kalaimane", songs)
        assertTrue(ranked.isNotEmpty())
        assertEquals("Kanne Kalaimane", ranked[0].first.name)
    }

    // ---- Unit checks on primitives ----

    @Test fun `normalize strips punctuation and case`() {
        assertEquals("munbe vaa", StringMatcher.normalize("Munbe-Vaa!"))
    }

    @Test fun `levenshtein known values`() {
        assertEquals(0, StringMatcher.levenshtein("ram", "ram"))
        assertEquals(1, StringMatcher.levenshtein("ram", "raam"))
        assertEquals(3, StringMatcher.levenshtein("", "ram"))
    }

    @Test fun `similarity identical is 1`() {
        assertEquals(1.0, StringMatcher.similarity("Ram", "ram"), 0.001)
    }

    // ---- Realistic catalog simulation (search-quality regression) ----

    @Test fun `ram query ranks ram songs above unrelated in mixed catalog`() {
        val catalog = listOf(
            song("Jai Jai Rama", artists = "Anirudh", movie = "Ram"),
            song("Rama Rama", artists = "Harris Jayaraj", movie = "Velayudham"),
            song("Oh Ram", movie = "Kushi"),
            song("Munbe Vaa", artists = "Naresh Iyer", movie = "Vaaranam Aayiram"),
            song("Aaruyire", movie = "Guru"),
            song("Nenjukkul Peidhidum", movie = "Vaaranam Aayiram")
        )
        val ranked = StringMatcher.rankSongs("ram", catalog)
        assertTrue(ranked.size >= 3)
        val top3 = ranked.take(3).map { it.first.name }.toSet()
        assertTrue(top3.contains("Jai Jai Rama"))
        assertTrue(top3.contains("Rama Rama") || top3.contains("Oh Ram"))
        // unrelated song must not outrank ram songs
        assertFalse(ranked.first().first.name == "Munbe Vaa")
    }
}
