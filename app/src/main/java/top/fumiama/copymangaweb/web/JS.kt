package top.fumiama.copymangaweb.web

import android.util.Log
import android.net.Uri
import android.webkit.JavascriptInterface
import top.fumiama.copymangaweb.activity.MainActivity.Companion.wm
import top.fumiama.copymangaweb.tool.SiteConfig
import top.fumiama.copymangaweb.tool.LocalCredentialStore
import top.fumiama.copymangaweb.tool.PropertiesTools
import java.io.File
import android.os.Build
import android.content.Intent
import top.fumiama.copymangaweb.activity.NovelDetailActivity
import top.fumiama.copymangaweb.activity.NovelLibraryActivity

class JS(private val comicWebView: Boolean = false) {
    private fun settings() = wm?.get()?.let { PropertiesTools(File("${it.filesDir}/settings.properties")) }

    @JavascriptInterface
    fun getBookrackSortField(): String = settings()?.get("bookrackSortField")
        ?.takeIf { it in listOf("update", "added", "read") } ?: "update"

    @JavascriptInterface
    fun getBookrackSortDirection(): String = settings()?.get("bookrackSortDirection")
        ?.takeIf { it in listOf("desc", "asc") } ?: "desc"

    @JavascriptInterface
    fun setBookrackSort(field: String, direction: String) {
        if (field !in listOf("update", "added", "read") || direction !in listOf("desc", "asc")) return
        settings()?.apply {
            this["bookrackSortField"] = field
            this["bookrackSortDirection"] = direction
        }
    }

    @JavascriptInterface
    fun isRanobeTraditionalEnabled(): Boolean = settings()?.get("ranobeTraditional") != "false"

    @JavascriptInterface
    fun isWebDarkModeEnabled(): Boolean = settings()?.get("webDarkMode") == "true"

    @JavascriptInterface
    fun toTraditionalChinese(text: String): String {
        if (!isRanobeTraditionalEnabled() || text.isBlank()) return text
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                traditionalTransliterator.javaClass.getMethod("transliterate", String::class.java)
                    .invoke(traditionalTransliterator, text) as String
            }.getOrDefault(text)
        } else text
    }
    private fun isLoginPage(pageUrl: String): Boolean {
        val activity = wm?.get() ?: return false
        val path = Uri.parse(pageUrl).path.orEmpty().lowercase()
        return SiteConfig.isAllowed(activity, pageUrl) && (path.contains("login") || path.contains("sign"))
    }

    @JavascriptInterface
    fun hasSavedLogin(pageUrl: String): Boolean = isLoginPage(pageUrl) &&
        (wm?.get()?.let { LocalCredentialStore(it).load() != null } == true)

    @JavascriptInterface
    fun getSavedAccount(pageUrl: String): String = if (isLoginPage(pageUrl))
        wm?.get()?.let { LocalCredentialStore(it).load()?.first } ?: "" else ""

    @JavascriptInterface
    fun getSavedPassword(pageUrl: String): String = if (isLoginPage(pageUrl))
        wm?.get()?.let { LocalCredentialStore(it).load()?.second } ?: "" else ""

    @JavascriptInterface
    fun saveLogin(pageUrl: String, account: String, password: String) {
        if (!isLoginPage(pageUrl) || account.isBlank() || password.isBlank()) return
        wm?.get()?.let { LocalCredentialStore(it).save(account, password) }
    }

    @JavascriptInterface
    fun clearSavedLogin() {
        wm?.get()?.let { LocalCredentialStore(it).clear() }
    }
    @JavascriptInterface
    fun loadComic(url: String) {
        val base = wm?.get()?.let { SiteConfig.get(it).trimEnd('/') } ?: return
        if (url.contains("/details/comic/")) wm?.get()?.rememberChapterEntryOrigin(url)
        val comicBase = "$base/comic"
        val u = when {
            url.contains("/details/comic/") -> "$comicBase${url.substringAfter("comic")}"
            url.contains("/comicContent/") -> "$comicBase/${url.substringAfter("comicContent/").substringBefore("/")}/chapter/${url.substringAfterLast("/")}"
            else -> ""
        }
        Log.d("MyJS", "Load comic: $u")
        if (u.contains("/chapter/")) {
            wm?.get()?.lastComicSelectionPath = Uri.parse(u.substringBefore("/chapter/"))
                .encodedPath.orEmpty()
        }
        wm?.get()?.loadHiddenUrl(u)
    }
    @JavascriptInterface
    fun shouldOpenComicInNewWebView(): Boolean = !comicWebView

    @JavascriptInterface
    fun openComicInNewWebView(url: String) {
        if (!comicWebView) wm?.get()?.openComicWebView(url)
    }
    @JavascriptInterface
    fun openNovel(url: String) {
        val activity = wm?.get() ?: return
        val path = Uri.parse(url).encodedPath.orEmpty().trimEnd('/')
        val segments = path.split('/').filter { it.isNotBlank() }
        val marker = segments.indexOfLast { it.equals("book", true) || it.equals("novel", true) }
        val slug = segments.getOrNull(marker + 1)?.takeIf {
            marker >= 0 && it.isNotBlank() && it !in listOf("discover", "search", "ranking")
        } ?: return
        activity.runOnUiThread {
            activity.startActivity(Intent(activity, NovelDetailActivity::class.java)
                .putExtra(NovelDetailActivity.EXTRA_SLUG, slug))
        }
    }
    @JavascriptInterface
    fun openNovelLocalShelf() {
        val activity = wm?.get() ?: return
        activity.runOnUiThread {
            activity.startActivity(Intent(activity, NovelLibraryActivity::class.java))
        }
    }
    @JavascriptInterface
    fun onVisiblePage(url: String) {
        wm?.get()?.onVisiblePage(url, comicWebView)
    }
    @JavascriptInterface
    fun hideFab() {
        wm?.get()?.hideFab()
    }
    @JavascriptInterface
    fun enterProfile(){
        wm?.get()?.setFab2DlList()
    }

    companion object {
        private val traditionalTransliterator by lazy {
            val type = Class.forName("android.icu.text.Transliterator")
            type.getMethod("getInstance", String::class.java).invoke(null, "Simplified-Traditional")
        }
    }
}
