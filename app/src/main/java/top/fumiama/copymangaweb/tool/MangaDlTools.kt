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
import android.webkit.CookieManager
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
        return p[hash].toIntOrNull()?.let { imgUrlsList?.getOrNull(it)?.size }
    }

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
        val index = p[hash].toIntOrNull() ?: run { sem.release(); return }
        imgUrlsList?.getOrNull(index)?.let { imgUrlsList?.set(index, imgUrls.filter { it.startsWith("http://") || it.startsWith("https://") }.toTypedArray()) }
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
            val rootDocument = treeUri?.let { DocumentFile.fromTreeUri(activity, Uri.parse(it)) }
            if (treeUri != null && (rootDocument == null || !rootDocument.canWrite())) {
                onDownloadedListener?.handleMessage(false)
                return@let
            }
            val defaultRoot = if (treeUri == null) File(activity.getExternalFilesDir(""), DlActivity.comicName).apply { mkdirs() } else null
            val zipOutput: OutputStream? = if (compressZip) {
                val customOutput = if (rootDocument != null && rootDocument.canWrite()) {
                    rootDocument.findFile("$safeName.zip")?.delete()
                    rootDocument.createFile("application/zip", "$safeName.zip")?.uri
                        ?.let { activity.contentResolver.openOutputStream(it) }
                } else null
                customOutput ?: defaultRoot?.let {
                    File(it, "$safeName.zip").apply { if (exists()) delete(); createNewFile() }.outputStream()
                }
            } else null
            if (compressZip && zipOutput == null) {
                onDownloadedListener?.handleMessage(false)
                return@let
            }
            val imageFolder = if (!compressZip) {
                if (rootDocument != null && rootDocument.canWrite()) {
                    rootDocument.findFile(safeName)?.let { if (it.isDirectory) it else null }
                        ?: rootDocument.createDirectory(safeName)
                } else null
            } else null
            val defaultImageFolder = if (!compressZip && imageFolder == null) defaultRoot?.let { File(it, safeName).apply { mkdirs() } } else null
            if (!compressZip && imageFolder == null && defaultImageFolder == null) {
                onDownloadedListener?.handleMessage(false)
                return@let
            }
            val zip = zipOutput?.let { ZipOutputStream(CheckedOutputStream(it, CRC32())).apply { setLevel(9) } }
            var succeed = true
            for (i in images.indices) {
                val fileName = "%03d.JPG".format(i + 1)
                var tryTimes = 3
                var s = false
                while (!s && tryTimes-- > 0){
                    s = try {
                        val candidates = linkedSetOf(
                            images[i],
                            activity.toolsBox.resolution.wrap(images[i])
                        )
                        candidates.any { u ->
                            dl.getHttpContent(u, SiteConfig.get(activity), activity.getString(R.string.pc_ua), cookie)?.let { data ->
                            if (zip != null) {
                                zip.putNextEntry(ZipEntry(fileName))
                                zip.write(data)
                                zip.closeEntry()
                            } else if (imageFolder != null) {
                                imageFolder.findFile(fileName)?.delete()
                                val image = imageFolder.createFile("image/jpeg", fileName)
                                val out = image?.uri?.let { activity.contentResolver.openOutputStream(it) }
                                if (out == null) return@let false
                                out.use { it.write(data) }
                            } else {
                                val folder = defaultImageFolder ?: return@let false
                                File(folder, fileName).writeBytes(data)
                            }
                            true
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
                if(!s && tryTimes <= 0) succeed = false
                onDownloadedListener?.handleMessage(s, i + 1)
                try {
                    zip?.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                    succeed = false
                }
                if (exit) break
                if (i < images.lastIndex) {
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
            try {
                zip?.finish()
                zip?.close()
                onDownloadedListener?.handleMessage(succeed)
            } catch (e: Exception) {
                e.printStackTrace()
                onDownloadedListener?.handleMessage(false)
            }
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
