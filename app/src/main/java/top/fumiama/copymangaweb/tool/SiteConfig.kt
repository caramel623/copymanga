package top.fumiama.copymangaweb.tool

import android.content.Context
import android.net.Uri
import java.io.File

object SiteConfig {
    const val DEFAULT_URL = "https://2025copy.com/"

    fun get(context: Context): String {
        val saved = PropertiesTools(File("${context.filesDir}/settings.properties"))["siteUrl"]
        return normalize(saved.takeUnless { it == "null" } ?: DEFAULT_URL) ?: DEFAULT_URL
    }

    fun normalize(value: String): String? {
        val input = value.trim().let { if (it.contains("://")) it else "https://$it" }
        val uri = Uri.parse(input)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return null
        return "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}/"
    }

    fun isAllowed(context: Context, url: String): Boolean {
        val expected = Uri.parse(get(context)).host?.removePrefix("www.") ?: return false
        return Uri.parse(url).host?.removePrefix("www.") == expected
    }
}
