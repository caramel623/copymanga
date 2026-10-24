package top.fumiama.copymangaweb.tool

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import top.fumiama.copymangaweb.data.NovelRepository
import java.io.File

object NovelChapterCodec {
    const val PLAIN_MARKER = "<!--TXT-->"

    private val bodyRegex = Regex("<body\\b[^>]*>(.*)</body>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val paragraphRegex = Regex("<p>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL))

    fun toStored(contentType: String, raw: String): String {
        val trimmed = raw.trimStart()
        val isHtml = contentType.contains("html") || trimmed.startsWith("<!doctype", true) || trimmed.startsWith("<html", true)
        if (isHtml) return bodyRegex.find(raw)?.groupValues?.get(1) ?: raw
        return PLAIN_MARKER + raw.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .joinToString("") { "<p>${escape(it)}</p>" }
    }

    fun isPlain(stored: String) = stored.startsWith(PLAIN_MARKER)

    fun toPlainText(stored: String): String =
        paragraphRegex.findAll(stored.removePrefix(PLAIN_MARKER))
            .joinToString("\n\n") { unescape(it.groupValues[1]) }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun unescape(value: String) = value.replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}

class NovelEpubDownloader(private val context: Context) {
    suspend fun download(slug: String, onProgress: (done: Int, total: Int) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(build(slug, onProgress))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private suspend fun build(slug: String, onProgress: (Int, Int) -> Unit): File {
        val repository = NovelRepository(context)
        val store = NovelDownloadStore(context)
        val book = repository.loadBook(slug)
        val volumes = repository.loadVolumes(slug)
        require(volumes.isNotEmpty()) { "目前沒有可下載的卷冊" }
        val partsDir = File(context.filesDir, "novel_parts/${store.sanitizedSlug(slug)}")
        partsDir.mkdirs()
        val traditional = NovelTraditional.enabled(PropertiesTools(File("${context.filesDir}/settings.properties")))
        val builder = EpubBuilder(store.epubFile(slug), "copymanga:$slug", book.name, book.author)
        return try {
            volumes.forEachIndexed { index, volume ->
                currentCoroutineContext().ensureActive()
                val href = "ch${(index + 1).toString().padStart(4, '0')}.xhtml"
                val partFile = File(partsDir, "$href.txt")
                val stored = if (partFile.isFile) partFile.readText() else {
                    val metadata = repository.loadVolume(slug, volume.id)
                    val (contentType, fetched) = repository.loadResource(metadata.address, metadata.encoding)
                    val body = if (traditional) NovelTraditional.convert(fetched) else fetched
                    NovelChapterCodec.toStored(contentType, body).also {
                        val tmp = File(partFile.parentFile, "${partFile.name}.tmp")
                        tmp.writeText(it)
                        if (!tmp.renameTo(partFile)) {
                            partFile.writeText(it)
                            tmp.delete()
                        }
                    }
                }
                builder.addChapter(href, volume.name, stored)
                onProgress(index + 1, volumes.size)
            }
            val file = builder.finish()
            store.markDownloaded(
                NovelDownloadRecord(
                    slug, book.name, book.author, file.length(), System.currentTimeMillis(),
                    volumes.map { it.id }, volumes.map { it.name }, traditional
                )
            )
            partsDir.deleteRecursively()
            file
        } catch (t: Throwable) {
            builder.abort()
            throw t
        }
    }
}
