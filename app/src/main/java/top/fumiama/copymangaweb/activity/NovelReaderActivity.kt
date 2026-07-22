package top.fumiama.copymangaweb.activity

import android.app.Activity
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
        root.addView(toolbar)
        progress = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = android.view.Gravity.CENTER })
        textView = TextView(this).apply { setPadding(42, 32, 42, 72); setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(textView); visibility = View.GONE }
        webView = WebView(this).apply { visibility = View.GONE; settings.javaScriptEnabled = false; settings.builtInZoomControls = true; settings.displayZoomControls = false }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
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
            val savedY = NovelReadingStore(this@NovelReaderActivity).find(slug)?.takeIf { it.volumeId == volumeId }?.scrollY ?: 0
            if (isHtml) {
                webView.visibility = View.VISIBLE
                loadedHtml = metadata.address to body
                loadStyledHtml()
                webView.postDelayed({ webView.scrollTo(0, savedY) }, 600)
            } else {
                scroll.visibility = View.VISIBLE
                textView.text = body.replace("\r\n", "\n")
                scroll.post { scroll.scrollTo(0, savedY) }
            }
        }.onFailure { progress.visibility = View.GONE; Toast.makeText(this@NovelReaderActivity, "載入正文失敗：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun adjustNumber(key: String, delta: Float, min: Float, max: Float) {
        val fallback = if (key == "novelFontSize") 19f else 1.5f
        val value = ((properties[key].toFloatOrNull() ?: fallback) + delta).coerceIn(min, max)
        properties[key] = value.toString(); applyAppearance()
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
        for (index in 0 until toolbar.childCount) {
            (toolbar.getChildAt(index) as? Button)?.apply {
                setTextColor(foreground)
                backgroundTintList = ColorStateList.valueOf(controlBackground)
            }
        }
        if (webView.visibility == View.VISIBLE) loadStyledHtml()
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
        NovelReadingStore(this).save(NovelReadingRecord(slug, bookName, volumeId, volumeName, y, System.currentTimeMillis()))
    }

    override fun onDestroy() { scope.cancel(); webView.destroy(); super.onDestroy() }

    companion object {
        const val EXTRA_SLUG = "novel_slug"; const val EXTRA_BOOK_NAME = "novel_book_name"
        const val EXTRA_VOLUME_ID = "novel_volume_id"; const val EXTRA_VOLUME_NAME = "novel_volume_name"
    }
}
