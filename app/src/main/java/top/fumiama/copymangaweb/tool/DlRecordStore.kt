package top.fumiama.copymangaweb.tool

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.LinkedHashMap
import java.util.LinkedHashSet

class DlRecordStore(baseDir: File) {
    private val gson = Gson()
    private val file = File(baseDir, "dl_record.json")
    private val record: MutableMap<String, MutableSet<String>>

    init {
        val type = object : TypeToken<LinkedHashMap<String, LinkedHashSet<String>>>() {}.type
        var loaded: Map<String, Set<String>>? = null
        if (file.exists()) {
            try { loaded = gson.fromJson(file.readText(), type) } catch (e: Exception) { loaded = null }
        }
        record = LinkedHashMap()
        loaded?.forEach { (k, v) -> record[k] = LinkedHashSet(v) }
    }

    fun contains(comic: String, chapter: String): Boolean =
        record[comic]?.contains(chapter) ?: false

    fun add(comic: String, chapter: String) {
        record.getOrPut(comic) { LinkedHashSet() }.add(chapter)
        save()
    }

    fun remove(comic: String, chapter: String) {
        if (record[comic]?.remove(chapter) == true) save()
    }

    private fun save() {
        runCatching { file.writeText(gson.toJson(record), Charsets.UTF_8) }
    }
}
