package top.fumiama.copymangaweb.tool

import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.activity.DlActivity
import java.io.File
import java.lang.Thread.sleep
import java.lang.ref.WeakReference
import java.util.concurrent.Semaphore
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStream

class MangaDlTools(activity: DlActivity) {
    var exit = false
    private val sem = Semaphore(1)
    private val da = WeakReference(activity)
    private val d get() = da.get()
    private val p = PropertiesTools(File("${d?.filesDir}/chapters.hash"))
    private var imgUrlsList: Array<Array<String>?>? = null
    private var chaptersCount = 0

    init {
        wmdlt = WeakReference(this)
    }

    fun getImgsCountByHash(hash: String): Int?{
        return imgUrlsList?.get(p[hash].toInt())?.size
    }

    fun allocateChapterUrls(count: Int){
        imgUrlsList = arrayOfNulls(count)
        chaptersCount = 0
    }

    fun dlChapterUrl(url: String){
        sem.acquire()
        da.get()?.apply {
            p[url.substringAfterLast("/")] = (chaptersCount++).toString()
            runOnUiThread { mBinding.dwh.apply { post { loadUrl(url) } } }
        }
    }

    fun setChapterImages(hash: String, imgUrls: Array<String>){
        imgUrlsList?.set(p[hash].toInt(), imgUrls)
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
        imgUrlsList?.get(p[hash].toInt())?.let { images ->
            val dl = DownloadTools()
            val activity = d ?: return@let
            val settings = PropertiesTools(File("${activity.filesDir}/settings.properties"))
            val batchSize = settings["downloadBatchSize"]
                .toIntOrNull()?.coerceIn(1, 5) ?: 5
            val compressZip = settings["compressZip"] != "false"
            val safeName = zipf.nameWithoutExtension.replace(Regex("[\\/:*?\"<>|]"), "_")
            val treeUri = settings["downloadTreeUri"].takeUnless { it == "null" }
            val rootDocument = treeUri?.let { DocumentFile.fromTreeUri(activity, Uri.parse(it)) }
            val defaultRoot = File(activity.getExternalFilesDir(""), DlActivity.comicName).apply { mkdirs() }
            val zipOutput: OutputStream? = if (compressZip) {
                val customOutput = if (rootDocument != null && rootDocument.canWrite()) {
                    rootDocument.findFile("$safeName.zip")?.delete()
                    rootDocument.createFile("application/zip", "$safeName.zip")?.uri
                        ?.let { activity.contentResolver.openOutputStream(it) }
                } else null
                customOutput ?: run {
                    File(defaultRoot, "$safeName.zip").apply { if (exists()) delete(); createNewFile() }.outputStream()
                }
            } else null
            val imageFolder = if (!compressZip) {
                if (rootDocument != null && rootDocument.canWrite()) {
                    rootDocument.findFile(safeName)?.let { if (it.isDirectory) it else null }
                        ?: rootDocument.createDirectory(safeName)
                } else null
            } else null
            val defaultImageFolder = if (!compressZip && imageFolder == null)
                File(defaultRoot, safeName).apply { mkdirs() } else null
            val zip = zipOutput?.let { ZipOutputStream(CheckedOutputStream(it, CRC32())).apply { setLevel(9) } }
            var succeed = true
            for (i in images.indices) {
                var tryTimes = 3
                var s = false
                while (!s && tryTimes-- > 0){
                    s = activity.toolsBox.resolution.wrap(images[i]).let { u ->
                        dl.getHttpContent(u, SiteConfig.get(activity), activity.getString(R.string.pc_ua))?.let { data ->
                            if (zip != null) {
                                zip.putNextEntry(ZipEntry("$i.webp"))
                                zip.write(data)
                                zip.closeEntry()
                            } else if (imageFolder != null) {
                                imageFolder.findFile("$i.webp")?.delete()
                                val image = imageFolder.createFile("image/webp", "$i.webp")
                                val out = image?.uri?.let { activity.contentResolver.openOutputStream(it) }
                                if (out == null) return@let false
                                out.use { it.write(data) }
                            } else {
                                val folder = defaultImageFolder ?: return@let false
                                File(folder, "$i.webp").writeBytes(data)
                            }
                            true
                        } ?: false
                    }
                    if (!s) {
                        onDownloadedListener?.handleMessage(i + 1)
                        sleep(2000)
                    }
                }
                if(!s && tryTimes <= 0) succeed = false
                onDownloadedListener?.handleMessage(s, i + 1)
                zip?.flush()
                if (exit) break
                if ((i + 1) % batchSize == 0 && i < images.lastIndex) {
                    sleep(Random.nextLong(1_000L, 20_001L))
                }
            }
            zip?.close()
            onDownloadedListener?.handleMessage(succeed)
        }
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
