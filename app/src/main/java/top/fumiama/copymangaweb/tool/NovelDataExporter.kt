package top.fumiama.copymangaweb.tool

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NovelDataExporter {
    data class ImportData(
        val shelf: List<NovelShelfRecord>,
        val readingHistory: List<NovelReadingRecord>
    )

    fun buildJson(
        shelf: List<NovelShelfRecord>,
        readingHistory: List<NovelReadingRecord>,
        exportedAt: Long = System.currentTimeMillis()
    ): String = JSONObject().apply {
        put("format", "copymanga-novel-data")
        put("version", 1)
        put("exportedAt", isoTime(exportedAt))
        put("novelShelf", JSONArray().apply {
            shelf.forEach { item ->
                put(JSONObject().apply {
                    put("slug", item.slug)
                    put("name", item.name)
                    put("lastUpdated", item.lastUpdated)
                    put("addedAt", item.addedAt)
                })
            }
        })
        put("readingHistory", JSONArray().apply {
            readingHistory.forEach { item ->
                put(JSONObject().apply {
                    put("slug", item.slug)
                    put("bookName", item.bookName)
                    put("volumeId", item.volumeId)
                    put("volumeName", item.volumeName)
                    put("scrollY", item.scrollY)
                    put("updatedAt", item.updatedAt)
                })
            }
        })
    }.toString(2)

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "copymanga_novel_data_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))}.json"

    fun parseJson(json: String): ImportData {
        val root = JSONObject(json)
        require(root.optString("format") == "copymanga-novel-data") { "不是 CopyManga 小說資料檔" }
        require(root.optInt("version") == 1) { "不支援此匯出檔版本" }

        val shelfArray = root.getJSONArray("novelShelf")
        val shelf = (0 until shelfArray.length()).map { index ->
            val item = shelfArray.getJSONObject(index)
            NovelShelfRecord(
                slug = item.getString("slug"),
                name = item.optString("name"),
                lastUpdated = item.optString("lastUpdated"),
                addedAt = item.optLong("addedAt")
            )
        }
        val historyArray = root.getJSONArray("readingHistory")
        val history = (0 until historyArray.length()).map { index ->
            val item = historyArray.getJSONObject(index)
            NovelReadingRecord(
                slug = item.getString("slug"),
                bookName = item.optString("bookName"),
                volumeId = item.getString("volumeId"),
                volumeName = item.optString("volumeName"),
                scrollY = item.optInt("scrollY"),
                updatedAt = item.optLong("updatedAt")
            )
        }
        return ImportData(shelf, history)
    }

    private fun isoTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(time))
}
