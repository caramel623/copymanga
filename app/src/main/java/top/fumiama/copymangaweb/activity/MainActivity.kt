package top.fumiama.copymangaweb.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.fumiama.copymangaweb.BuildConfig
import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.activity.DlActivity.Companion.json
import top.fumiama.copymangaweb.activity.template.ToolsBoxActivity
import top.fumiama.copymangaweb.activity.viewmodel.MainViewModel
import top.fumiama.copymangaweb.databinding.ActivityMainBinding
import top.fumiama.copymangaweb.handler.MainHandler
import top.fumiama.copymangaweb.tool.InsetsTools
import top.fumiama.copymangaweb.tool.ChapterNavigationStore
import top.fumiama.copymangaweb.tool.PropertiesTools
import top.fumiama.copymangaweb.tool.MangaDlTools.Companion.wmdlt
import top.fumiama.copymangaweb.tool.SetDraggable
import top.fumiama.copymangaweb.tool.Updater
import top.fumiama.copymangaweb.tool.SiteConfig
import top.fumiama.copymangaweb.web.JS
import top.fumiama.copymangaweb.web.JSHidden
import top.fumiama.copymangaweb.web.WebChromeClient
import top.fumiama.copymangaweb.view.JSWebView
import java.lang.ref.WeakReference
import java.io.File

class MainActivity: ToolsBoxActivity() {
    var uploadMessageAboveL: ValueCallback<Array<Uri>>? = null
    var saveUrlsOnly = false
    lateinit var mBinding: ActivityMainBinding
    private val mViewModel = MainViewModel()
    private var currentSiteUrl = ""
    private var comicWebView: JSWebView? = null
    private val chapterNavigationStore by lazy { ChapterNavigationStore(this) }
    private var chapterEntryOriginUrl: String? = null
    private var chapterEntryComicSlug: String? = null
    private var lastVisibleNonComicUrl: String? = null
    // The host may be changed in Settings while reading, so retain only the
    // comic page path and resolve it against the current configured entry.
    var lastComicSelectionPath: String? = null

    fun lastComicSelectionUrl(): String? {
        val saved = chapterNavigationStore.read()
        saved.selectionUrl?.let { return it }
        val path = lastComicSelectionPath ?: saved.selectionPath ?: return null
        return SiteConfig.get(this).trimEnd('/') + if (path.startsWith('/')) path else "/$path"
    }

    fun rememberChapterSelectionUrl(chapterUrl: String) {
        val path = Uri.parse(chapterUrl).encodedPath.orEmpty()
        val selection = path.substringBefore("/chapter/")
        if (selection.isNotBlank() && selection != path) {
            lastComicSelectionPath = selection
            chapterNavigationStore.recordChapter(chapterUrl, selection)
        }
    }

    fun onVisiblePage(pageUrl: String, comicWebView: Boolean = false) {
        mBinding.w.post { handleVisiblePage(pageUrl, comicWebView) }
    }

    fun onVisiblePageStarted(pageUrl: String, comicWebView: Boolean = false) {
        handleVisiblePage(pageUrl, comicWebView)
    }

    private fun handleVisiblePage(pageUrl: String, comicWebView: Boolean) {
        val path = Uri.parse(pageUrl).encodedPath.orEmpty().trimEnd('/')
        if (!comicWebView && path.contains("/details/comic/")) {
            // Some site cards navigate through JavaScript instead of a normal
            // anchor. Move that navigation to the comic WebView as well.
            openComicWebView(pageUrl)
            mBinding.w.post {
                mBinding.w.stopLoading()
                if (mBinding.w.canGoBack()) mBinding.w.goBack()
            }
            return
        }
        if (!comicWebView && isOutsideComicFlow(path)) {
            chapterNavigationStore.clear()
            chapterEntryOriginUrl = null
            chapterEntryComicSlug = null
            lastComicSelectionPath = null
            lastVisibleNonComicUrl = pageUrl
        } else if (path.contains("/details/comic/")) {
            rememberChapterEntryOrigin(pageUrl)
        } else if (path.contains("/comicContent/") || path.contains("/chapter/")) {
            chapterNavigationStore.recordChapter(pageUrl)
        }
    }

    fun openComicWebView(url: String) {
        if (!SiteConfig.isAllowed(this, url)) return
        runOnUiThread {
            comicWebView?.let {
                if (mBinding.comicWebContainer.visibility == View.VISIBLE && it.url == url) {
                    return@runOnUiThread
                }
            }
            val webView = comicWebView ?: JSWebView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(webBackgroundColor())
                setWebViewClient("i.js", comicWebView = true)
                webChromeClient = WebChromeClient()
                loadJSInterface(JS(comicWebView = true))
                mBinding.comicWebContainer.addView(this)
                comicWebView = this
            }
            webView.stopLoading()
            webView.clearHistory()
            webView.loadUrl(url)
            mBinding.comicWebContainer.visibility = View.VISIBLE
        }
    }

    fun visibleWebView(): WebView =
        comicWebView?.takeIf {
            mBinding.comicWebContainer.visibility == View.VISIBLE
        } ?: mBinding.w

    private fun closeComicWebView() {
        mBinding.comicWebContainer.visibility = View.GONE
        comicWebView?.let { oldWebView ->
            oldWebView.stopLoading()
            oldWebView.loadUrl("about:blank")
            mBinding.comicWebContainer.removeView(oldWebView)
            oldWebView.removeAllViews()
            oldWebView.destroy()
        }
        comicWebView = null
        mBinding.wh.stopLoading()
        chapterNavigationStore.clear()
        chapterEntryOriginUrl = null
        chapterEntryComicSlug = null
        lastComicSelectionPath = null
        hideFab()
    }

    fun rememberChapterEntryOrigin(selectionUrl: String) {
        val slug = Uri.parse(selectionUrl).encodedPath.orEmpty()
            .substringAfter("/details/comic/", "")
            .substringBefore('/')
            .takeIf { it.isNotBlank() } ?: return
        mBinding.w.post {
            val saved = chapterNavigationStore.read()
            if ((chapterEntryComicSlug == slug && chapterEntryOriginUrl != null) ||
                (saved.comicSlug == slug && saved.originUrl != null)) {
                chapterEntryComicSlug = slug
                chapterEntryOriginUrl = chapterEntryOriginUrl ?: saved.originUrl
                return@post
            }
            val history = mBinding.w.copyBackForwardList()
            var origin: String? = lastVisibleNonComicUrl
            if (origin == null) {
                for (i in history.currentIndex - 1 downTo 0) {
                    val itemUrl = history.getItemAtIndex(i).url
                    val path = Uri.parse(itemUrl).encodedPath.orEmpty().trimEnd('/')
                    if (itemUrl.startsWith("http") && isOutsideComicFlow(path)) {
                        origin = itemUrl
                        break
                    }
                }
            }
            chapterEntryComicSlug = slug
            chapterEntryOriginUrl = origin
            chapterNavigationStore.recordEntry(origin, selectionUrl, slug)
        }
    }

    private fun returnToChapterEntryOrigin(): Boolean {
        val saved = chapterNavigationStore.read()
        val origin = chapterEntryOriginUrl ?: saved.originUrl ?: return false
        val slug = chapterEntryComicSlug ?: saved.comicSlug ?: return false
        val path = Uri.parse(visibleWebView().url.orEmpty()).encodedPath.orEmpty().trimEnd('/')
        val isMobileSelection = path.substringAfter("/details/comic/", "")
            .substringBefore('/') == slug
        val isDesktopSelection = path == "/comic/$slug"
        if (!isMobileSelection && !isDesktopSelection) return false
        chapterNavigationStore.markReturningToOrigin()
        visibleWebView().loadUrl(origin)
        return true
    }

    fun returnToChapterSelection() {
        val selectionPath = lastComicSelectionPath ?: chapterNavigationStore.read().selectionPath
        val comicSlug = selectionPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        visibleWebView().apply { post {
            stopLoading()
            val history = copyBackForwardList()
            val currentIndex = history.currentIndex
            var targetIndex = -1
            if (comicSlug != null) {
                for (i in currentIndex - 1 downTo 0) {
                    val path = Uri.parse(history.getItemAtIndex(i).url).encodedPath.orEmpty().trimEnd('/')
                    val isDesktopSelection = path == selectionPath?.trimEnd('/')
                    val isMobileSelection = path.substringAfter("/details/comic/", "")
                        .substringBefore('/') == comicSlug
                    if (isDesktopSelection || isMobileSelection) {
                        targetIndex = i
                        break
                    }
                }
            }
            chapterNavigationStore.markReturningToSelection()
            if (targetIndex >= 0) goBackOrForward(targetIndex - currentIndex)
            else lastComicSelectionUrl()?.let { loadUrl(it) }
        } }
    }

    private fun isOutsideComicFlow(path: String): Boolean =
        !path.contains("/details/comic/") &&
            !path.contains("/comicContent/") &&
            !path.contains("/chapter/") &&
            !path.startsWith("/comic/")

    @SuppressLint("JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        mBinding.mainViewModel = mViewModel
        mBinding.lifecycleOwner = this
        setContentView(mBinding.root)
        InsetsTools.applySafeContentInsets(this, mBinding.root)

        chapterNavigationStore.read().let { saved ->
            chapterEntryOriginUrl = saved.originUrl
            chapterEntryComicSlug = saved.comicSlug
            lastComicSelectionPath = saved.selectionPath
        }

        wm = WeakReference(this)
        mh = MainHandler(Looper.myLooper()!!)
        toolsBox.netInfo.let {
            if(it == "无网络" || it == "错误") {
                setFab2DlList()
                return@let
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    goCheckUpdate(false)
                }
            }

            WebView.setWebContentsDebuggingEnabled(true)
            mBinding.w.apply { post {
                setBackgroundColor(webBackgroundColor())
                setWebViewClient("i.js")
                webChromeClient = WebChromeClient()
                loadJSInterface(JS())
                currentSiteUrl = SiteConfig.get(this@MainActivity)
                loadUrl(currentSiteUrl)
            } }

            mBinding.wh.apply { post {
                settings.userAgentString = getString(R.string.pc_ua)
                webChromeClient = WebChromeClient()
                setWebViewClient("h.js")
                loadJSInterface(JSHidden(WeakReference(this)))
            } }
        }
        SetDraggable().with(this).onto(mBinding.fab)
    }

    override fun onResume() {
        super.onResume()
        if (::mBinding.isInitialized && currentSiteUrl.isNotEmpty()) {
            val configuredUrl = SiteConfig.get(this)
            if (configuredUrl != currentSiteUrl) {
                currentSiteUrl = configuredUrl
                mBinding.w.loadUrl(configuredUrl)
            }
            applyWebDarkMode()
        }
    }

    private fun webDarkModeEnabled(): Boolean =
        PropertiesTools(File("$filesDir/settings.properties"))["webDarkMode"] == "true"

    private fun webBackgroundColor(): Int =
        if (webDarkModeEnabled()) Color.rgb(7, 21, 34) else Color.WHITE

    private fun applyWebDarkMode() {
        mBinding.w.setBackgroundColor(webBackgroundColor())
        mBinding.w.evaluateJavascript(
            "if (typeof invoke !== 'undefined') invoke.applyWebDarkMode();",
            null
        )
        comicWebView?.let {
            it.setBackgroundColor(webBackgroundColor())
            it.evaluateJavascript(
                "if (typeof invoke !== 'undefined') invoke.applyWebDarkMode();",
                null
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val activeComicWebView = comicWebView
        if (activeComicWebView != null &&
            mBinding.comicWebContainer.visibility == View.VISIBLE) {
            val comicPath = Uri.parse(activeComicWebView.url.orEmpty()).encodedPath.orEmpty().trimEnd('/')
            val isChapterSelection = comicPath.contains("/details/comic/") ||
                (comicPath.startsWith("/comic/") && !comicPath.contains("/chapter/"))
            if (isChapterSelection || !activeComicWebView.canGoBack()) {
                closeComicWebView()
            } else {
                activeComicWebView.goBack()
            }
            return
        }
        if (returnToChapterEntryOrigin()) return
        if(mBinding.w.canGoBack()) mBinding.w.goBack()
        else super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {  //处理返回的图片，并进行上传
            if (uploadMessageAboveL == null || resultCode != RESULT_OK) return
            data?.let {
                onActivityResultAboveL(requestCode, resultCode, it)
            }
        }
    }

    private fun onActivityResultAboveL(requestCode: Int, resultCode: Int, intent: Intent) {
        if (requestCode != FILE_CHOOSER_RESULT_CODE ||
            uploadMessageAboveL == null ||
            resultCode != RESULT_OK
        ) return
        intent.clipData?.let { clipData ->
            var results = arrayOf<Uri>()
            for (i in 0..clipData.itemCount) {
                val item = clipData.getItemAt(i)
                results += item.uri
            }
            if (intent.dataString != null) {
                uploadMessageAboveL?.onReceiveValue(results)
                uploadMessageAboveL = null
            }
        }
    }

    private suspend fun goCheckUpdate(ignoreSkip: Boolean) {
        Updater(
            WeakReference(this),
            toolsBox,
            ignoreSkip,
            getPreferences(MODE_PRIVATE).getInt("skipVersion", 0)
        ).check(BuildConfig.VERSION_CODE)
    }

    fun loadHiddenUrl(u: String) {
        mBinding.wh.apply { post { loadUrl(u) } }
    }

    fun updateLoadProgress(p: Int) {
        lifecycleScope.launch { mViewModel.updateLoadProgress(p) }
    }

    fun setFab(content: String) {
        json = content
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                mViewModel.showDlList.value = false
                mViewModel.setFabVisibility(true)
            }
        }
    }

    fun setFab2DlList() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                mViewModel.showDlList.value = true
                mViewModel.setFabVisibility(true)
            }
        }
    }

    fun hideFab() {
        lifecycleScope.launch { mViewModel.setFabVisibility(false) }
    }

    fun onFabClicked(v: View) {
        DlListActivity.currentDir = getExternalFilesDir("")
        startActivity(
            Intent(this, (if(mViewModel.showDlList.value == true) DlListActivity::class else DlActivity::class).java)
                .putExtra("title", "我的下载")
        )
    }

    fun openSettings(v: View) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun openImageChooserActivity() {
        // 调用自己的图库
        startActivityForResult(
            Intent.createChooser(
                Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*"), "Image Chooser"
            ), FILE_CHOOSER_RESULT_CODE
        )
    }

    fun callViewManga(content: String) {
        lifecycleScope.launch { withContext(Dispatchers.IO) {
            val listChapter = content.split('\n')
            if(!saveUrlsOnly) {
                ViewMangaActivity.titleText = listChapter[0].substringBeforeLast(' ')
                ViewMangaActivity.nextChapterUrl = listChapter[1].let { if(it == "null") null else it }
                ViewMangaActivity.previousChapterUrl = listChapter[2].let { if(it == "null") null else it }
                ViewMangaActivity.imgUrls = arrayOf()
                for(i in 3 until listChapter.size) ViewMangaActivity.imgUrls += listChapter[i]
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@MainActivity, ViewMangaActivity::class.java))
                }
            } else {
                var imgs = arrayOf<String>()
                for(i in 3 until listChapter.size) imgs += listChapter[i]
                wmdlt?.get()?.setChapterImages(listChapter[0].substringAfterLast(' '), imgs)
            }
        } }
    }

    fun startViewManga(header: String) {
        val lines = header.split('\n').filter { it.isNotBlank() }
        if (lines.size < 3 || saveUrlsOnly) return
        ViewMangaActivity.titleText = lines[0].substringBeforeLast(' ')
        ViewMangaActivity.nextChapterUrl = lines[1].let { if (it == "null") null else it }
        ViewMangaActivity.previousChapterUrl = lines[2].let { if (it == "null") null else it }
        ViewMangaActivity.imgUrls = arrayOf()
        runOnUiThread {
            startActivity(Intent(this, ViewMangaActivity::class.java))
        }
    }

    @Synchronized
    fun callViewMangaChunk(content: String, first: Boolean, finished: Boolean) {
        val lines = content.split('\n').filter { it.isNotBlank() }
        if (first) {
            if (lines.size < 4) return
            ViewMangaActivity.titleText = lines[0].substringBeforeLast(' ')
            ViewMangaActivity.nextChapterUrl = lines[1].let { if (it == "null") null else it }
            ViewMangaActivity.previousChapterUrl = lines[2].let { if (it == "null") null else it }
            ViewMangaActivity.imgUrls = lines.drop(3).toTypedArray()
            runOnUiThread {
                startActivity(Intent(this, ViewMangaActivity::class.java))
            }
        } else {
            ViewMangaActivity.appendOnlineImages(lines.toTypedArray(), finished)
        }
    }

    companion object {
        const val FILE_CHOOSER_RESULT_CODE = 1
        var wm: WeakReference<MainActivity>? = null
        var mh: MainHandler? = null
    }
}
