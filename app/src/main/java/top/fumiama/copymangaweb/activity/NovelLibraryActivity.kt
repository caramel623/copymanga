package top.fumiama.copymangaweb.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import android.graphics.Typeface
import android.graphics.Color
import android.content.res.Configuration
import top.fumiama.copymangaweb.tool.NovelReadingStore
import top.fumiama.copymangaweb.tool.NovelShelfStore

class NovelLibraryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "本地輕小說書架"
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
            setBackgroundColor(if (night) Color.rgb(18, 18, 18) else Color.WHITE)
        }
        val shelf = NovelShelfStore(this).list()
        val progress = NovelReadingStore(this).list().associateBy { it.slug }
        if (shelf.isEmpty()) list.addView(TextView(this).apply {
            text = "尚未加入任何輕小說"; textSize = 18f; setPadding(16, 40, 16, 40)
            setTextColor(if (night) Color.LTGRAY else Color.DKGRAY)
        })
        else list.addView(createRow("小說名稱", "上次閱讀卷", "最後更新日期", true, null, 0, night))
        shelf.forEachIndexed { index, record ->
            val reading = progress[record.slug]
            list.addView(createRow(
                record.name, reading?.volumeName ?: "尚未閱讀",
                record.lastUpdated.ifBlank { "--" }, false, record.slug, index, night
            ))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(if (night) Color.rgb(18, 18, 18) else Color.WHITE)
            addView(list)
        })
    }

    private fun createRow(name: String, volume: String, date: String, header: Boolean, slug: String?, index: Int, night: Boolean): LinearLayout =
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
            addView(cell(name, 2.8f))
            addView(cell(volume, 1.0f))
            addView(cell(date, 1.4f))
        }
}
