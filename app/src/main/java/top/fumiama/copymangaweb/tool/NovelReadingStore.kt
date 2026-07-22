package top.fumiama.copymangaweb.tool

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NovelReadingRecord(
    val slug: String, val bookName: String, val volumeId: String, val volumeName: String,
    val scrollY: Int, val updatedAt: Long
)

class NovelReadingStore(context: Context) {
    private val file = File(context.filesDir, "novel_reading_list.json")

    @Synchronized
    fun list(): List<NovelReadingRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                NovelReadingRecord(
                    o.getString("slug"), o.optString("bookName"), o.getString("volumeId"),
                    o.optString("volumeName"), o.optInt("scrollY"), o.optLong("updatedAt")
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(record: NovelReadingRecord) {
        val records = list().filterNot { it.slug == record.slug }.toMutableList().apply { add(0, record) }
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("slug", item.slug); put("bookName", item.bookName)
                put("volumeId", item.volumeId); put("volumeName", item.volumeName)
                put("scrollY", item.scrollY); put("updatedAt", item.updatedAt)
            })
        }
        file.writeText(array.toString())
    }

    fun find(slug: String): NovelReadingRecord? = list().firstOrNull { it.slug == slug }
}
