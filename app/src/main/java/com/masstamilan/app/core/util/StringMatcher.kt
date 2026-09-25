package com.masstamilan.app.core.util

import com.masstamilan.app.data.model.SongResult
import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy matching + ranking for Spotify-like song search.
 * Pure Kotlin, no Android deps — unit-tested.
 *
 * Scoring (0.0..~1.5):
 * - exact prefix match on song name: +1.0
 * - exact word-prefix / token match: +0.8
 * - substring: +0.5 (earlier position scores higher)
 * - token overlap (query words found in name/artists/movie): +0.15 each
 * - Levenshtein similarity bonus: +0.0..0.4 (only when similarity > 0.55)
 * - artist/movie matches add smaller bonuses so song-title hits rank first
 */
object StringMatcher {

    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9\\u0B80-\\u0BFF ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun tokens(s: String): List<String> =
        normalize(s).split(" ").filter { it.length >= 2 }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = min(
                    min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    fun similarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        val dist = levenshtein(na, nb)
        return 1.0 - dist.toDouble() / max(na.length, nb.length)
    }

    /** Score one song against a raw query. Returns 0.0 when irrelevant. */
    fun scoreSong(query: String, song: SongResult): Double {
        val q = normalize(query)
        if (q.isBlank()) return 0.0
        val name = normalize(song.name)
        val artists = normalize(song.artists)
        val movie = normalize(song.movieName)
        if (name.isBlank()) return 0.0

        var score = 0.0

        // 1. Song-name signals (strongest)
        if (name == q) score += 1.2
        else if (name.startsWith(q)) score += 1.0
        else if (name.contains(q)) {
            score += 0.5 + 0.2 * (1.0 - name.indexOf(q).toDouble() / name.length)
        } else {
            // word-prefix: "mun" matches "Munbe Vaa"
            val words = name.split(" ")
            if (words.any { it.startsWith(q) }) score += 0.8
            else if (q.split(" ").all { qw -> words.any { it.startsWith(qw) } }) score += 0.7
        }

        // 2. Token overlap
        val qToks = q.split(" ").filter { it.length >= 2 }
        val nameToks = name.split(" ").toSet()
        val matched = qToks.count { it in nameToks }
        score += 0.15 * matched
        // partial token prefix
        val partial = qToks.count { qt -> qt !in nameToks && nameToks.any { it.startsWith(qt) } }
        score += 0.10 * partial

        // 3. Levenshtein bonus (typo tolerance: "ram" vs "raam", "kanne" vs "kanne")
        if (score < 0.5) {
            val sim = similarity(q, name)
            if (sim > 0.55) score += (sim - 0.55) * 0.9 // max ~+0.4
            else {
                // token-level fuzzy: best query-token vs name-token similarity
                val best = qToks.maxOfOrNull { qt ->
                    nameToks.maxOfOrNull { similarity(qt, it) } ?: 0.0
                } ?: 0.0
                if (best > 0.72) score += (best - 0.72) * 1.0
            }
        }

        // 4. Artist / movie signals (weaker — song title wins ties)
        if (artists.isNotBlank()) {
            if (artists == q) score += 0.5
            else if (artists.startsWith(q)) score += 0.4
            else if (artists.contains(q)) score += 0.25
        }
        if (movie.isNotBlank()) {
            if (movie == q) score += 0.45
            else if (movie.startsWith(q)) score += 0.35
            else if (movie.contains(q)) score += 0.2
        }

        return score
    }

    /** Rank songs for a query; drops zero-score items. Stable sort (score desc). */
    fun rankSongs(query: String, songs: List<SongResult>): List<Pair<SongResult, Double>> {
        if (query.isBlank()) return emptyList()
        return songs.mapNotNull { s ->
            val sc = scoreSong(query, s)
            if (sc > 0.02) s to sc else null
        }.sortedByDescending { it.second }
    }
}
