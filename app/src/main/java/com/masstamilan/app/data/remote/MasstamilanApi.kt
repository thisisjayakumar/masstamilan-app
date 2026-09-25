package com.masstamilan.app.data.remote

import com.masstamilan.app.data.model.AutocompleteSuggestion
import com.masstamilan.app.data.model.SearchResult
import com.masstamilan.app.data.model.SongResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network layer for masstamilan.dev (Rails SSR, no public API — HTML scrape).
 *
 * All I/O is suspend + Dispatchers.IO. All HTML/JSON parsing lives in
 * [MasstamilanParsers] (pure functions, unit-tested) so this class stays thin.
 */
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

    /** Autocomplete JSON: [{n,s,l}] -> typed suggestions. Never throws. */
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

    // ---- High-level helpers (compose network + pure parsers) ----

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

    /**
     * Resolve a playable stream URL for a song page path like "12345/song-mp3-song".
     * Prefers 320kbps dlink, falls back to 128kbps, then albumTracks dl_path.
     */
    suspend fun resolveStreamUrl(songPath: String, prefer320: Boolean = true): String? {
        return try {
            val html = getSongPage(songPath)
            MasstamilanParsers.extractStreamUrl(html, prefer320)
        } catch (_: Exception) {
            null
        }
    }

    // ---- Backward-compatible shims (pure, no I/O) ----
    // Keep old call sites compiling; delegate to parsers.

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

/**
 * Pure parsing functions — no Android, no I/O. Fully unit-testable.
 */
object MasstamilanParsers {

    // Matches: <h2 class="nostyle"><span itemprop="name">
    //   <link itemprop="url" href="/12345/song-mp3-song" title="Download X mp3 song">Song Name</span>
    private val SONG_LINK = Regex(
        """<h2\s+class="nostyle">\s*<span\s+itemprop="name">\s*<link\s+itemprop="url"\s+href="(/(\d+)/[^"]+)"[^>]*title="Download\s+([^"]+?)\s+mp3 song"[^>]*>\s*([^<]+)""",
        RegexOption.IGNORE_CASE
    )
    // Looser fallback: any song-page link
    private val SONG_LINK_LOOSE = Regex(
        """href="(/(\d+)/[a-z0-9\-]+-mp3-song)"[^>]{0,200}?title="Download\s+([^"]+?)(?:\s+mp3 song)?"""",
        RegexOption.IGNORE_CASE
    )
    private val SONG_NAME_FALLBACK = Regex(
        """href="/\d+/[a-z0-9\-]+-mp3-song"[^>]*>\s*([^<]{2,120})""",
        RegexOption.IGNORE_CASE
    )

    private val ARTISTS = Regex(
        """<b>\s*Artists:\s*</b>\s*<span\s+itemprop="byArtist">\s*([^<]+)""",
        RegexOption.IGNORE_CASE
    )
    private val DURATION = Regex(
        """<b>\s*Length:\s*</b>\s*<span\s+itemprop="duration">\s*([^<]+)""",
        RegexOption.IGNORE_CASE
    )
    private val DOWNLOADS = Regex(
        """<b>\s*Downloads:\s*</b>\s*<span[^>]*>\s*([\d,]+)""",
        RegexOption.IGNORE_CASE
    )
    private val MOVIE_H1 = Regex("""<h1[^>]*>\s*([^<]{2,200})""", RegexOption.IGNORE_CASE)

    // <a class="dlink" href="/downloader/..." ... title="Download X 320kbps" ...> 320kbps (8.1 MB)
    private val DLINK = Regex(
        """<a\s+class="dlink"\s+href="(/downloader/[^"]+)"[^>]*title="Download\s+[^"]*?(\d+kbps)"[^>]*>\s*(\d+kbps)""",
        RegexOption.IGNORE_CASE
    )

    // window.albumTracks = [{...}]  — capture the array
    private val ALBUM_TRACKS_JS = Regex(
        """window\.albumTracks\s*=\s*(\[.*?\])\s*;""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val JS_OBJ_FIELD = { field: String ->
        Regex(""""$field"\s*:\s*(?:"((?:[^"\\]|\\.)*)"|(\d+))""")
    }

    private val IMG_IN_TRACK = Regex(""""img_name"\s*:\s*"([^"]+)"""")
    private val DL_PATH_IN_TRACK = Regex(""""dl_path"\s*:\s*"([^"]+)"""")

    // Search page movie cards: <a href="/movie-songs?ref=search"...><img ... alt="..."><h2>Movie</h2>
    private val SEARCH_CARD = Regex(
        """<a\s+href="((?:/[a-z0-9\-]+)+(?:\?ref=search)?)"[^>]*>\s*<img[^>]*?(?:alt="([^"]*)")?[^>]*>\s*<h2[^>]*>\s*([^<]+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val SEARCH_IMG = Regex(
        """<a\s+href="((?:/[a-z0-9\-]+)+(?:\?ref=search)?)"[^>]*>\s*<img[^>]+src="([^"]+)"""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val TOTAL_RESULTS = Regex(
        """(\d[\d,]*)\s+results?|page\s+1\s*/\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun parseAutocomplete(json: String): List<AutocompleteSuggestion> {
        if (json.isBlank()) return emptyList()
        return try {
            MiniJson.parseArrayOfObjects(json.trim()).mapNotNull { o ->
                val name = o["n"].ifNullOrBlank { o["name"] } ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                AutocompleteSuggestion(
                    name = name,
                    slug = o["s"].ifNullOrBlank { o["slug"] } ?: "",
                    link = o["l"].ifNullOrBlank { o["link"] } ?: ""
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun String?.ifNullOrBlank(fallback: () -> String?): String? {
        if (!this.isNullOrBlank()) return this
        return fallback()
    }

    fun parseSearchMovies(html: String): List<SearchResult> {
        if (html.isBlank()) return emptyList()
        val imgs = mutableMapOf<String, String>()
        SEARCH_IMG.findAll(html).forEach { m ->
            val href = m.groupValues[1].replace("?ref=search", "").trim()
            val src = m.groupValues[2].trim()
            if (href.isNotBlank() && src.isNotBlank()) imgs.putIfAbsent(href, src)
        }
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<SearchResult>()
        SEARCH_CARD.findAll(html).forEach { m ->
            val slug = m.groupValues[1].replace("?ref=search", "").trim()
            val alt = m.groupValues[2].trim()
            val title = m.groupValues[3].trim()
            val name = title.ifBlank { alt }.trim()
            if (slug.isBlank() || name.isBlank() || !seen.add(slug)) return@forEach
            if (!slug.endsWith("-songs") && !slug.matches(Regex("""/[a-z0-9\-]+"""))) return@forEach
            out.add(SearchResult(name = decodeHtml(name), slug = slug, image = imgs[slug] ?: ""))
        }
        return out
    }

    fun parseMovieTracks(html: String, movieNameFallback: String = ""): List<SongResult> {
        if (html.isBlank()) return emptyList()
        val movieName = extractMovieName(html).ifBlank { movieNameFallback }
        // Prefer albumTracks JS (has dl_path + img) when present
        val jsTracks = parseAlbumTracksFromHtml(html)
        if (jsTracks.isNotEmpty()) {
            return jsTracks.map { t ->
                t.copy(movieName = t.movieName.ifBlank { movieName })
            }
        }
        val artists = ARTISTS.find(html)?.groupValues?.get(1)?.trim()?.let(::decodeHtml) ?: ""
        val imgName = IMG_IN_TRACK.find(html)?.groupValues?.get(1)?.trim() ?: ""
        val songs = mutableListOf<SongResult>()
        val matches = SONG_LINK.findAll(html).toList()
        if (matches.isNotEmpty()) {
            matches.forEach { m ->
                val path = m.groupValues[1].trim()
                val id = m.groupValues[2].toIntOrNull() ?: 0
                val name = decodeHtml(m.groupValues[4].trim())
                songs.add(
                    SongResult(
                        name = name, artists = artists, movieName = movieName,
                        duration = durationNear(html, m.range.first),
                        id = id, dlPath = path, imageName = imgName
                    )
                )
            }
            return songs
        }
        // Loose fallback
        SONG_LINK_LOOSE.findAll(html).forEach { m ->
            val path = m.groupValues[1].trim()
            val id = m.groupValues[2].toIntOrNull() ?: 0
            val name = decodeHtml(m.groupValues[3].trim())
            if (name.isNotBlank()) {
                songs.add(
                    SongResult(
                        name = name, artists = artists, movieName = movieName,
                        id = id, dlPath = path, imageName = imgName
                    )
                )
            }
        }
        return songs
    }

    private fun durationNear(html: String, pos: Int): String {
        val window = html.substring(pos, minOf(html.length, pos + 3000))
        return DURATION.find(window)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun parseAlbumTracksFromHtml(html: String): List<SongResult> {
        val m = ALBUM_TRACKS_JS.find(html) ?: return emptyList()
        return parseAlbumTracksJson(m.groupValues[1])
    }

    /** Accepts either a raw JSON array or {"albumTracks": [...]}. */
    fun parseAlbumTracksJson(json: String): List<SongResult> {
        if (json.isBlank()) return emptyList()
        val arrayText = run {
            val t = json.trim()
            if (t.startsWith("[")) return@run t
            // object form: find albumTracks array
            val inner = Regex(""""albumTracks"\s*:\s*(\[.*\])""", RegexOption.DOT_MATCHES_ALL)
                .find(t)?.groupValues?.get(1)
            inner ?: return emptyList()
        }
        return try {
            MiniJson.parseArrayOfObjects(arrayText).map { o ->
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
            // Regex fallback for malformed JS objects
            parseAlbumTracksRegexFallback(arrayText)
        }
    }

    private fun parseAlbumTracksRegexFallback(arrayText: String): List<SongResult> {
        val objRe = Regex("""\{[^{}]*"name"\s*:[^{}]*\}""")
        return objRe.findAll(arrayText).mapNotNull { m ->
            val o = m.value
            fun f(field: String): String =
                JS_OBJ_FIELD(field).find(o)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } } ?: ""
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

    /** All /downloader/... links keyed by quality label. */
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
        // Fallback: albumTracks dl_path (relative /downloader/... or p128_cdn path)
        val track = parseAlbumTracksFromHtml(html).firstOrNull { it.dlPath.isNotBlank() }
        track?.let {
            val p = it.dlPath
            if (p.startsWith("http")) return p
            if (p.startsWith("/downloader/")) return MasstamilanApi.BASE_URL + p
        }
        return null
    }

    fun extractMovieName(html: String): String {
        val h1 = MOVIE_H1.find(html)?.groupValues?.get(1)?.trim() ?: return ""
        return h1
            .replace("Tamil mp3 songs download.*".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("MassTamilan.*".toRegex(RegexOption.IGNORE_CASE), "")
            .trim(' ', '-', '|')
            .trim()
    }

    fun parseTotalResults(html: String): Int {
        val m = TOTAL_RESULTS.find(html) ?: return -1
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

/**
 * Minimal JSON reader for flat arrays-of-objects (`[{...},{...}]`).
 * Avoids org.json (an Android stub that throws "not mocked" in local JVM
 * unit tests) and keeps zero new dependencies. Handles string escapes
 * (incl. \uXXXX), numbers, booleans and null; nested objects/arrays are
 * skipped. Throws IllegalArgumentException on malformed input.
 */
object MiniJson {

    fun parseArrayOfObjects(text: String): List<Map<String, String>> {
        val p = Parser(text.trim())
        p.ws()
        if (!p.consume('[')) throw IllegalArgumentException("Expected JSON array")
        val out = mutableListOf<Map<String, String>>()
        p.ws()
        if (p.consume(']')) return out
        while (true) {
            p.ws()
            out.add(p.obj())
            p.ws()
            when {
                p.consume(',') -> continue
                p.consume(']') -> break
                else -> throw IllegalArgumentException("Expected ',' or ']' in array")
            }
        }
        return out
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun consume(c: Char): Boolean {
            if (i < s.length && s[i] == c) {
                i++
                return true
            }
            return false
        }

        fun expect(c: Char) {
            if (!consume(c)) throw IllegalArgumentException("Expected '$c' at $i")
        }

        fun obj(): Map<String, String> {
            expect('{')
            val map = LinkedHashMap<String, String>()
            ws()
            if (consume('}')) return map
            while (true) {
                ws()
                val key = str()
                ws()
                expect(':')
                ws()
                map[key] = value()
                ws()
                when {
                    consume(',') -> continue
                    consume('}') -> break
                    else -> throw IllegalArgumentException("Expected ',' or '}' in object")
                }
            }
            return map
        }

        fun value(): String {
            if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (s[i]) {
                '"' -> str()
                '{', '[' -> run {
                    skipNested()
                    ""
                }
                't' -> literal("true", "true")
                'f' -> literal("false", "false")
                'n' -> literal("null", "")
                else -> number()
            }
        }

        fun literal(word: String, result: String): String {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("Bad literal at $i")
            i += word.length
            return result
        }

        fun number(): String {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "-+eE.")) i++
            if (start == i) throw IllegalArgumentException("Bad value at $i")
            return s.substring(start, i)
        }

        fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> break
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("Bad escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("Bad \\u escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        fun skipNested() {
            val open = s[i++]
            val close = if (open == '{') '}' else ']'
            var depth = 1
            var inStr = false
            while (i < s.length && depth > 0) {
                val c = s[i++]
                if (inStr) {
                    if (c == '\\') i++
                    else if (c == '"') inStr = false
                } else {
                    when (c) {
                        '"' -> inStr = true
                        open -> depth++
                        close -> depth--
                    }
                }
            }
            if (depth != 0) throw IllegalArgumentException("Unbalanced $open")
        }
    }
}
