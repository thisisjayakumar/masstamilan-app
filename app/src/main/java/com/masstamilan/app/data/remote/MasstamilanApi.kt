package com.masstamilan.app.data.remote

import com.masstamilan.app.data.model.AutocompleteSuggestion
import com.masstamilan.app.data.model.SearchResult
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.core.util.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasstamilanApi @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        const val BASE_URL = "https://www.masstamilan.dev"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
    }

    private suspend fun get(pathOrUrl: String): String = withContext(Dispatchers.IO) {
        val url = if (pathOrUrl.startsWith("http")) pathOrUrl else BASE_URL + pathOrUrl
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json")
            .header("Accept-Language", "en-US,en;q=0.9,ta;q=0.8")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            resp.body?.string() ?: ""
        }
    }

    class IOException(message: String) : java.io.IOException(message)

    suspend fun getHomePage(): String = get("/")

    suspend fun searchMoviesHtml(keyword: String, page: Int = 1): String {
        val q = URLEncoder.encode(keyword, "UTF-8")
        return get("/search?keyword=$q&page=$page")
    }

    suspend fun autocomplete(keyword: String): List<AutocompleteSuggestion> {
        if (keyword.isBlank()) return emptyList()
        return try {
            val q = URLEncoder.encode(keyword.trim(), "UTF-8")
            val json = get("/search/ac?keyword=$q")
            MasstamilanParsers.parseAutocomplete(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getMoviePage(movieSlug: String): String {
        val slug = movieSlug.trim('/').replace("?ref=search", "")
        return get("/$slug")
    }

    suspend fun getSongPage(songPath: String): String {
        val p = "/" + songPath.trim('/')
        return get(p)
    }

    suspend fun searchMovies(keyword: String): List<SearchResult> {
        if (keyword.isBlank()) return emptyList()
        return try {
            MasstamilanParsers.parseSearchMovies(searchMoviesHtml(keyword))
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getSongsFromMovieSlug(movieSlug: String): List<SongResult> {
        return try {
            val html = getMoviePage(movieSlug)
            MasstamilanParsers.parseMovieTracks(html)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resolveStreamUrl(songPath: String, prefer320: Boolean = true): String? {
        return try {
            val html = getSongPage(songPath)
            MasstamilanParsers.extractStreamUrl(html, prefer320)
        } catch (_: Exception) {
            null
        }
    }

    fun getSongsFromMoviePage(html: String): List<SongResult> =
        MasstamilanParsers.parseMovieTracks(html)

    fun getSearchResults(html: String): List<SearchResult> =
        MasstamilanParsers.parseSearchMovies(html)

    fun parseSearchResults(html: String): List<SearchResult> =
        MasstamilanParsers.parseSearchMovies(html)

    fun searchSongs(keyword: String): List<SearchResult> =
        throw UnsupportedOperationException("Use suspend searchMovies() from a coroutine")

    fun getDownloadUrl(html: String, quality: String = "320kbps"): String? =
        MasstamilanParsers.extractDownloadUrl(html, quality)

    fun getAlbumTracks(json: String): List<SongResult> =
        MasstamilanParsers.parseAlbumTracksJson(json)
}

data class MovieCard(
    val name: String,
    val slug: String,
    val poster: String,
    val starring: String = ""
)

object MasstamilanParsers {



    fun parseAutocomplete(json: String): List<AutocompleteSuggestion> {
        if (json.isBlank()) return emptyList()
        return try {
            Json.parseArrayOfObjects(json.trim()).mapNotNull { o ->
                val name = o["n"].ifNullOrBlank { o["name"] } ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                val link = (o["l"] ?: o["link"] ?: "").trim().trim('/')
                val slug = link.ifBlank { (o["s"] ?: o["slug"] ?: "").trim().trim('/') }
                if (slug.isBlank()) return@mapNotNull null
                AutocompleteSuggestion(name = name, slug = slug, link = link)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun String?.ifNullOrBlank(fallback: () -> String?): String? {
        if (!this.isNullOrBlank()) return this
        return fallback()
    }

    /**
     * Parse movie cards using Jsoup — robust against markup changes.
     * Matches <a href="/movie-songs"> containing a <picture>/<img> and <h2>.
     */
    fun parseMovieCards(html: String): List<MovieCard> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<MovieCard>()
        doc.select("a[href]").forEach { anchor ->
            val href = anchor.attr("href").trim()
            if (!href.startsWith("/")) return@forEach
            val slug = href.substringBefore("?").substringBefore("#").trim()
            if (!slug.contains("-songs")) return@forEach
            val img = anchor.selectFirst("img[src]") ?: return@forEach
            val poster = img.attr("src").trim()
            if (poster.isBlank()) return@forEach
            val h2 = anchor.selectFirst("h2") ?: anchor.selectFirst("h2[title]")
            val nameFromH2 = h2?.ownText()?.trim().orEmpty()
            val alt = img.attr("alt").trim()
            val name = if (nameFromH2.isNotBlank()) nameFromH2 else alt
            if (name.isBlank() || !seen.add(slug)) return@forEach
            val starring = extractStarring(anchor)
            out.add(
                MovieCard(
                    name = decodeHtml(name),
                    slug = slug,
                    poster = poster,
                    starring = decodeHtml(starring)
                )
            )
        }
        return out
    }

    /** Text right after the <b>Starring:</b> label, stopping at the next <b>/<br>. */
    private fun extractStarring(anchor: Element): String {
        val p = anchor.selectFirst("p") ?: return ""
        val kids = p.childNodes()
        var i = kids.indexOfFirst {
            it is Element && it.tagName() == "b" && it.text().contains("Starring", ignoreCase = true)
        }
        if (i < 0) return ""
        val sb = StringBuilder()
        i++
        while (i < kids.size) {
            val n = kids[i++]
            if (n is Element && (n.tagName() == "b" || n.tagName() == "br")) break
            if (n is org.jsoup.nodes.TextNode) sb.append(n.text())
        }
        return sb.toString().trim().trimEnd(',')
    }

    fun parseSearchMovies(html: String): List<SearchResult> =
        parseMovieCards(html).map { SearchResult(name = it.name, slug = it.slug, image = it.poster) }

    fun parseMovieTracks(html: String, movieNameFallback: String = ""): List<SongResult> {
        if (html.isBlank()) return emptyList()
        val movieName = extractMovieName(html).ifBlank { movieNameFallback }
        val jsTracks = parseAlbumTracksFromHtml(html)
        if (jsTracks.isNotEmpty()) {
            // window.albumTracks dl_paths are p128 preview streams (/downloader/…),
            // so the song-page path must come from the track-table anchors instead —
            // otherwise downloads/streaming can only ever offer the 128kbps fallback.
            val pageByName = extractSongPagePaths(html)
            return jsTracks.map { t ->
                t.copy(
                    movieName = t.movieName.ifBlank { movieName },
                    pagePath = pageByName[normalizeSongName(t.name)].orEmpty()
                )
            }
        }
        val doc = Jsoup.parse(html)
        val artists = extractArtists(doc)
        val imgName = extractImageName(doc)
        val songs = mutableListOf<SongResult>()
        doc.select("h2.nostyle, h2[itemprop], h2").forEach { h2 ->
            // Live markup nests a <link itemprop="url" href="/id/song-mp3-song"> inside the h2.
            val path = h2.selectFirst("a[href]")?.attr("href")?.trim()
                ?: h2.selectFirst("link[href]")?.attr("href")?.trim()
                .orEmpty()
            if (!path.startsWith("/") || !path.contains("-mp3-song")) return@forEach
            val id = path.split("/").getOrNull(1)?.toIntOrNull() ?: 0
            val name = decodeHtml(h2.text().trim())
            if (name.isBlank()) return@forEach
            songs.add(
                SongResult(
                    name = name, artists = artists, movieName = movieName,
                    duration = extractDuration(doc, h2),
                    id = id, dlPath = path, imageName = imgName,
                    pagePath = path.trim('/')
                )
            )
        }
        if (songs.isNotEmpty()) return songs
        doc.select("a[href]").forEach { anchor ->
            val href = anchor.attr("href").trim()
            if (!href.matches(Regex("""/\d+/[a-z0-9\-]+-mp3-song"""))) return@forEach
            val name = anchor.ownText().trim()
            if (name.isBlank()) return@forEach
            val id = href.split("/").getOrNull(1)?.toIntOrNull() ?: 0
            songs.add(
                SongResult(
                    name = decodeHtml(name), artists = artists, movieName = movieName,
                    id = id, dlPath = href, imageName = imgName,
                    pagePath = href.trim('/')
                )
            )
        }
        return songs
    }

    /**
     * Map normalized song name → song-page path (e.g. "4738/ala-bolelo-mp3-song")
     * from the track-table anchors. The album JSON only carries preview-stream
     * dl_paths, so this is what unlocks per-song 128/320kbps resolution.
     * Pure + unit-tested.
     */
    fun extractSongPagePaths(html: String): Map<String, String> {
        if (html.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        Jsoup.parse(html).select("link[href], a[href]").forEach { el ->
            val raw = el.attr("href").trim()
            val path = raw.substringBefore("?").substringBefore("#").trim()
            if (!path.matches(Regex("""/\d+/[a-z0-9\-]+-mp3-song"""))) return@forEach
            val pagePath = path.trim('/')
            // Prefer the title ("Download X mp3 song") over the visible text,
            // which can carry trailing whitespace. link[itemprop=url] tags have
            // no title/text, so fall back to the sibling track name.
            val title = el.attr("title").trim()
            val fromTitle = Regex("""^Download\s+(.+?)\s+mp3 song$""", RegexOption.IGNORE_CASE)
                .find(title)?.groupValues?.get(1)
            val label = fromTitle ?: el.text().ifBlank {
                el.parent()?.selectFirst("a[href]")?.text().orEmpty()
            }
            val key = normalizeSongName(label)
            if (key.isNotBlank()) out.putIfAbsent(key, pagePath)
        }
        return out
    }

    /** Lowercase + collapse whitespace so "Ala Bolelo " matches "ala bolelo". */
    fun normalizeSongName(s: String): String =
        decodeHtml(s).lowercase().replace(Regex("""\s+"""), " ").trim()

    private fun extractArtists(doc: org.jsoup.nodes.Document): String {
        val label = doc.select("b:contains(Artists)").firstOrNull()
            ?: doc.select("b:contains(artist)").firstOrNull()
            ?: return ""
        val span = label.nextElementSibling()
        return if (span != null) decodeHtml(span.ownText().trim()) else ""
    }

    private fun extractImageName(doc: org.jsoup.nodes.Document): String {
        val img = doc.selectFirst("img[src]") ?: return ""
        val src = img.attr("src").trim()
        return src.substringAfterLast("/").substringBeforeLast(".", src)
    }

    private fun extractDuration(doc: org.jsoup.nodes.Document, anchor: Element): String {
        val label = doc.select("b:contains(Length)").firstOrNull()
            ?: doc.select("b:contains(length)").firstOrNull()
            ?: return ""
        val span = label.nextElementSibling()
        return span?.ownText()?.trim() ?: ""
    }

    fun parseAlbumTracksFromHtml(html: String): List<SongResult> {
        val m = Regex("""window\.albumTracks\s*=\s*(\[.*?\])\s*;""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: return emptyList()
        return parseAlbumTracksJson(m.groupValues[1])
    }

    fun parseAlbumTracksJson(json: String): List<SongResult> {
        if (json.isBlank()) return emptyList()
        val arrayText = run {
            val t = json.trim()
            if (t.startsWith("[")) return@run t
            val inner = Regex(""""albumTracks"\s*:\s*(\[.*\])""", RegexOption.DOT_MATCHES_ALL)
                .find(t)?.groupValues?.get(1)
            inner ?: return emptyList()
        }
        return try {
            Json.parseArrayOfObjects(arrayText).map { o ->
                SongResult(
                    name = o["name"] ?: "",
                    artists = o["artists"] ?: "",
                    movieName = o["m_name"] ?: "",
                    duration = o["length"] ?: "",
                    id = o["id"]?.toIntOrNull() ?: 0,
                    dlPath = o["dl_path"] ?: "",
                    imageName = o["img_name"] ?: "",
                    downloads = o["downloads"]?.toIntOrNull() ?: 0
                )
            }.filter { it.name.isNotBlank() }
        } catch (_: Exception) {
            parseAlbumTracksRegexFallback(arrayText)
        }
    }

    private fun parseAlbumTracksRegexFallback(arrayText: String): List<SongResult> {
        val objRe = Regex("""\{[^{}]*"name"\s*:[^{}]*\}""")
        return objRe.findAll(arrayText).mapNotNull { m ->
            val o = m.value
            fun f(field: String): String =
                Regex(""""$field"\s*:\s*(?:"((?:[^"\\]|\\.)*)"|(\d+))""")
                    .find(o)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } } ?: ""
            val name = f("name")
            if (name.isBlank()) return@mapNotNull null
            SongResult(
                name = name, artists = f("artists"), movieName = f("m_name"),
                duration = f("length"), id = f("id").toIntOrNull() ?: 0,
                dlPath = f("dl_path"), imageName = f("img_name"),
                downloads = f("downloads").toIntOrNull() ?: 0
            )
        }.toList()
    }

    private val DLINK = Regex(
        """<a\s+class="dlink"\s+href="(/downloader/[^"]+)"[^>]*title="Download\s+[^"]*?(\d+kbps)"[^>]*>\s*(\d+kbps)""",
        RegexOption.IGNORE_CASE
    )

    fun extractDownloadLinks(html: String): Map<String, String> {
        if (html.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        DLINK.findAll(html).forEach { m ->
            val href = m.groupValues[1]
            val quality = (m.groupValues[2].ifBlank { m.groupValues[3] }).lowercase()
            out.putIfAbsent(quality, MasstamilanApi.BASE_URL + href)
        }
        return out
    }

    fun extractDownloadUrl(html: String, quality: String = "320kbps"): String? {
        val links = extractDownloadLinks(html)
        val q = quality.lowercase()
        return links[q]
            ?: links["320kbps"]
            ?: links["128kbps"]
            ?: links.values.firstOrNull()
    }

    fun extractStreamUrl(html: String, prefer320: Boolean = true): String? {
        extractDownloadUrl(html, if (prefer320) "320kbps" else "128kbps")?.let { return it }
        val track = parseAlbumTracksFromHtml(html).firstOrNull { it.dlPath.isNotBlank() }
        track?.let {
            val p = it.dlPath
            if (p.startsWith("http")) return p
            if (p.startsWith("/downloader/")) return MasstamilanApi.BASE_URL + p
        }
        return null
    }

    fun extractMovieName(html: String): String {
        val doc = Jsoup.parse(html)
        val h1 = doc.selectFirst("h1")?.ownText()?.trim() ?: return ""
        return h1
            .replace("Tamil mp3 songs download.*".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("MassTamilan.*".toRegex(RegexOption.IGNORE_CASE), "")
            .trim(' ', '-', '|')
            .trim()
    }

    fun parseTotalResults(html: String): Int {
        val m = Regex("""(\d[\d,]*)\s+results?|page\s+1\s*/\s*(\d+)""",
            RegexOption.IGNORE_CASE).find(html) ?: return -1
        val num = m.groupValues[1].ifBlank { m.groupValues[2] }.replace(",", "")
        return num.toIntOrNull() ?: -1
    }

    fun decodeHtml(s: String): String =
        s.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
}