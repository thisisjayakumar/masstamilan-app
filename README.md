# MasstamilanApp

A Spotify-like Android app for Tamil music. Catalog and playback come from
`masstamilan.dev` via HTML scraping — no WebView, no wrapped browser.
Clean song-level search, instant play, and downloads.

Reference architecture: [thisisjayakumar/linqmusic](https://github.com/thisisjayakumar/linqmusic)
(Kotlin, Compose Material3, Media3, Room, Hilt — minimal external dependencies).

## Features

- **Unified song search** — type 2+ letters, get ranked song results (not movie pages).
  Autocomplete suggestions at 150 ms, full search at 450 ms debounce.
- **Fuzzy ranked results** — exact, prefix, substring, token and typo-tolerant
  (Levenshtein) matching; song titles outrank artist/movie matches.
- **Instant play** — tap a result, the stream URL (`/downloader/...`, 320 kbps
  preferred, 128 kbps fallback) resolves and ExoPlayer starts immediately.
- **Movie pages** — full track listing per movie with artwork and durations.
- **Downloads** — MP3 download tracking backed by Room.
- **Offline-first UI** — loading, empty, and error-with-retry states on search.

## How it works

`masstamilan.dev` is a Rails SSR site with no public API, so all data is scraped:

| Source | Route | Use |
|---|---|---|
| Autocomplete JSON `[{n,s,l}]` | `/search/ac?keyword=` | Instant suggestions |
| Movie-level HTML (paginated) | `/search?keyword=` | Candidate movies |
| Movie page track table + `window.albumTracks` JS | `/{movie}-songs` | Song-level fan-out |
| Song page `dlink` anchors | `/{id}/{song}-mp3-song` | 128/320 kbps stream URLs |

Search flow: autocomplete for suggestions → movie search → parallel fan-out over
the top 5 movie pages → dedupe → fuzzy rank (`core/util/StringMatcher`).
All parsing lives in pure functions (`MasstamilanParsers`, `MiniJson`) with no
Android dependencies, so it is fully unit-tested.

## Project structure

```
app/src/main/java/com/masstamilan/app/
├── data/
│   ├── model/        SongResult, RankedSong, AutocompleteSuggestion, DownloadEntity
│   ├── remote/       MasstamilanApi (suspend I/O) + MasstamilanParsers + MiniJson
│   ├── repository/   MasstamilanRepository (unifiedSongSearch fan-out)
│   ├── dao|database/ Room (downloads)
├── domain/
│   ├── model/        SongDomain
│   └── usecase/      SearchSongsUseCase, GetMovieSongsUseCase, GetDownloadUrlUseCase
├── core/
│   ├── di/           Hilt AppModule
│   ├── media/        PlaybackManager (ExoPlayer queue + instant play)
│   ├── network/      NetworkHelper
│   └── util/         StringMatcher (fuzzy rank), DownloadHelper, Logger
├── feature/
│   ├── home|search|songdetail|player|downloads
├── service/          MusicPlaybackService + notification receiver
└── ui/               MainActivity (NavHost) + theme
```

Unit tests live in `app/src/test/`:

- `MasstamilanParsersTest` — HTML/JSON parsing, download-link preference, MiniJson edge cases
- `StringMatcherTest` — search-quality regression (ordering, prefix, typo tolerance)
- `PlaybackUrlTest` — playable-URL rules, 320-over-128 stream selection

## Tech stack

Kotlin 1.9.20 · Compose BOM 2024.01.00 (Material3) · Media3 1.2.1 · Room 2.6.1 ·
Hilt 2.50 · OkHttp 4.12.0 · Coil 2.5.0 · Coroutines 1.7.3 · DataStore · WorkManager ·
Timber · Gradle 8.2 · AGP 8.2.0 · minSdk 28, target/compile 34, Java 17.

## Building (CI only)

There is intentionally **no local build setup** — release APKs are produced by
GitHub Actions (`.github/workflows/android-release.yml`):

| Trigger | Result |
|---|---|
| Push to `main`, PR, manual dispatch | Unit tests + release APK uploaded as run artifact |
| Tag `v*` (e.g. `v1.0.0`) | Same, plus a GitHub Release with the APK attached |

First run:

```bash
git init && git add -A && git commit -m "Initial commit"
git remote add origin git@github.com:<you>/MasstamilanApp.git
git push -u origin main
# APK: Actions tab -> latest run -> Artifacts -> masstamilan-release-apk
# Release build: git tag v1.0.0 && git push origin v1.0.0
```

### Signed production APK (optional, recommended)

Without secrets CI uploads `app-release-unsigned.apk` (not installable).
For an installable APK, add these repository secrets (Settings → Secrets → Actions):

| Secret | Value |
|---|---|
| `MASSTAMILAN_KEYSTORE_BASE64` | `base64 -w0 release.keystore` of your keystore |
| `MASSTAMILAN_KEYSTORE_PASSWORD` | keystore password |
| `MASSTAMILAN_KEY_ALIAS` | key alias |
| `MASSTAMILAN_KEY_PASSWORD` | key password |

Generate a keystore once, keep it out of git (see `.gitignore`):

```bash
keytool -genkeypair -keystore release.keystore -alias masstamilan \
  -keyalg RSA -keysize 2048 -validity 10000
```

## Local development (optional)

CI is the supported path, but the project builds locally with JDK 17, the
Android SDK (API 34, build-tools 34.0.0), and the pinned wrapper:

```bash
# Unit tests
./gradlew testDebugUnitTest

# Debug + release APKs
./gradlew assembleDebug assembleRelease
# app/build/outputs/apk/release/app-release[-unsigned].apk
```

## Disclaimer

Built for educational purposes. Song catalog, artwork, and audio belong to
their respective owners — respect `masstamilan.dev`'s terms and the rights of
artists and labels. Download links expire (~1 day); the app re-resolves them
at play time.
