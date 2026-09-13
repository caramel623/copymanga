package top.fumiama.copymangaweb.activity

import android.app.Activity
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebViewClient
import android.app.AlertDialog
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import android.webkit.WebView
import android.widget.*
import kotlinx.coroutines.*
import top.fumiama.copymangaweb.data.NovelRepository
import top.fumiama.copymangaweb.tool.NovelReadingRecord
import top.fumiama.copymangaweb.tool.NovelReadingStore
import top.fumiama.copymangaweb.tool.PropertiesTools
import java.io.File

class NovelReaderActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var textView: TextView
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var toolbar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var positionLabel: TextView
    private var seeking = false
    private var ready = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var pendingY: Int? = null
    private lateinit var properties: PropertiesTools
    private var loadedHtml: Pair<String, String>? = null
    private val slug by lazy { intent.getStringExtra(EXTRA_SLUG).orEmpty() }
    private val bookName by lazy { intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty() }
    private val volumeId by lazy { intent.getStringExtra(EXTRA_VOLUME_ID).orEmpty() }
    private val volumeName by lazy { intent.getStringExtra(EXTRA_VOLUME_NAME).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        properties = PropertiesTools(File("$filesDir/settings.properties"))
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbar = createToolbar()
        progress = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.CENTER })
        textView = TextView(this).apply { setPadding(42, 32, 42, 72); setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(textView); visibility = View.GONE }
        webView = WebView(this).apply { visibility = View.GONE; settings.javaScriptEnabled = false; settings.builtInZoomControls = true; settings.displayZoomControls = false }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        bottomBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bookmarks = LinearLayout(this)
        bookmarks.addView(Button(this).apply { text = "加入書籤"; setOnClickListener { addBookmark() } }, LinearLayout.LayoutParams(0, -2, 1f))
        bookmarks.addView(Button(this).apply { text = "書籤列表"; setOnClickListener { showBookmarks() } }, LinearLayout.LayoutParams(0, -2, 1f))
        bottomBar.addView(bookmarks)
        positionLabel = TextView(this).apply { gravity = android.view.Gravity.CENTER }
        bottomBar.addView(positionLabel)
        seek = SeekBar(this).apply {
            max = 1000; isEnabled = false
            contentDescription = "本卷閱讀進度"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) { positionLabel.text = "本卷 ${value / 10f}%"; jumpTo((maxScroll() * value / 1000f).toInt()) }
                }
                override fun onStopTrackingTouch(bar: SeekBar?) { seeking = false; updateProgress() }
            })
        }
        bottomBar.addView(seek)
        toolbar.visibility = View.GONE; bottomBar.visibility = View.GONE
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> updateProgress() }
        webView.setOnScrollChangeListener { _, _, _, _, _ -> updateProgress() }
        textView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateProgress() }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.post { pendingY?.let { view.scrollTo(0, it); pendingY = null }; updateProgress() }
            }
        }
        setContentView(FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(-1, -1))
            addView(toolbar, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP))
            addView(bottomBar, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
        })
        applyAppearance()
        load()
    }

    private fun createToolbar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(8, 8, 8, 8)
        addView(Button(this@NovelReaderActivity).apply { text = "A−"; setOnClickListener { adjustNumber("novelFontSize", -1f, 12f, 36f) } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(this@NovelReaderActivity).apply { text = "A+"; setOnClickListener { adjustNumber("novelFontSize", 1f, 12f, 36f) } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(this@NovelReaderActivity).apply { text = "行距−"; setOnClickListener { adjustNumber("novelLineSpacing", -0.1f, 1f, 2.5f) } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(this@NovelReaderActivity).apply { text = "行距+"; setOnClickListener { adjustNumber("novelLineSpacing", 0.1f, 1f, 2.5f) } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(this@NovelReaderActivity).apply { text = "底色"; setOnClickListener { cycleTheme() } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun load() = scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val repository = NovelRepository(this@NovelReaderActivity)
                val metadata = repository.loadVolume(slug, volumeId)
                metadata to repository.loadResource(metadata.address, metadata.encoding)
            }
        }.onSuccess { (metadata, resource) ->
            progress.visibility = View.GONE
            val (contentType, originalBody) = resource
            val body = withContext(Dispatchers.Default) { toTraditionalIfEnabled(originalBody) }
            val trimmed = body.trimStart()
            val isHtml = contentType.contains("html") || trimmed.startsWith("<!doctype", true) || trimmed.startsWith("<html", true)
            ready = true; seek.isEnabled = true
            val savedY = if (intent.hasExtra("bookmark_y")) intent.getIntExtra("bookmark_y", 0) else NovelReadingStore(this@NovelReaderActivity).find(slug)?.takeIf { it.volumeId == volumeId }?.scrollY ?: 0
            if (isHtml) {
                webView.visibility = View.VISIBLE
                loadedHtml = metadata.address to body
                pendingY = savedY
                loadStyledHtml()
            } else {
                scroll.visibility = View.VISIBLE
                textView.text = body.replace("\r\n", "\n")
                scroll.post { scroll.scrollTo(0, savedY); updateProgress() }
            }
        }.onFailure { progress.visibility = View.GONE; Toast.makeText(this@NovelReaderActivity, "載入正文失敗：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun adjustNumber(key: String, delta: Float, min: Float, max: Float) {
        val fraction = if (maxScroll() > 0) currentY().toFloat() / maxScroll() else 0f
        val fallback = if (key == "novelFontSize") 19f else 1.5f
        val value = ((properties[key].toFloatOrNull() ?: fallback) + delta).coerceIn(min, max)
        properties[key] = value.toString(); applyAppearance()
        if (scroll.visibility == View.VISIBLE) scroll.post { jumpTo((maxScroll() * fraction).toInt()); updateProgress() }
    }

    private fun cycleTheme() {
        val themes = listOf("light", "dark", "black")
        properties["novelTheme"] = themes[(themes.indexOf(properties["novelTheme"]).coerceAtLeast(0) + 1) % themes.size]
        applyAppearance()
    }

    private fun applyAppearance() {
        val font = properties["novelFontSize"].toFloatOrNull() ?: 19f
        val spacing = properties["novelLineSpacing"].toFloatOrNull() ?: 1.5f
        val (background, foreground, controlBackground) = when (properties["novelTheme"]) {
            "dark" -> Triple(Color.rgb(38, 38, 38), Color.rgb(232, 232, 232), Color.rgb(68, 68, 68))
            "black" -> Triple(Color.BLACK, Color.rgb(210, 210, 210), Color.rgb(30, 30, 30))
            else -> Triple(Color.rgb(250, 247, 238), Color.rgb(35, 35, 35), Color.rgb(230, 225, 214))
        }
        root.setBackgroundColor(background); scroll.setBackgroundColor(background); webView.setBackgroundColor(background)
        textView.setTextColor(foreground); textView.textSize = font; textView.setLineSpacing(0f, spacing)
        toolbar.setBackgroundColor(background)
        bottomBar.setBackgroundColor(background)
        positionLabel.setTextColor(foreground)
        fun tint(view: View) {
            if (view is Button) { view.setTextColor(foreground); view.backgroundTintList = ColorStateList.valueOf(controlBackground) }
            if (view is ViewGroup) for (i in 0 until view.childCount) tint(view.getChildAt(i))
        }
        tint(bottomBar)
        for (index in 0 until toolbar.childCount) {
            (toolbar.getChildAt(index) as? Button)?.apply {
                setTextColor(foreground)
                backgroundTintList = ColorStateList.valueOf(controlBackground)
            }
        }
        if (webView.visibility == View.VISIBLE) { pendingY = webView.scrollY; loadStyledHtml() }
    }

    private fun loadStyledHtml() {
        val (base, html) = loadedHtml ?: return
        val font = properties["novelFontSize"].toFloatOrNull() ?: 19f
        val spacing = properties["novelLineSpacing"].toFloatOrNull() ?: 1.5f
        val (background, foreground) = when (properties["novelTheme"]) {
            "dark" -> "#262626" to "#e8e8e8"
            "black" -> "#000000" to "#d2d2d2"
            else -> "#faf7ee" to "#232323"
        }
        val style = "<style>html,body{background:$background!important;color:$foreground!important;font-size:${font}px!important;line-height:$spacing!important;}img{max-width:100%;height:auto;}a{color:#6fa8dc;}</style>"
        val styled = if (html.contains("</head>", true)) html.replace(Regex("</head>", RegexOption.IGNORE_CASE), "$style</head>") else style + html
        webView.loadDataWithBaseURL(base, styled, "text/html", "UTF-8", null)
    }

    private fun toTraditionalIfEnabled(text: String): String {
        if (properties["ranobeTraditional"] == "false" || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return text
        return runCatching {
            val type = Class.forName("android.icu.text.Transliterator")
            val converter = type.getMethod("getInstance", String::class.java).invoke(null, "Simplified-Traditional")
            type.getMethod("transliterate", String::class.java).invoke(converter, text) as String
        }.getOrDefault(text)
    }

    override fun onPause() {
        super.onPause()
        val y = when {
            ::webView.isInitialized && webView.visibility == View.VISIBLE -> webView.scrollY
            ::scroll.isInitialized -> scroll.scrollY
            else -> 0
        }
        if (ready) NovelReadingStore(this).save(NovelReadingRecord(slug, bookName, volumeId, volumeName, y, System.currentTimeMillis()))
    }

    private fun currentY() = if (webView.visibility == View.VISIBLE) webView.scrollY else scroll.scrollY
    private fun maxScroll(): Int = if (webView.visibility == View.VISIBLE)
        (webView.contentHeight * webView.scale - webView.height).toInt().coerceAtLeast(0)
        else (textView.height - scroll.height).coerceAtLeast(0)
    private fun jumpTo(y: Int) {
        if (webView.visibility == View.VISIBLE) webView.scrollTo(0, y.coerceIn(0, maxScroll()))
        else scroll.scrollTo(0, y.coerceIn(0, maxScroll()))
    }
    private fun updateProgress() {
        if (!seeking && ::seek.isInitialized) {
            seek.progress = if (maxScroll() == 0) 0 else (currentY().toLong() * 1000 / maxScroll()).toInt().coerceIn(0, 1000)
            positionLabel.text = "本卷 ${seek.progress / 10f}%"
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; downTime = event.eventTime; moved = false }
            MotionEvent.ACTION_POINTER_DOWN -> moved = true
            MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - downX) > ViewConfiguration.get(this).scaledTouchSlop || kotlin.math.abs(event.y - downY) > ViewConfiguration.get(this).scaledTouchSlop) moved = true
            MotionEvent.ACTION_UP -> {
                val bounds = android.graphics.Rect()
                val reader = if (webView.visibility == View.VISIBLE) webView else scroll
                reader.getGlobalVisibleRect(bounds)
                if (ready && !moved && event.eventTime - downTime < ViewConfiguration.getLongPressTimeout() &&
                    event.rawX > bounds.left + bounds.width() / 3 && event.rawX < bounds.right - bounds.width() / 3 &&
                    event.rawY > bounds.top + bounds.height() / 3 && event.rawY < bounds.bottom - bounds.height() / 3) {
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel); cancel.recycle()
                    val visibility = if (toolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    toolbar.visibility = visibility; bottomBar.visibility = visibility
                    reader.post { updateProgress() }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun bookmarkPrefs() = getSharedPreferences("novel_bookmarks", MODE_PRIVATE)
    private fun bookmarks(): JSONArray = runCatching { JSONArray(bookmarkPrefs().getString(slug, "[]")) }.getOrElse { JSONArray() }
    private fun addBookmark() {
        if (!ready) return
        val y = currentY()
        val input = EditText(this).apply { setText("$volumeName · ${seek.progress / 10f}%") }
        AlertDialog.Builder(this).setTitle("新增書籤").setView(input).setNegativeButton("取消", null)
            .setPositiveButton("儲存") { _, _ ->
                val items = bookmarks().put(JSONObject().put("volumeId", volumeId).put("volumeName", volumeName).put("y", y).put("name", input.text.toString()))
                bookmarkPrefs().edit().putString(slug, items.toString()).apply()
                Toast.makeText(this, "已儲存書籤", Toast.LENGTH_SHORT).show()
            }.show()
    }
    private fun showBookmarks() {
        val items = bookmarks()
        if (items.length() == 0) { Toast.makeText(this, "這本小說尚無書籤", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("${bookName} 書籤")
            .setItems(Array(items.length()) { items.getJSONObject(it).optString("name") }) { _, index ->
                val item = items.getJSONObject(index)
                AlertDialog.Builder(this).setTitle(item.optString("name")).setNegativeButton("取消", null)
                    .setNeutralButton("刪除") { _, _ -> items.remove(index); bookmarkPrefs().edit().putString(slug, items.toString()).apply() }
                    .setPositiveButton("前往") { _, _ ->
                        if (item.getString("volumeId") == volumeId) jumpTo(item.optInt("y"))
                        else {
                            startActivity(Intent(this, NovelReaderActivity::class.java).putExtra(EXTRA_SLUG, slug).putExtra(EXTRA_BOOK_NAME, bookName)
                                .putExtra(EXTRA_VOLUME_ID, item.getString("volumeId")).putExtra(EXTRA_VOLUME_NAME, item.optString("volumeName")).putExtra("bookmark_y", item.optInt("y")))
                            finish()
                        }
                    }.show()
            }.show()
    }

    override fun onDestroy() { scope.cancel(); webView.destroy(); super.onDestroy() }

    companion object {
        const val EXTRA_SLUG = "novel_slug"; const val EXTRA_BOOK_NAME = "novel_book_name"
        const val EXTRA_VOLUME_ID = "novel_volume_id"; const val EXTRA_VOLUME_NAME = "novel_volume_name"
    }
}
