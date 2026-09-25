package com.masstamilan.app.domain.usecase

import javax.inject.Inject

class GetDownloadUrlUseCase @Inject constructor() {
    operator fun invoke(html: String, quality: String): String? {
        val pattern = Regex("""<a class="dlink" href="(/downloader/[^"]+)"\s+rel="nofollow" title="Download [^"]+ $quality"\s+[^>]*>\s*$quality\s*\([^)]+\)""")
        val match = pattern.find(html)
        return match?.groupValues?.get(1)?.let { "https://www.masstamilan.dev$it" }
    }
}
