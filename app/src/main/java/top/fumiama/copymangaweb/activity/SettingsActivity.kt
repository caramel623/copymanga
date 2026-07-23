package top.fumiama.copymangaweb.activity

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.databinding.ActivitySettingsBinding
import top.fumiama.copymangaweb.tool.PropertiesTools
import top.fumiama.copymangaweb.tool.SiteConfig
import top.fumiama.copymangaweb.tool.NovelConfig
import top.fumiama.copymangaweb.tool.NovelDataExporter
import top.fumiama.copymangaweb.tool.NovelReadingStore
import top.fumiama.copymangaweb.tool.NovelShelfStore
import java.io.File

class SettingsActivity : Activity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var properties: PropertiesTools
    private val fields = mutableListOf<Triple<String, Spinner, Array<String>>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        properties = PropertiesTools(File("$filesDir/settings.properties"))
        binding.siteUrl.setText(SiteConfig.get(this))
        binding.novelApiUrl.setText(NovelConfig.get(this))
        binding.openNovelLibrary.setOnClickListener {
            startActivity(Intent(this, NovelLibraryActivity::class.java))
        }
        binding.exportNovelData.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, NovelDataExporter.suggestedFileName())
                },
                EXPORT_NOVEL_DATA_REQUEST
            )
        }
        binding.importNovelData.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                },
                IMPORT_NOVEL_DATA_REQUEST
            )
        }

        add(binding.webDarkMode, "webDarkMode", arrayOf("開啟：黑底白字", "關閉：網站原始配色"), arrayOf("true", "false"), "false")
        add(binding.quality, "quality", arrayOf("原圖", "高 1500px", "中 1000px", "省流 750px"), arrayOf("0", "1500", "1000", "750"), "1500")
        add(binding.preload, "preload", arrayOf("1 張", "3 張", "5 張", "8 張"), arrayOf("1", "3", "5", "8"), "3")
        add(binding.retry, "retry", arrayOf("不重試", "1 次", "2 次", "3 次"), arrayOf("0", "1", "2", "3"), "1")
        add(binding.cache, "cache", arrayOf("開啟", "關閉"), arrayOf("true", "false"), "true")
        add(binding.direction, "r2l", arrayOf("由右至左", "由左至右"), arrayOf("true", "false"), "true")
        add(binding.displayMode, "noAnimation", arrayOf("單頁無動畫", "滑頁動畫"), arrayOf("true", "false"), "true")
        add(binding.pageOrientation, "vertical", arrayOf("上下滑頁", "左右滑頁"), arrayOf("true", "false"), "true")
        add(
            binding.novelTheme,
            "novelTheme",
            arrayOf("淺色：米白底深色字", "深色：深灰底淺色字", "黑色：純黑底灰白字"),
            arrayOf("light", "dark", "black"),
            "light"
        )
        add(binding.ranobeTraditional, "ranobeTraditional", arrayOf("開啟：簡體轉繁體", "關閉：保留原文"), arrayOf("true", "false"), "true")
        add(binding.loadSpeed, "loadSpeed", arrayOf("慢", "標準", "快"), arrayOf("160", "320", "640"), "320")
        add(binding.downloadBatchSize, "downloadBatchSize", arrayOf("1 張", "2 張", "3 張", "4 張", "5 張"), arrayOf("1", "2", "3", "4", "5"), "5")
        add(binding.compressZip, "compressZip", arrayOf("每個章節壓縮成單一 ZIP", "儲存為未壓縮圖片"), arrayOf("true", "false"), "true")
        updateDownloadLocationText()
        binding.selectDownloadLocation.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            ), DOWNLOAD_FOLDER_REQUEST)
        }

        binding.save.setOnClickListener {
            val siteUrl = SiteConfig.normalize(binding.siteUrl.text.toString())
            if (siteUrl == null) {
                binding.siteUrl.error = "請輸入有效的 http 或 https 網址"
                return@setOnClickListener
            }
            val novelApiUrl = NovelConfig.normalize(binding.novelApiUrl.text.toString())
            if (novelApiUrl == null) {
                binding.novelApiUrl.error = "請輸入有效的 http 或 https 網址"
                return@setOnClickListener
            }
            properties["siteUrl"] = siteUrl
            properties["novelApiUrl"] = novelApiUrl
            fields.forEach { (key, spinner, values) -> properties[key] = values[spinner.selectedItemPosition] }
            if (properties["vertical"] == "true") properties["noAnimation"] = "false"
            Toast.makeText(this, "設定已儲存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT_NOVEL_DATA_REQUEST) {
            if (resultCode == RESULT_OK) data?.data?.let(::exportNovelData)
            return
        }
        if (requestCode == IMPORT_NOVEL_DATA_REQUEST) {
            if (resultCode == RESULT_OK) data?.data?.let(::importNovelData)
            return
        }
        if (requestCode != DOWNLOAD_FOLDER_REQUEST || resultCode != RESULT_OK) return
        data?.data?.let { uri ->
            contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            properties["downloadTreeUri"] = uri.toString()
            updateDownloadLocationText()
        }
    }

    private fun exportNovelData(uri: Uri) {
        val json = NovelDataExporter.buildJson(
            NovelShelfStore(this).list(),
            NovelReadingStore(this).list()
        )
        runCatching {
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(json)
            } ?: error("無法開啟輸出檔案")
        }.onSuccess {
            Toast.makeText(this, "小說清單與閱讀歷史已匯出", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "匯出失敗：${it.message ?: "無法寫入檔案"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importNovelData(uri: Uri) {
        runCatching {
            val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("無法開啟匯入檔案")
            NovelDataExporter.parseJson(json)
        }.onSuccess { imported ->
            val shelfStore = NovelShelfStore(this)
            val readingStore = NovelReadingStore(this)
            imported.shelf.asReversed().forEach(shelfStore::add)
            imported.readingHistory.asReversed().forEach(readingStore::save)
            Toast.makeText(
                this,
                "已匯入 ${imported.shelf.size} 本小說、${imported.readingHistory.size} 筆閱讀歷史",
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(this, "匯入失敗：${it.message ?: "檔案格式錯誤"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateDownloadLocationText() {
        val uri = properties["downloadTreeUri"]
        binding.downloadLocation.text = if (uri == "null") "應用程式預設資料夾" else Uri.parse(uri).toString()
    }

    private fun add(spinner: Spinner, key: String, labels: Array<String>, values: Array<String>, default: String) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val current = properties[key].takeUnless { it == "null" } ?: default
        spinner.setSelection(values.indexOf(current).coerceAtLeast(0))
        fields += Triple(key, spinner, values)
    }

    companion object {
        private const val DOWNLOAD_FOLDER_REQUEST = 41
        private const val EXPORT_NOVEL_DATA_REQUEST = 42
        private const val IMPORT_NOVEL_DATA_REQUEST = 43
    }
}
