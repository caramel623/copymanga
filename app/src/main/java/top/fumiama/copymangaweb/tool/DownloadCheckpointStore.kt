package top.fumiama.copymangaweb.tool

import com.google.gson.Gson
import java.io.File

data class DownloadCheckpoint(
    var hash: String = "",
    var comicName: String = "",
    var outputName: String = "",
    var imageUrls: Array<String> = emptyArray(),
    var completed: BooleanArray = BooleanArray(0),
    var compressZip: Boolean = true,
    var treeUri: String? = null,
    var updatedAt: Long = 0L
)

class DownloadCheckpointStore(private val root: File) {
    private val gson = Gson()

    init {
        root.mkdirs()
    }

    private fun safeHash(hash: String): String = hash.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun checkpointFile(hash: String) = File(root, "${safeHash(hash)}.json")

    fun stageDirectory(hash: String) = File(root, "${safeHash(hash)}.images").apply { mkdirs() }

    fun stageImage(hash: String, index: Int) = File(stageDirectory(hash), "%03d.JPG".format(index + 1))

    @Synchronized
    fun load(hash: String): DownloadCheckpoint? {
        val file = checkpointFile(hash)
        if (!file.isFile) return null
        return runCatching {
            gson.fromJson(file.readText(), DownloadCheckpoint::class.java)?.takeIf {
                it.hash == hash && it.imageUrls.isNotEmpty()
            }
        }.getOrNull()
    }

    @Synchronized
    fun save(checkpoint: DownloadCheckpoint) {
        root.mkdirs()
        checkpoint.updatedAt = System.currentTimeMillis()
        val target = checkpointFile(checkpoint.hash)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(gson.toJson(checkpoint))
        if (target.exists() && !target.delete()) error("Cannot replace download checkpoint")
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText())
            temporary.delete()
        }
    }

    @Synchronized
    fun storeUrls(hash: String, comicName: String, urls: Array<String>): DownloadCheckpoint {
        val existing = load(hash)
        val sameUrls = existing?.imageUrls?.contentEquals(urls) == true
        val checkpoint = if (sameUrls) existing!! else {
            stageDirectory(hash).deleteRecursively()
            DownloadCheckpoint(hash = hash, comicName = comicName, imageUrls = urls)
        }
        checkpoint.comicName = comicName
        if (checkpoint.completed.size != urls.size) checkpoint.completed = BooleanArray(urls.size)
        save(checkpoint)
        return checkpoint
    }

    @Synchronized
    fun prepareDownload(
        hash: String,
        comicName: String,
        outputName: String,
        urls: Array<String>,
        compressZip: Boolean,
        treeUri: String?
    ): DownloadCheckpoint {
        val checkpoint = storeUrls(hash, comicName, urls)
        checkpoint.outputName = outputName
        checkpoint.compressZip = compressZip
        checkpoint.treeUri = treeUri
        save(checkpoint)
        return checkpoint
    }

    @Synchronized
    fun markCompleted(checkpoint: DownloadCheckpoint, index: Int) {
        if (index !in checkpoint.completed.indices) return
        checkpoint.completed[index] = true
        save(checkpoint)
    }

    @Synchronized
    fun pendingForComic(comicName: String): List<DownloadCheckpoint> =
        root.listFiles { file -> file.isFile && file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { gson.fromJson(file.readText(), DownloadCheckpoint::class.java) }.getOrNull()
            }
            ?.filter { it.comicName == comicName && it.imageUrls.isNotEmpty() }
            ?.sortedBy { it.updatedAt }
            ?: emptyList()

    @Synchronized
    fun clear(hash: String) {
        checkpointFile(hash).delete()
        File(root, "${safeHash(hash)}.json.tmp").delete()
        File(root, "${safeHash(hash)}.images").deleteRecursively()
    }
}
