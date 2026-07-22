package top.fumiama.copymangaweb.data

import android.content.Context
import org.json.JSONObject
import top.fumiama.copymangaweb.tool.NovelConfig
import java.net.HttpURLConnection
import java.net.URL

data class NovelBook(val name: String, val slug: String, val author: String, val brief: String, val lastUpdated: String)
data class NovelVolume(val id: String, val name: String, val previousId: String?, val nextId: String?)
data class NovelContent(val book: NovelBook, val volume: NovelVolume, val address: String, val encoding: String)

class NovelRepository(private val context: Context) {
    private val base get() = NovelConfig.get(context).trimEnd('/')

    fun loadBook(slug: String): NovelBook {
        val result = getJson("$base/api/v3/book/${encodePath(slug)}").getJSONObject("results")
        val book = result.getJSONObject("book")
        val authors = book.optJSONArray("author")
        return NovelBook(
            book.optString("name", slug),
            book.optString("path_word", slug),
            authors?.optJSONObject(0)?.optString("name").orEmpty(),
            book.optString("brief"),
            book.optString("datetime_updated")
        )
    }

    fun loadVolumes(slug: String): List<NovelVolume> {
        val list = getJson("$base/api/v3/book/${encodePath(slug)}/volumes")
            .getJSONObject("results").getJSONArray("list")
        return (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            NovelVolume(
                item.getString("id"), item.optString("name", "第 ${index + 1} 卷"),
                item.optString("prev").takeIf { it.isNotBlank() && it != "null" },
                item.optString("next").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    fun loadVolume(slug: String, volumeId: String): NovelContent {
        val result = getJson("$base/api/v3/book/${encodePath(slug)}/volume/${encodePath(volumeId)}")
            .getJSONObject("results")
        val bookJson = result.getJSONObject("book")
        val volumeJson = result.getJSONObject("volume")
        return NovelContent(
            NovelBook(bookJson.optString("name", slug), bookJson.optString("path_word", slug), "", "", ""),
            NovelVolume(
                volumeJson.getString("id"), volumeJson.optString("name"),
                volumeJson.optString("prev").takeIf { it.isNotBlank() && it != "null" },
                volumeJson.optString("next").takeIf { it.isNotBlank() && it != "null" }
            ),
            volumeJson.getString("txt_addr"), volumeJson.optString("txt_encoding", "UTF-8")
        )
    }

    fun loadResource(address: String, encoding: String): Pair<String, String> {
        val connection = open(address)
        val contentType = connection.contentType.orEmpty().lowercase()
        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()
        return contentType to bytes.toString(charset(encoding.ifBlank { "UTF-8" }))
    }

    private fun getJson(url: String): JSONObject {
        val connection = open(url)
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val status = connection.responseCode
        connection.disconnect()
        if (status !in 200..299) error("API HTTP $status")
        val json = JSONObject(body)
        if (json.optInt("code", 200) != 200) error(json.optString("message", "API error"))
        return json
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/json,text/plain,text/html,*/*")
        setRequestProperty("User-Agent", "CopyMangaWeb/1.6.1 Android")
    }

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
