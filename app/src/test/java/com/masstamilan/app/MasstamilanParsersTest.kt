package com.masstamilan.app

import com.masstamilan.app.data.remote.MasstamilanParsers
import com.masstamilan.app.core.util.Json
import org.junit.Assert.*
import org.junit.Test

class MasstamilanParsersTest {

    private val movieHtml = """
        <html><head><title>Test</title></head><body>
        <h1>Vaaranam Aayiram Tamil mp3 songs download MassTamilan.com</h1>
        <b>Artists:</b> <span itemprop="byArtist">Harris Jayaraj, Hariharan</span>
        <script>window.albumTracks = [{"id":101,"name":"Munbe Vaa","artists":"Naresh Iyer",
          "m_name":"Vaaranam Aayiram","img_name":"vaaranam.jpg",
          "dl_path":"/downloader/sig/ts/d128_cdn/101/abc","length":"5:12","downloads":999}];</script>
        <h2 class="nostyle"><span itemprop="name">
          <link itemprop="url" href="/101/munbe-vaa-mp3-song" title="Download Munbe Vaa mp3 song">Munbe Vaa</span></h2>
        <b>Length:</b> <span itemprop="duration">5:12</span>
        <a class="dlink" href="/downloader/sig/ts/d128_cdn/101/abc" rel="nofollow"
           title="Download Munbe Vaa 128kbps" download> 128kbps (4.8 MB)</a>
        <a class="dlink" href="/downloader/sig/ts/d320_cdn/101/xyz" rel="nofollow"
           title="Download Munbe Vaa 320kbps" download> 320kbps (12.1 MB)</a>
        </body></html>
    """.trimIndent()

    @Test fun `movie tracks prefer albumTracks JS with dl_path`() {
        val songs = MasstamilanParsers.parseMovieTracks(movieHtml)
        assertEquals(1, songs.size)
        assertEquals("Munbe Vaa", songs[0].name)
        assertEquals(101, songs[0].id)
        assertTrue(songs[0].dlPath.startsWith("/downloader/"))
        assertEquals("Vaaranam Aayiram", songs[0].movieName)
    }

    @Test fun `movie tracks fallback to h2 link when no JS block`() {
        val html = """
            <h1>Ram Tamil mp3 songs</h1>
            <b>Artists:</b> <span itemprop="byArtist">Anirudh</span>
            <h2 class="nostyle"><span itemprop="name">
            <link itemprop="url" href="/555/jai-jai-rama-mp3-song"
                  title="Download Jai Jai Rama mp3 song">Jai Jai Rama</span></h2>
        """.trimIndent()
        val songs = MasstamilanParsers.parseMovieTracks(html)
        assertEquals(1, songs.size)
        assertEquals("Jai Jai Rama", songs[0].name)
        assertEquals(555, songs[0].id)
        assertEquals("/555/jai-jai-rama-mp3-song", songs[0].dlPath)
    }

    @Test fun `empty html returns empty list, never throws`() {
        assertTrue(MasstamilanParsers.parseMovieTracks("").isEmpty())
        assertTrue(MasstamilanParsers.parseMovieTracks("<html></html>").isEmpty())
        assertTrue(MasstamilanParsers.parseSearchMovies("").isEmpty())
    }

    @Test fun `search movies parses cards and dedupes`() {
        val html = """
            <a href="/vaaranam-aayiram-songs?ref=search"><img src="/i/vaa.jpg" alt="Vaaranam">
              <h2>Vaaranam Aayiram</h2></a>
            <a href="/vaaranam-aayiram-songs?ref=search"><img src="/i/vaa.jpg" alt="Vaaranam">
              <h2>Vaaranam Aayiram</h2></a>
            <a href="/ram-songs?ref=search"><img src="/i/ram.jpg" alt="Ram">
              <h2>Ram</h2></a>
        """.trimIndent()
        val results = MasstamilanParsers.parseSearchMovies(html)
        assertEquals(2, results.size)
        assertEquals("Vaaranam Aayiram", results[0].name)
        assertEquals("/vaaranam-aayiram-songs", results[0].slug)
        assertEquals("/i/vaa.jpg", results[0].image)
    }

    @Test fun `autocomplete parses n,s,l JSON`() {
        val json = """[{"n":"Vaaranam Aayiram","s":"vaaranam-aayiram-songs","l":"/vaaranam-aayiram-songs"},
                      {"n":"Ram","s":"ram-songs","l":"/ram-songs"}]"""
        val out = MasstamilanParsers.parseAutocomplete(json)
        assertEquals(2, out.size)
        assertEquals("Vaaranam Aayiram", out[0].name)
        assertEquals("vaaranam-aayiram-songs", out[0].slug)
    }

    @Test fun `autocomplete never throws on garbage`() {
        assertTrue(MasstamilanParsers.parseAutocomplete("").isEmpty())
        assertTrue(MasstamilanParsers.parseAutocomplete("not json").isEmpty())
        assertTrue(MasstamilanParsers.parseAutocomplete("<html>").isEmpty())
    }

    @Test fun `download links prefer 320kbps`() {
        val links = MasstamilanParsers.extractDownloadLinks(movieHtml)
        assertEquals(2, links.size)
        assertTrue(links["320kbps"]!!.contains("d320_cdn"))
        assertEquals(links["320kbps"], MasstamilanParsers.extractDownloadUrl(movieHtml, "320kbps"))
        // unknown quality falls back to 320
        assertEquals(links["320kbps"], MasstamilanParsers.extractDownloadUrl(movieHtml, "999kbps"))
    }

    @Test fun `stream url resolves dlink first`() {
        val url = MasstamilanParsers.extractStreamUrl(movieHtml, prefer320 = true)
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://www.masstamilan.dev/downloader/"))
        assertTrue(url.contains("d320_cdn"))
    }

    @Test fun `albumTracks raw array parses`() {
        val json = """[{"id":1,"name":"A","artists":"B","m_name":"M","length":"3:00",
                        "dl_path":"/downloader/x","img_name":"i.jpg","downloads":5}]"""
        val tracks = MasstamilanParsers.parseAlbumTracksJson(json)
        assertEquals(1, tracks.size)
        assertEquals("A", tracks[0].name)
        assertEquals("/downloader/x", tracks[0].dlPath)
    }

    @Test fun `html entities decoded`() {
        assertEquals("A & B", MasstamilanParsers.decodeHtml("A &amp; B"))
    }

    @Test fun `miniJson parses escapes numbers and unicode`() {
        val out = Json.parseArrayOfObjects(
            """[{"n":"A \"B\"","id":101,"x":null,"t":true,"u":"\u0b85"}]"""
        )
        assertEquals(1, out.size)
        assertEquals("A \"B\"", out[0]["n"])
        assertEquals("101", out[0]["id"])
        assertEquals("", out[0]["x"])
        assertEquals("true", out[0]["t"])
        assertEquals("\u0b85", out[0]["u"])
    }

    @Test fun `miniJson empty array and malformed`() {
        assertTrue(Json.parseArrayOfObjects("[]").isEmpty())
        try {
            Json.parseArrayOfObjects("not json")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            Json.parseArrayOfObjects("""[{"n":"x"}""")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun `albumTracks object form parses`() {
        val json = """{"albumTracks":[{"id":7,"name":"Z","artists":"A","m_name":"M",
                      "dl_path":"/downloader/q","img_name":"i.jpg","length":"4:00","downloads":3}]}"""
        val tracks = MasstamilanParsers.parseAlbumTracksJson(json)
        assertEquals(1, tracks.size)
        assertEquals("Z", tracks[0].name)
        assertEquals(7, tracks[0].id)
    }

    // ---- Fixtures below mirror live masstamilan.dev markup (captured 2026-10-01) ----

    private val liveHomeCard = """
        <div class="a-i">
    <a href="/jailer-2-2026-songs" title="Jailer 2 tamil songs download">
      <picture>
        <source srcset="/w/jailer-2-tamil-2026.webp" type="image/webp">
        <source srcset="/i/jailer-2-tamil-2026.jpg" type="image/jpeg">
        <img alt="Jailer 2 movie poster" title="Jailer 2 Movie Poster" src="/i/jailer-2-tamil-2026.jpg" loading="lazy" width="100" height="100">
      </picture>
      <div class="mw0">
        <h2>Jailer 2</h2>
        <p>
          <b>Starring:</b> Rajinikanth<br>
          <b>Music:</b> Anirudh Ravichander<br>
          <b>Director:</b> Nelson
        </p>
      </div>
    </a>
  </div>
    """.trimIndent()

    private val liveSearchCard = """
        <div class="a-i">
    <a href="/jailer-songs-3?ref=search" title="Jailer tamil songs download">
      <picture>
        <source srcset="/w/jailer-tamil-2023.webp" type="image/webp">
        <img alt="Jailer movie poster" title="Jailer Movie Poster" src="/i/jailer-tamil-2023.jpg" loading="lazy" width="100" height="100">
      </picture>
      <div class="mw0">
        <h2>Jailer</h2>
        <p>
          <b>Starring:</b> Rajnikanth, Mohan Lal, Jackie Shroff, Tamannah<br>
          <b>Music:</b> Anirudh Ravichander
        </p>
      </div>
    </a>
  </div>
    """.trimIndent()

    @Test fun `home card with picture element parses`() {
        val cards = MasstamilanParsers.parseMovieCards(liveHomeCard)
        assertEquals(1, cards.size)
        assertEquals("Jailer 2", cards[0].name)
        assertEquals("/jailer-2-2026-songs", cards[0].slug)
        assertEquals("/i/jailer-2-tamil-2026.jpg", cards[0].poster)
        assertEquals("Rajinikanth", cards[0].starring)
    }

    @Test fun `search card strips ref query and parses`() {
        val results = MasstamilanParsers.parseSearchMovies(liveSearchCard)
        assertEquals(1, results.size)
        assertEquals("Jailer", results[0].name)
        assertEquals("/jailer-songs-3", results[0].slug)
        assertEquals("/i/jailer-tamil-2023.jpg", results[0].image)
    }

    @Test fun `movie cards ignore non-movie links without image`() {
        val html = """
            <p><a href="/playlists?ref=banner">Playlists</a></p>
            <a href="/tamil-songs">Tamil Songs</a>
            $liveHomeCard
        """.trimIndent()
        val cards = MasstamilanParsers.parseMovieCards(html)
        assertEquals(1, cards.size)
        assertEquals("/jailer-2-2026-songs", cards[0].slug)
    }

    @Test fun `autocomplete live payload maps l to slug`() {
        val json = """[{"n":"Jailer","s":"Tamil — 2023","l":"jailer-songs-3"},
                      {"n":"Jailer 2","s":"Tamil — 2026","l":"jailer-2-2026-songs"}]"""
        val out = MasstamilanParsers.parseAutocomplete(json)
        assertEquals(2, out.size)
        assertEquals("jailer-songs-3", out[0].slug)
        assertEquals("jailer-2-2026-songs", out[1].slug)
        assertEquals("Jailer", out[0].name)
    }

    @Test fun `live song page dlink picks 320kbps`() {
        val html = """
            <h1>Ala Bolelo Song Download MassTamilan.com from Jailer 2 (2026)</h1>
            <a class="dlink" href="/downloader/sig/ts/d128_cdn/45295/abc" rel="nofollow" title="Download Ala Bolelo 128kbps">128kbps (3.7 MB)</a>
            <a class="dlink" href="/downloader/sig/ts/d320_cdn/45295/xyz" rel="nofollow" title="Download Ala Bolelo 320kbps">320kbps (7.8 MB)</a>
        """.trimIndent()
        val url = MasstamilanParsers.extractStreamUrl(html, prefer320 = true)
        assertEquals("https://www.masstamilan.dev/downloader/sig/ts/d320_cdn/45295/xyz", url)
    }
}
