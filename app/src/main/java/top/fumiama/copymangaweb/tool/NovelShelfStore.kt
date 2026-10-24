package top.fumiama.copymangaweb.tool

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NovelShelfRecord(
    val slug: String,
    val name: String,
    val lastUpdated: String,
    val addedAt: Long,
    val cover: String = ""
)

class NovelShelfStore(context: Context) {
    private val file = File(context.filesDir, "novel_shelf.json")

    fun list(): List<NovelShelfRecord> = synchronized(lock) {
        if (!file.isFile) return@synchronized emptyList()
        runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                NovelShelfRecord(
                    item.getString("slug"), item.optString("name"),
                    item.optString("lastUpdated"), item.optLong("addedAt"), item.optString("cover")
                )
            }.sortedByDescending { it.addedAt }
        }.getOrDefault(emptyList())
    }

    fun contains(slug: String): Boolean = synchronized(lock) { list().any { it.slug == slug } }

    fun updateMetadata(slug: String, name: String, updated: String, cover: String) = synchronized(lock) {
        write(list().map { if (it.slug == slug) it.copy(name = name, lastUpdated = updated, cover = cover) else it })
    }

    fun add(record: NovelShelfRecord) = synchronized(lock) {
        val records = list().filterNot { it.slug == record.slug }.toMutableList().apply { add(0, record) }
        write(records)
    }

    fun remove(slug: String) = synchronized(lock) { write(list().filterNot { it.slug == slug }) }

    private fun write(records: List<NovelShelfRecord>) {
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("slug", item.slug); put("name", item.name)
                put("lastUpdated", item.lastUpdated); put("addedAt", item.addedAt)
                put("cover", item.cover)
            })
        }
        file.let { target ->
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(target)) target.writeText(tmp.readText())
        }
    }

    companion object {
        private val lock = Any()
    }
}
