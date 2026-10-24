package top.fumiama.copymangaweb.tool

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import android.util.Log

data class NovelReadingRecord(
    val slug: String, val bookName: String, val volumeId: String, val volumeName: String,
    val scrollY: Int, val updatedAt: Long
)

class NovelReadingStore(context: Context) {
    private val file = File(context.filesDir, "novel_reading_list.json")

    fun list(): List<NovelReadingRecord> = synchronized(lock) {
        if (!file.isFile) return@synchronized emptyList()
        runCatching {
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

    fun save(record: NovelReadingRecord) = synchronized(lock) {
        val records = list().filterNot { it.slug == record.slug }.toMutableList().apply { add(0, record) }
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("slug", item.slug); put("bookName", item.bookName)
                put("volumeId", item.volumeId); put("volumeName", item.volumeName)
                put("scrollY", item.scrollY); put("updatedAt", item.updatedAt)
            })
        }
        file.let { target ->
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(target)) target.writeText(tmp.readText())
        }
    }

    fun saveAsync(record: NovelReadingRecord) {
        writer.execute {
            runCatching { save(record) }.onFailure { Log.w("NovelReadingStore", "Unable to save reading position", it) }
        }
    }

    fun find(slug: String): NovelReadingRecord? = synchronized(lock) { list().firstOrNull { it.slug == slug } }

    companion object {
        private val lock = Any()
        private val writer = Executors.newSingleThreadExecutor()
    }
}
