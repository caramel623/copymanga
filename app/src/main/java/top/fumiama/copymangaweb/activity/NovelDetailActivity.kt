package top.fumiama.copymangaweb.activity

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.fumiama.copymangaweb.data.NovelBook
import top.fumiama.copymangaweb.data.NovelRepository
import top.fumiama.copymangaweb.data.NovelVolume
import top.fumiama.copymangaweb.tool.NovelReadingStore
import top.fumiama.copymangaweb.tool.NovelShelfRecord
import top.fumiama.copymangaweb.tool.NovelShelfStore
import top.fumiama.copymangaweb.tool.PropertiesTools
import java.io.File

class NovelDetailActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var properties: PropertiesTools
    private lateinit var palette: Palette
    private var appliedTheme = ""
    private var loadedBook: NovelBook? = null
    private var loadedVolumes: List<NovelVolume> = emptyList()
    private val slug by lazy { intent.getStringExtra(EXTRA_SLUG).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        properties = PropertiesTools(File("$filesDir/settings.properties"))
        appliedTheme = themeKey()
        palette = paletteFor(appliedTheme)
        buildPage()
        if (slug.isBlank()) {
            toast("無法辨識小說網址")
            finish()
            return
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (!::properties.isInitialized) return
        val currentTheme = themeKey()
        if (currentTheme != appliedTheme) {
            appliedTheme = currentTheme
            palette = paletteFor(currentTheme)
            applyWindowColors()
            root.setBackgroundColor(palette.background)
            progress.indeterminateTintList = ColorStateList.valueOf(palette.accent)
        }
        loadedBook?.let { render(it, loadedVolumes) }
    }

    private fun buildPage() {
        applyWindowColors()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }
        root.addView(createTopBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(palette.accent)
        }
        val body = FrameLayout(this).apply {
            addView(ScrollView(this@NovelDetailActivity).apply {
                isFillViewport = true
                addView(content)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(progress, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        }
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun createTopBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, dp(4), 0)
        setBackgroundColor(palette.surface)
        elevation = dp(2).toFloat()

        addView(TextView(this@NovelDetailActivity).apply {
            text = "‹"
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(palette.primaryText)
            isClickable = true
            isFocusable = true
            contentDescription = "返回"
            background = selectableBackground(palette.surface, palette.ripple, dp(28).toFloat())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT))

        addView(TextView(this@NovelDetailActivity).apply {
            text = "輕小說"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.primaryText)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        addView(View(this@NovelDetailActivity), LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun load() = scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                NovelRepository(this@NovelDetailActivity).let {
                    it.loadBook(slug) to it.loadVolumes(slug)
                }
            }
        }.onSuccess { (book, volumes) ->
            loadedBook = book
            loadedVolumes = volumes
            progress.visibility = View.GONE
            render(book, volumes)
        }.onFailure {
            progress.visibility = View.GONE
            toast("載入小說目錄失敗：${it.message}")
        }
    }

    private fun render(book: NovelBook, volumes: List<NovelVolume>) {
        content.removeAllViews()
        content.addView(createBookCard(book))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(24), dp(4), dp(10))
            addView(text("目錄", 19f, palette.primaryText, bold = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("共 ${volumes.size} 卷", 13f, palette.secondaryText))
        }
        content.addView(heading)

        val savedVolumeId = NovelReadingStore(this).find(slug)?.volumeId
        if (volumes.isEmpty()) {
            content.addView(text("目前沒有可閱讀的卷冊", 15f, palette.secondaryText).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(36), 0, dp(36))
            })
        } else {
            volumes.forEach { volume ->
                content.addView(
                    createVolumeRow(book.name, volume, savedVolumeId == volume.id),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                )
            }
        }
    }

    private fun createBookCard(book: NovelBook): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBackground(palette.surface, dp(14).toFloat(), palette.outline, dp(1))
        elevation = if (appliedTheme == "light") dp(1).toFloat() else 0f

        addView(text(book.name, 23f, palette.primaryText, bold = true).apply {
            setLineSpacing(0f, 1.08f)
        })
        if (book.author.isNotBlank()) {
            addView(text("作者　${book.author}", 14f, palette.secondaryText).apply {
                setPadding(0, dp(8), 0, 0)
            })
        }

        val actions = LinearLayout(this@NovelDetailActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
            addView(createShelfButton(book))
        }
        addView(actions)

        if (book.brief.isNotBlank()) {
            addView(text("作品簡介", 14f, palette.primaryText, bold = true).apply {
                setPadding(0, dp(18), 0, dp(6))
            })
            addView(text(book.brief, 14f, palette.secondaryText).apply {
                maxLines = 6
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.25f)
            })
        }
    }

    private fun createShelfButton(book: NovelBook): TextView = TextView(this).apply {
        textSize = 14f
        gravity = Gravity.CENTER
        minHeight = dp(38)
        setPadding(dp(16), 0, dp(16), 0)
        isClickable = true
        isFocusable = true
        updateShelfButton(this, book.slug)
        setOnClickListener {
            val shelf = NovelShelfStore(this@NovelDetailActivity)
            if (shelf.contains(book.slug)) shelf.remove(book.slug)
            else shelf.add(NovelShelfRecord(book.slug, book.name, book.lastUpdated, System.currentTimeMillis()))
            updateShelfButton(this, book.slug)
        }
    }

    private fun createVolumeRow(bookName: String, volume: NovelVolume, isLastRead: Boolean): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
            setPadding(dp(16), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            contentDescription = "閱讀 ${volume.name}"
            background = selectableBackground(
                if (isLastRead) palette.selectedSurface else palette.surface,
                palette.ripple,
                dp(12).toFloat(),
                if (isLastRead) palette.accent else palette.outline
            )
            setOnClickListener { openReader(bookName, volume.id, volume.name) }

            addView(text(volume.name, 16f, palette.primaryText).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (isLastRead) {
                addView(text("上次閱讀", 11f, palette.accent, bold = true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(9), dp(4), dp(9), dp(4))
                    background = roundedBackground(palette.accentSoft, dp(12).toFloat())
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8)
                })
            }
            addView(text("›", 28f, palette.secondaryText).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginStart = dp(4)
            })
        }

    private fun openReader(bookName: String, volumeId: String, volumeName: String) {
        startActivity(Intent(this, NovelReaderActivity::class.java).apply {
            putExtra(NovelReaderActivity.EXTRA_SLUG, slug)
            putExtra(NovelReaderActivity.EXTRA_BOOK_NAME, bookName)
            putExtra(NovelReaderActivity.EXTRA_VOLUME_ID, volumeId)
            putExtra(NovelReaderActivity.EXTRA_VOLUME_NAME, volumeName)
        })
    }

    private fun updateShelfButton(button: TextView, bookSlug: String) {
        val inShelf = NovelShelfStore(this).contains(bookSlug)
        button.text = if (inShelf) "✓ 已加入本地書架" else "＋ 加入本地書架"
        button.setTextColor(if (inShelf) palette.accent else palette.buttonText)
        button.background = selectableBackground(
            if (inShelf) palette.accentSoft else palette.accent,
            palette.ripple,
            dp(19).toFloat(),
            palette.accent
        )
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun roundedBackground(fill: Int, radius: Float, stroke: Int? = null, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius
            if (stroke != null && strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun selectableBackground(
        fill: Int,
        ripple: Int,
        radius: Float,
        stroke: Int? = null
    ) = RippleDrawable(
        ColorStateList.valueOf(ripple),
        roundedBackground(fill, radius, stroke, if (stroke == null) 0 else dp(1)),
        null
    )

    private fun themeKey(): String = properties["novelTheme"].takeIf {
        it == "dark" || it == "black"
    } ?: "light"

    private fun applyWindowColors() {
        window.statusBarColor = palette.surface
        window.navigationBarColor = palette.background
        var flags = 0
        if (appliedTheme == "light" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (appliedTheme == "light" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun paletteFor(theme: String): Palette = when (theme) {
        "dark" -> Palette(
            background = Color.rgb(30, 30, 30),
            surface = Color.rgb(43, 43, 43),
            selectedSurface = Color.rgb(36, 55, 73),
            primaryText = Color.rgb(240, 240, 240),
            secondaryText = Color.rgb(178, 178, 178),
            outline = Color.rgb(62, 62, 62),
            accent = Color.rgb(112, 180, 255),
            accentSoft = Color.rgb(42, 66, 91),
            buttonText = Color.WHITE,
            ripple = Color.argb(50, 255, 255, 255)
        )
        "black" -> Palette(
            background = Color.BLACK,
            surface = Color.rgb(13, 13, 13),
            selectedSurface = Color.rgb(16, 36, 54),
            primaryText = Color.rgb(229, 229, 229),
            secondaryText = Color.rgb(150, 150, 150),
            outline = Color.rgb(38, 38, 38),
            accent = Color.rgb(104, 174, 242),
            accentSoft = Color.rgb(19, 47, 72),
            buttonText = Color.WHITE,
            ripple = Color.argb(62, 255, 255, 255)
        )
        else -> Palette(
            background = Color.rgb(247, 248, 250),
            surface = Color.WHITE,
            selectedSurface = Color.rgb(238, 246, 255),
            primaryText = Color.rgb(35, 38, 43),
            secondaryText = Color.rgb(112, 117, 125),
            outline = Color.rgb(229, 232, 236),
            accent = Color.rgb(45, 140, 240),
            accentSoft = Color.rgb(231, 243, 255),
            buttonText = Color.WHITE,
            ripple = Color.argb(38, 45, 140, 240)
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class Palette(
        val background: Int,
        val surface: Int,
        val selectedSurface: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val outline: Int,
        val accent: Int,
        val accentSoft: Int,
        val buttonText: Int,
        val ripple: Int
    )

    companion object {
        const val EXTRA_SLUG = "novel_slug"
    }
}
