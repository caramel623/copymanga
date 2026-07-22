package top.fumiama.copymangaweb.tool

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NovelShelfRecord(
    val slug: String,
    val name: String,
    val lastUpdated: String,
    val addedAt: Long
)

class NovelShelfStore(context: Context) {
    private val file = File(context.filesDir, "novel_shelf.json")

    @Synchronized
    fun list(): List<NovelShelfRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                NovelShelfRecord(
                    item.getString("slug"), item.optString("name"),
                    item.optString("lastUpdated"), item.optLong("addedAt")
                )
            }.sortedByDescending { it.addedAt }
        }.getOrDefault(emptyList())
    }

    fun contains(slug: String): Boolean = list().any { it.slug == slug }

    @Synchronized
    fun add(record: NovelShelfRecord) {
        val records = list().filterNot { it.slug == record.slug }.toMutableList().apply { add(0, record) }
        write(records)
    }

    @Synchronized
    fun remove(slug: String) = write(list().filterNot { it.slug == slug })

    private fun write(records: List<NovelShelfRecord>) {
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("slug", item.slug); put("name", item.name)
                put("lastUpdated", item.lastUpdated); put("addedAt", item.addedAt)
            })
        }
        file.writeText(array.toString())
    }
}
