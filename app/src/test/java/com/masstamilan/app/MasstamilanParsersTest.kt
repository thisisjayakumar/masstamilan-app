package com.masstamilan.app

import com.masstamilan.app.data.remote.MasstamilanParsers
import com.masstamilan.app.data.remote.MiniJson
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
        val out = MiniJson.parseArrayOfObjects(
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
        assertTrue(MiniJson.parseArrayOfObjects("[]").isEmpty())
        try {
            MiniJson.parseArrayOfObjects("not json")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            MiniJson.parseArrayOfObjects("""[{"n":"x"}""")
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
}
