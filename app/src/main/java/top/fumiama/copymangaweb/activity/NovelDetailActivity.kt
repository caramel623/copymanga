package top.fumiama.copymangaweb.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import kotlinx.coroutines.*
import top.fumiama.copymangaweb.data.NovelRepository
import top.fumiama.copymangaweb.tool.NovelReadingStore
import top.fumiama.copymangaweb.tool.NovelShelfRecord
import top.fumiama.copymangaweb.tool.NovelShelfStore

class NovelDetailActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var shelfButton: Button
    private val slug by lazy { intent.getStringExtra(EXTRA_SLUG).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "輕小說"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 28) }
        progress = ProgressBar(this).apply { isIndeterminate = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        if (slug.isBlank()) { toast("無法辨識小說網址"); finish(); return }
        load()
    }

    private fun load() = scope.launch {
        runCatching { withContext(Dispatchers.IO) { NovelRepository(this@NovelDetailActivity).let { it.loadBook(slug) to it.loadVolumes(slug) } } }
            .onSuccess { (book, volumes) ->
                progress.visibility = android.view.View.GONE
                content.addView(TextView(this@NovelDetailActivity).apply { text = book.name; textSize = 25f })
                shelfButton = Button(this@NovelDetailActivity).apply {
                    updateShelfButton(this, book.slug)
                    setOnClickListener {
                        val shelf = NovelShelfStore(this@NovelDetailActivity)
                        if (shelf.contains(book.slug)) shelf.remove(book.slug)
                        else shelf.add(NovelShelfRecord(book.slug, book.name, book.lastUpdated, System.currentTimeMillis()))
                        updateShelfButton(this, book.slug)
                    }
                }
                content.addView(shelfButton)
                content.addView(TextView(this@NovelDetailActivity).apply { text = listOf(book.author, book.brief).filter { it.isNotBlank() }.joinToString("\n\n"); textSize = 15f; setPadding(0, 16, 0, 24) })
                val saved = NovelReadingStore(this@NovelDetailActivity).find(slug)
                volumes.forEach { volume ->
                    content.addView(Button(this@NovelDetailActivity).apply {
                        text = if (saved?.volumeId == volume.id) "${volume.name}　（上次閱讀）" else volume.name
                        setOnClickListener { openReader(book.name, volume.id, volume.name) }
                    })
                }
            }.onFailure { progress.visibility = android.view.View.GONE; toast("載入小說目錄失敗：${it.message}") }
    }

    private fun openReader(bookName: String, volumeId: String, volumeName: String) {
        startActivity(Intent(this, NovelReaderActivity::class.java).apply {
            putExtra(NovelReaderActivity.EXTRA_SLUG, slug); putExtra(NovelReaderActivity.EXTRA_BOOK_NAME, bookName)
            putExtra(NovelReaderActivity.EXTRA_VOLUME_ID, volumeId); putExtra(NovelReaderActivity.EXTRA_VOLUME_NAME, volumeName)
        })
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun updateShelfButton(button: Button, bookSlug: String) {
        button.text = if (NovelShelfStore(this).contains(bookSlug)) "已加入本地書架（點擊移除）" else "＋ 加入本地書架"
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object { const val EXTRA_SLUG = "novel_slug" }
}
