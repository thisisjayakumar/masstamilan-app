package com.masstamilan.app.data.model

import com.masstamilan.app.core.util.Artwork
import com.masstamilan.app.data.remote.MasstamilanApi

/** One playable entry in the in-memory album queue. */
data class QueueTrack(
    val streamUrl: String? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: String = "",
    val songPagePath: String = "",
    val movieSlug: String = "",
    val songId: Int = 0
)

/**
 * Song-page path usable with [MasstamilanApi.resolveStreamUrl].
 * `/{id}/{song}-mp3-song` qualifies; `/downloader/…` links are already streams.
 */
fun pagePathOf(dlPath: String): String {
    val p = dlPath.trim()
    if (p.matches(Regex("""/\d+/.+""")) && !p.startsWith("/downloader/")) return p.trim('/')
    return ""
}

/** Pure mapper: scraped songs → playable queue. Direct `/downloader/` links need no resolving. */
fun List<SongResult>.toQueue(movieSlug: String = ""): List<QueueTrack> = map { s ->
    val dl = s.dlPath.trim()
    QueueTrack(
        streamUrl = when {
            dl.startsWith("/downloader/") -> MasstamilanApi.BASE_URL + dl
            dl.startsWith("http") -> dl
            else -> null
        },
        title = s.name,
        artist = s.artists,
        artwork = Artwork.url(s.imageName),
        songPagePath = pagePathOf(dl),
        movieSlug = movieSlug.ifBlank { s.movieName },
        songId = s.id
    )
}

/** Wrap-around step used by Next/Previous. Pure + unit-tested. */
fun stepIndex(current: Int, size: Int, delta: Int): Int {
    if (size <= 0) return -1
    return ((current % size) + size + delta) % size
}
