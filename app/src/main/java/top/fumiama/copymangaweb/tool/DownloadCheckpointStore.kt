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

    fun stageDirectory(hash: String) = synchronized(lock) {
        File(root, "${safeHash(hash)}.images").apply { mkdirs() }
    }

    fun stageImage(hash: String, index: Int) = synchronized(lock) {
        File(stageDirectory(hash), "%03d.JPG".format(index + 1))
    }

    fun load(hash: String): DownloadCheckpoint? = synchronized(lock) {
        val file = checkpointFile(hash)
        if (!file.isFile) return@synchronized null
        runCatching {
            gson.fromJson(file.readText(), DownloadCheckpoint::class.java)?.takeIf {
                it.hash == hash && it.imageUrls.isNotEmpty()
            }
        }.getOrNull()
    }

    fun save(checkpoint: DownloadCheckpoint) = synchronized(lock) {
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

    fun storeUrls(hash: String, comicName: String, urls: Array<String>): DownloadCheckpoint = synchronized(lock) {
        val existing = load(hash)
        val sameUrls = existing?.imageUrls?.contentEquals(urls) == true
        val checkpoint = if (sameUrls) existing!! else {
            stageDirectory(hash).deleteRecursively()
            DownloadCheckpoint(hash = hash, comicName = comicName, imageUrls = urls)
        }
        checkpoint.comicName = comicName
        if (checkpoint.completed.size != urls.size) checkpoint.completed = BooleanArray(urls.size)
        save(checkpoint)
        checkpoint
    }

    fun prepareDownload(
        hash: String,
        comicName: String,
        outputName: String,
        urls: Array<String>,
        compressZip: Boolean,
        treeUri: String?
    ): DownloadCheckpoint = synchronized(lock) {
        val checkpoint = storeUrls(hash, comicName, urls)
        checkpoint.outputName = outputName
        checkpoint.compressZip = compressZip
        checkpoint.treeUri = treeUri
        save(checkpoint)
        checkpoint
    }

    fun markCompleted(checkpoint: DownloadCheckpoint, index: Int) = synchronized(lock) {
        if (index !in checkpoint.completed.indices) return@synchronized
        checkpoint.completed[index] = true
        save(checkpoint)
    }

    fun sweepInvalid(): Int = synchronized(lock) {
        var removed = 0
        val validHashes = HashSet<String>()
        for (file in root.listFiles().orEmpty()) {
            when {
                file.isFile && file.extension == "json" -> {
                    val checkpoint = runCatching {
                        gson.fromJson(file.readText(), DownloadCheckpoint::class.java)
                    }.getOrNull()
                    val valid = checkpoint != null &&
                        checkpoint.hash.isNotBlank() &&
                        checkpoint.comicName.isNotBlank() &&
                        checkpoint.imageUrls.isNotEmpty() &&
                        file.name == "${safeHash(checkpoint.hash)}.json"
                    if (valid) validHashes.add(safeHash(checkpoint!!.hash)) else if (file.delete()) removed++
                }
                file.isFile && file.name.endsWith(".json.tmp") -> if (file.delete()) removed++
            }
        }
        for (file in root.listFiles().orEmpty()) {
            if (file.isDirectory && file.name.endsWith(".images") &&
                file.name.removeSuffix(".images") !in validHashes && file.deleteRecursively()) removed++
        }
        removed
    }

    fun pendingForComic(comicName: String): List<DownloadCheckpoint> = synchronized(lock) {
        root.listFiles { file -> file.isFile && file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    gson.fromJson(file.readText(), DownloadCheckpoint::class.java)
                        ?.takeIf { it.comicName == comicName && it.imageUrls.isNotEmpty() }
                }.getOrNull()
            }
            ?.sortedBy { it.updatedAt }
            ?: emptyList()
    }

    fun clear(hash: String) = synchronized(lock) {
        checkpointFile(hash).delete()
        File(root, "${safeHash(hash)}.json.tmp").delete()
        File(root, "${safeHash(hash)}.images").deleteRecursively()
    }

    companion object {
        // One store instance is created per DlActivity, so lock across instances.
        private val lock = Any()
    }
}
