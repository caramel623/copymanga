package top.fumiama.copymangaweb.web

import android.util.Log
import android.webkit.JavascriptInterface
import top.fumiama.copymangaweb.activity.DlActivity
import top.fumiama.copymangaweb.activity.MainActivity.Companion.mh
import top.fumiama.copymangaweb.activity.MainActivity.Companion.wm
import top.fumiama.copymangaweb.handler.MainHandler
import top.fumiama.copymangaweb.tool.PropertiesTools
import top.fumiama.copymangaweb.tool.MangaDlTools.Companion.wmdlt
import top.fumiama.copymangaweb.view.JSWebView
import java.io.File
import java.lang.ref.WeakReference

class JSHidden(private val webView: WeakReference<JSWebView>) {
    @JavascriptInterface
    fun scheduleCollectorTick() {
        val view = webView.get() ?: return
        view.postDelayed({
            view.evaluateJavascript("window.cmCollectorTick && window.cmCollectorTick();", null)
        }, 250)
    }

    @JavascriptInterface
    fun reportCollectorCount(expected: Int, actual: Int, source: String) {
        val message = "Collector count: expected=$expected, actual=$actual, source=$source"
        if (expected > 0 && expected != actual) Log.w("MyJSH", message)
        else Log.d("MyJSH", message)
    }

    @JavascriptInterface
    fun isDownloadMode(): Boolean = wm?.get()?.saveUrlsOnly == true

    @JavascriptInterface
    fun getDownloadBatchSize(): Int {
        val activity = wm?.get() ?: return 5
        return (PropertiesTools(File("${activity.filesDir}/settings.properties"))["downloadBatchSize"]
            .toIntOrNull() ?: 5).coerceIn(1, 5)
    }
    @JavascriptInterface
    fun getChapterLoadSpeed(): Int {
        val activity = wm?.get() ?: return 320
        return PropertiesTools(File("${activity.filesDir}/settings.properties"))["loadSpeed"].toIntOrNull() ?: 320
    }
    @JavascriptInterface
    fun rememberChapterSelectionUrl(chapterUrl: String) {
        wm?.get()?.rememberChapterSelectionUrl(chapterUrl)
    }
    @JavascriptInterface
    fun startChapter(header: String) {
        wm?.get()?.startViewManga(header)
    }
    @JavascriptInterface
    fun loadChapter(listString: String){
        wm?.get()?.callViewManga(listString)
    }
    @JavascriptInterface
    fun loadChapterChunk(content: String, first: Boolean, finished: Boolean) {
        wm?.get()?.callViewMangaChunk(content, first, finished)
    }
    @JavascriptInterface
    fun setTitle(title:String){
        Log.d("MyJSH", "Set title: $title")
        DlActivity.comicName = title
    }
    @JavascriptInterface
    fun setFab(content: String){
        wm?.get()?.setFab(content)
    }
    @JavascriptInterface
    fun setLoadingDialog(display: Boolean) {
        mh?.sendEmptyMessage(if (display) MainHandler.SHOW_LOADING_DIALOG else MainHandler.HIDE_LOADING_DIALOG)
    }
    @JavascriptInterface
    fun setLoadingDialogProgress(index: String, count: String) {
        if (isDownloadMode()) wmdlt?.get()?.updateScanProgress(index, count)
        mh?.obtainMessage(MainHandler.SET_LOADING_DIALOG_TEXT, "$index/$count")?.sendToTarget()
    }
}
