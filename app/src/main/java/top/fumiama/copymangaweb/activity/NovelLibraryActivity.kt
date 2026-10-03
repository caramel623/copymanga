package top.fumiama.copymangaweb.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import android.graphics.Typeface
import android.graphics.Color
import android.content.res.Configuration
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.tool.NovelReadingStore
import top.fumiama.copymangaweb.tool.NovelShelfStore

class NovelLibraryActivity : Activity() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val sortPreferences by lazy { getSharedPreferences("novel_library", MODE_PRIVATE) }
    override fun onResume() { super.onResume(); render() }
    override fun onDestroy() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); super.onDestroy() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "本地輕小說書架"
        scope.launchRefresh()
    }

    private fun kotlinx.coroutines.CoroutineScope.launchRefresh() = launch {
        for (record in NovelShelfStore(this@NovelLibraryActivity).list()) {
            val book = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { top.fumiama.copymangaweb.data.NovelRepository(this@NovelLibraryActivity).loadBook(record.slug) }.getOrNull()
            } ?: continue
            NovelShelfStore(this@NovelLibraryActivity).updateMetadata(book.slug, book.name, book.lastUpdated, book.cover)
        }
        render()
    }

    private val sortOptions = arrayOf("updated", "added", "reading")

    private fun currentSortMode(): String {
        val stored = runCatching { sortPreferences.getString("sortMode", null) }.getOrNull()
        if (stored != null && stored in sortOptions) return stored
        return if (sortPreferences.getBoolean("updated", false)) "updated" else "added"
    }

    private fun render() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
            setBackgroundColor(if (night) Color.rgb(18, 18, 18) else Color.WHITE)
        }
        val sortMode = currentSortMode()
        list.addView(Button(this).apply {
            text = when (sortMode) {
                "updated" -> "排序：更新時間 ↓"
                "reading" -> "排序：閱讀時間 ↓"
                else -> "排序：加入書架時間 ↓"
            }
            setTextColor(if (night) Color.rgb(235, 235, 235) else Color.rgb(40, 40, 40))
            setBackgroundColor(if (night) Color.rgb(58, 58, 58) else Color.rgb(222, 226, 230))
            val labels = arrayOf("更新時間", "加入書架時間", "閱讀時間")
            setOnClickListener {
                val builder = if (night) android.app.AlertDialog.Builder(this@NovelLibraryActivity, R.style.DarkAlertTheme)
                    else android.app.AlertDialog.Builder(this@NovelLibraryActivity)
                builder.setTitle("書架排序（最新在前）")
                    .setSingleChoiceItems(labels, sortOptions.indexOf(sortMode)) { dialog, which ->
                        sortPreferences.edit().putString("sortMode", sortOptions[which]).apply(); dialog.dismiss(); render()
                    }.show()
            }
        })
        val records = NovelShelfStore(this).list()
        val progress = NovelReadingStore(this).list().associateBy { it.slug }
        val shelf = when (sortMode) {
            "updated" -> records.sortedWith(compareByDescending<top.fumiama.copymangaweb.tool.NovelShelfRecord> { it.lastUpdated }.thenByDescending { it.addedAt })
            "reading" -> records.sortedWith(compareByDescending<top.fumiama.copymangaweb.tool.NovelShelfRecord> { progress[it.slug]?.updatedAt ?: 0L }.thenByDescending { it.addedAt })
            else -> records
        }
        if (shelf.isEmpty()) list.addView(TextView(this).apply {
            text = "尚未加入任何輕小說"; textSize = 18f; setPadding(16, 40, 16, 40)
            setTextColor(if (night) Color.LTGRAY else Color.DKGRAY)
        })
        shelf.forEachIndexed { index, record ->
            val reading = progress[record.slug]
            list.addView(createRow(
                record.name, reading?.volumeName ?: "尚未閱讀",
                record.lastUpdated.ifBlank { "--" }, false, record.slug, index, night, record.cover
            ))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(if (night) Color.rgb(18, 18, 18) else Color.WHITE)
            addView(list)
        })
    }

    private fun createRow(name: String, volume: String, date: String, header: Boolean, slug: String?, index: Int, night: Boolean, cover: String = ""): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, if (header) 18 else 30, 12, if (header) 18 else 30)
            val rowColor = when {
                header && night -> Color.rgb(58, 58, 58)
                header -> Color.rgb(222, 226, 230)
                night && index % 2 == 0 -> Color.rgb(28, 28, 28)
                night -> Color.rgb(43, 43, 43)
                index % 2 == 0 -> Color.WHITE
                else -> Color.rgb(241, 244, 247)
            }
            val foreground = if (night) Color.rgb(235, 235, 235) else Color.rgb(40, 40, 40)
            setBackgroundColor(rowColor)
            if (!header && slug != null) {
                isClickable = true; isFocusable = true
                setOnClickListener {
                    startActivity(Intent(this@NovelLibraryActivity, NovelDetailActivity::class.java)
                        .putExtra(NovelDetailActivity.EXTRA_SLUG, slug))
                }
            }
            fun cell(value: String, weight: Float) = TextView(this@NovelLibraryActivity).apply {
                text = value; textSize = if (header) 12f else 14f
                setTextColor(foreground)
                if (header) setTypeface(typeface, Typeface.BOLD)
                setPadding(8, 6, 8, 6)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            }
            if (!header) addView(ImageView(this@NovelLibraryActivity).apply {
                contentDescription = "$name 封面"
                scaleType = ImageView.ScaleType.CENTER_CROP
                com.bumptech.glide.Glide.with(this@NovelLibraryActivity).load(cover.ifBlank { null })
                    .placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_menu_gallery).into(this)
            }, LinearLayout.LayoutParams((60 * resources.displayMetrics.density).toInt(), (86 * resources.displayMetrics.density).toInt()))
            addView(LinearLayout(this@NovelLibraryActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 0, 0, 0)
                listOf(name, "上次閱讀：$volume", "更新：$date").forEachIndexed { i, value ->
                    addView(TextView(this@NovelLibraryActivity).apply {
                        text = value; textSize = if (i == 0) 17f else 13f
                        setTextColor(foreground)
                        setPadding(0, 4, 0, 8)
                        if (i == 0) setTypeface(typeface, Typeface.BOLD)
                    })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
}
