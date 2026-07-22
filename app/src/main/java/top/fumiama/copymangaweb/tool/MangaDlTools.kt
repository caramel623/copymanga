package top.fumiama.copymangaweb.tool

import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.activity.DlActivity
import java.io.File
import java.lang.Thread.sleep
import java.lang.ref.WeakReference
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStream

class MangaDlTools(activity: DlActivity) {
    var exit = false
    private val sem = Semaphore(1)
    private val da = WeakReference(activity)
    private val d get() = da.get()
    private val p = PropertiesTools(File("${d?.filesDir}/chapters.hash"))
    private val checkpointStore = DownloadCheckpointStore(File(activity.filesDir, "download-checkpoints"))
    private var imgUrlsList: Array<Array<String>?>? = null
    private var chaptersCount = 0

    init {
        wmdlt = WeakReference(this)
    }

    fun getImgsCountByHash(hash: String): Int?{
        return p[hash].toIntOrNull()?.let { imgUrlsList?.getOrNull(it)?.size }
            ?: checkpointStore.load(hash)?.imageUrls?.size
    }

    fun pendingHashes(comicName: String): Set<String> =
        checkpointStore.pendingForComic(comicName).map { it.hash }.toSet()

    fun allocateChapterUrls(count: Int){
        imgUrlsList = arrayOfNulls(count)
        chaptersCount = 0
    }

    fun dlChapterUrl(url: String): Boolean {
        val acquired = try { sem.tryAcquire(30, TimeUnit.SECONDS) } catch (_: InterruptedException) { false }
        if (!acquired) {
            val index = chaptersCount++
            p[url.substringAfterLast("/")] = index.toString()
            imgUrlsList?.set(index, emptyArray())
            return false
        }
        da.get()?.apply {
            p[url.substringAfterLast("/")] = (chaptersCount++).toString()
            runOnUiThread { mBinding.dwh.apply { post { loadUrl(url) } } }
        }
        return da.get() != null
    }

    fun waitChapterUrlsReady(): Boolean {
        return try {
            if (sem.tryAcquire(5, TimeUnit.MINUTES)) {
                sem.release()
                true
            } else false
        } catch (_: InterruptedException) { false }
    }

    fun setChapterImages(hash: String, imgUrls: Array<String>){
        val index = p[hash].toIntOrNull()
        val chapters = imgUrlsList
        if (index == null || chapters == null || index !in chapters.indices) {
            Log.e("Mydl", "Cannot store chapter URLs: hash=$hash, index=$index")
            sem.release()
            return
        }
        val validUrls = imgUrls.filter { it.startsWith("http://") || it.startsWith("https://") }.toTypedArray()
        chapters[index] = validUrls
        if (validUrls.isNotEmpty()) checkpointStore.storeUrls(hash, DlActivity.comicName, validUrls)
        Log.d("Mydl", "Stored ${validUrls.size} image URLs for chapter $hash at index $index")
        sem.release()
    }

    fun updateScanProgress(index: String, count: String) {
        val current = index.filter { it.isDigit() }.toIntOrNull() ?: 0
        val total = count.toIntOrNull() ?: 0
        d?.runOnUiThread {
            d?.mBinding?.dldlbar?.tdwn?.text = "$index/$count"
            if (total > 0) d?.mBinding?.dldlbar?.pdwn?.progress = current * 100 / total
        }
    }

    fun dlChapterAndPackIntoZip(zipf: File, hash: String){
        val chapterIndex = p[hash].toIntOrNull()
        val storedCheckpoint = checkpointStore.load(hash)
        val chapterImages = chapterIndex?.let { imgUrlsList?.getOrNull(it) }
            ?: storedCheckpoint?.imageUrls
        if (chapterImages == null) {
            Log.e("Mydl", "Chapter image URLs are missing: hash=$hash, index=$chapterIndex")
            onDownloadedListener?.handleMessage(false)
            return
        }
        Log.d("Mydl", "Start downloading ${chapterImages.size} images for chapter $hash")
        d?.runOnUiThread { d?.updateProgressBar(0, chapterImages.size) }
        chapterImages.let { images ->
            if (images.isEmpty()) {
                onDownloadedListener?.handleMessage(false)
                return@let
            }
            val dl = DownloadTools()
            val activity = d ?: return@let
            val cookie = CookieManager.getInstance().getCookie(SiteConfig.get(activity))
            val settings = PropertiesTools(File("${activity.filesDir}/settings.properties"))
            val compressZip = settings["compressZip"] != "false"
            val safeName = zipf.nameWithoutExtension.replace(Regex("[\\/:*?\"<>|]"), "_")
            val treeUri = settings["downloadTreeUri"].takeUnless { it == "null" }
            val checkpoint = checkpointStore.prepareDownload(
                hash, DlActivity.comicName, safeName, images, compressZip, treeUri
            )
            for (i in images.indices) {
                val stagedImage = checkpointStore.stageImage(hash, i)
                val alreadyCompleted = checkpoint.completed.getOrNull(i) == true &&
                    stagedImage.isFile && stagedImage.length() > 0
                var s = alreadyCompleted
                var attempts = 0
                while (!s && attempts++ < 3 && !exit){
                    s = try {
                        val candidates = linkedSetOf(
                            images[i],
                            activity.toolsBox.resolution.wrap(images[i])
                        )
                        candidates.any { u ->
                            dl.getHttpContent(u, SiteConfig.get(activity), activity.getString(R.string.pc_ua), cookie)?.let { data ->
                                val part = File(stagedImage.parentFile, "${stagedImage.name}.part")
                                part.writeBytes(data)
                                if (stagedImage.exists()) stagedImage.delete()
                                if (!part.renameTo(stagedImage)) {
                                    part.copyTo(stagedImage, overwrite = true)
                                    part.delete()
                                }
                                stagedImage.isFile && stagedImage.length() == data.size.toLong()
                            } ?: false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                    if (!s) {
                        onDownloadedListener?.handleMessage(i + 1)
                        sleep(2000)
                    }
                }
                if (s && checkpoint.completed.getOrNull(i) != true) checkpointStore.markCompleted(checkpoint, i)
                onDownloadedListener?.handleMessage(s, i + 1)
                if (exit) break
                if (s && !alreadyCompleted && i < images.lastIndex) {
                    val delay = when {
                        images.size <= 25 -> 0L
                        images.size <= 50 -> 500L
                        images.size >= 100 && (i + 1) % 50 == 0 -> Random.nextLong(3_000L, 18_001L)
                        else -> listOf(500L, 1_000L).random()
                    }
                    var remaining = delay
                    while (remaining > 0 && !exit) {
                        if (images.size > 50 && delay > 0) {
                            d?.runOnUiThread { d?.mBinding?.dldlbar?.textView?.text = "防護等待：${((remaining + 999) / 1000)} 秒" }
                        }
                        val chunk = minOf(250L, remaining)
                        sleep(chunk)
                        remaining -= chunk
                    }
                }
            }
            if (exit) {
                Log.d("Mydl", "Download paused and checkpoint retained: $hash")
                return@let
            }
            if (!checkpoint.completed.all { it }) {
                onDownloadedListener?.handleMessage(false)
                return@let
            }
            val finalized = finalizeDownload(activity, zipf, checkpoint)
            if (finalized) checkpointStore.clear(hash)
            onDownloadedListener?.handleMessage(finalized)
        }
    }

    private fun finalizeDownload(
        activity: DlActivity,
        zipf: File,
        checkpoint: DownloadCheckpoint
    ): Boolean = runCatching {
        val images = checkpoint.imageUrls.indices.map { checkpointStore.stageImage(checkpoint.hash, it) }
        if (images.any { !it.isFile || it.length() <= 0 }) return false
        val rootDocument = checkpoint.treeUri?.let { DocumentFile.fromTreeUri(activity, Uri.parse(it)) }
        if (checkpoint.treeUri != null && (rootDocument == null || !rootDocument.canWrite())) return false
        if (checkpoint.compressZip) {
            if (rootDocument == null) finalizeLocalZip(zipf, images)
            else finalizeDocumentZip(activity, rootDocument, checkpoint.outputName, checkpoint.hash, images)
        } else {
            if (rootDocument == null) finalizeLocalFolder(zipf, checkpoint.outputName, images)
            else finalizeDocumentFolder(activity, rootDocument, checkpoint.outputName, images)
        }
    }.getOrElse {
        Log.e("Mydl", "Cannot finalize checkpoint ${checkpoint.hash}", it)
        false
    }

    private fun writeZip(output: OutputStream, images: List<File>) {
        ZipOutputStream(CheckedOutputStream(output, CRC32())).use { zip ->
            zip.setLevel(9)
            images.forEachIndexed { index, image ->
                zip.putNextEntry(ZipEntry("%03d.JPG".format(index + 1)))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun finalizeLocalZip(zipf: File, images: List<File>): Boolean {
        zipf.parentFile?.mkdirs()
        val part = File(zipf.parentFile, "${zipf.name}.part")
        if (part.exists()) part.delete()
        part.outputStream().use { writeZip(it, images) }
        if (zipf.exists() && !zipf.delete()) return false
        return part.renameTo(zipf)
    }

    private fun finalizeDocumentZip(
        activity: DlActivity,
        root: DocumentFile,
        safeName: String,
        hash: String,
        images: List<File>
    ): Boolean {
        val internalPart = File(checkpointStore.stageDirectory(hash), "$safeName.zip.part")
        if (internalPart.exists()) internalPart.delete()
        internalPart.outputStream().use { writeZip(it, images) }
        root.findFile("$safeName.zip.part")?.delete()
        val documentPart = root.createFile("application/octet-stream", "$safeName.zip.part") ?: return false
        val output = activity.contentResolver.openOutputStream(documentPart.uri) ?: return false
        output.use { out -> internalPart.inputStream().use { it.copyTo(out) } }
        root.findFile("$safeName.zip")?.delete()
        return documentPart.renameTo("$safeName.zip")
    }

    private fun finalizeLocalFolder(zipf: File, safeName: String, images: List<File>): Boolean {
        val folder = File(zipf.parentFile, safeName).apply { mkdirs() }
        images.forEachIndexed { index, image ->
            val final = File(folder, "%03d.JPG".format(index + 1))
            val part = File(folder, "${final.name}.part")
            image.copyTo(part, overwrite = true)
            if (final.exists()) final.delete()
            if (!part.renameTo(final)) return false
        }
        return true
    }

    private fun finalizeDocumentFolder(
        activity: DlActivity,
        root: DocumentFile,
        safeName: String,
        images: List<File>
    ): Boolean {
        val folder = root.findFile(safeName)?.takeIf { it.isDirectory } ?: root.createDirectory(safeName) ?: return false
        images.forEachIndexed { index, image ->
            val fileName = "%03d.JPG".format(index + 1)
            folder.findFile(fileName)?.delete()
            val outputDocument = folder.createFile("image/jpeg", fileName) ?: return false
            val output = activity.contentResolver.openOutputStream(outputDocument.uri) ?: return false
            output.use { out -> image.inputStream().use { it.copyTo(out) } }
        }
        return true
    }

    var onDownloadedListener: OnDownloadedListener? = null

    interface OnDownloadedListener {
        fun handleMessage(succeed: Boolean)
        fun handleMessage(succeed: Boolean, pageNow: Int)
        fun handleMessage(pageNow: Int)
    }

    companion object {
        var wmdlt: WeakReference<MangaDlTools>? = null
    }
}
