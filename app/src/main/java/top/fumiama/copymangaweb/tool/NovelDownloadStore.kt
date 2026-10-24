package top.fumiama.copymangaweb.tool

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NovelDownloadRecord(
    val slug: String,
    val name: String,
    val author: String = "",
    val sizeBytes: Long,
    val downloadedAt: Long,
    val volumeIds: List<String> = emptyList(),
    val volumeNames: List<String> = emptyList(),
    val traditional: Boolean = false
)

class NovelDownloadStore(private val indexFile: File, private val novelsDir: File) {
    constructor(context: Context) : this(
        File(context.filesDir, "novel_downloads.json"),
        context.getExternalFilesDir("novels") ?: File(context.filesDir, "novels")
    )

    fun sanitizedSlug(slug: String): String = slug.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun epubFile(slug: String): File = File(novelsDir, sanitizedSlug(slug) + ".epub")

    @Synchronized
    fun list(): List<NovelDownloadRecord> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                NovelDownloadRecord(
                    item.getString("slug"), item.optString("name"), item.optString("author"),
                    item.optLong("sizeBytes"), item.optLong("downloadedAt"),
                    item.optJSONArray("volumeIds").toStringList(),
                    item.optJSONArray("volumeNames").toStringList(),
                    item.optBoolean("traditional")
                )
            }.sortedByDescending { it.downloadedAt }
        }.getOrDefault(emptyList())
    }

    fun recordFor(slug: String): NovelDownloadRecord? = list().firstOrNull { it.slug == slug }

    fun fileFor(slug: String): File? = recordFor(slug)?.let(::fileFor)

    @Synchronized
    fun fileFor(record: NovelDownloadRecord): File? {
        val file = epubFile(record.slug)
        if (file.isFile) return file
        removeRecord(record.slug)
        return null
    }

    @Synchronized
    fun markDownloaded(record: NovelDownloadRecord) =
        write(list().filterNot { it.slug == record.slug } + record)

    @Synchronized
    fun removeDownload(slug: String) {
        epubFile(slug).delete()
        removeRecord(slug)
    }

    @Synchronized
    private fun removeRecord(slug: String) = write(list().filterNot { it.slug == slug })

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    @Synchronized
    private fun write(records: List<NovelDownloadRecord>) {
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("slug", item.slug); put("name", item.name); put("author", item.author)
                put("sizeBytes", item.sizeBytes); put("downloadedAt", item.downloadedAt)
                put("volumeIds", JSONArray(item.volumeIds))
                put("volumeNames", JSONArray(item.volumeNames))
                put("traditional", item.traditional)
            })
        }
        val tmp = File(indexFile.parentFile, "${indexFile.name}.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
