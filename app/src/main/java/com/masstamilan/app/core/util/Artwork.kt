package com.masstamilan.app.core.util

import com.masstamilan.app.data.remote.MasstamilanApi

/**
 * Album art comes from the site as an extension-less image name
 * ("jailer-2-tamil-2026"). `/i/<name>` 404s; only `/i/<name>.jpg` serves.
 */
object Artwork {
    private val EXT = Regex("""\.[a-z0-9]{2,5}$""", RegexOption.IGNORE_CASE)

    fun url(imageName: String?): String {
        val name = imageName?.trim().orEmpty()
        if (name.isBlank()) return ""
        if (name.startsWith("http")) return name
        val file = if (EXT.containsMatchIn(name)) name else "$name.jpg"
        val path = if (file.startsWith("/")) file else "/i/$file"
        return MasstamilanApi.BASE_URL + path
    }
}
