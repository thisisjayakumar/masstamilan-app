package com.masstamilan.app.data.model

data class SongResult(
    val name: String = "",
    val artists: String = "",
    val movieName: String = "",
    val duration: String = "",
    val id: Int = 0,
    val dlPath: String = "",
    val imageName: String = "",
    val downloads: Int = 0
)

data class MoviePage(
    val movieName: String = "",
    val songs: List<SongResult> = emptyList(),
    val totalTracks: Int = 0
)

data class SearchResult(
    val name: String = "",
    val slug: String = "",
    val image: String = ""
)

data class AlbumTrack(
    val id: Int = 0,
    val name: String = "",
    val artists: String = "",
    val mName: String = "",
    val imgName: String = "",
    val dlPath: String = ""
)

/** /search/ac?keyword= entry: {n: name, s: slug, l: link} */
data class AutocompleteSuggestion(
    val name: String = "",
    val slug: String = "",
    val link: String = ""
)

/** Song + fuzzy relevance score (higher = better). */
data class RankedSong(
    val song: SongResult,
    val score: Double = 0.0,
    val movieSlug: String = ""
)
