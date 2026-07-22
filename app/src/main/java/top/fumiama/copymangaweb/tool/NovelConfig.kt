package top.fumiama.copymangaweb.tool

import android.content.Context
import android.net.Uri
import java.io.File

object NovelConfig {
    const val DEFAULT_API_URL = "https://api.copy3000.com/"

    fun get(context: Context): String {
        val saved = PropertiesTools(File("${context.filesDir}/settings.properties"))["novelApiUrl"]
        return normalize(saved.takeUnless { it == "null" } ?: DEFAULT_API_URL) ?: DEFAULT_API_URL
    }

    fun normalize(value: String): String? {
        val input = value.trim().let { if (it.contains("://")) it else "https://$it" }
        val uri = Uri.parse(input)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return null
        return "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}/"
    }
}
