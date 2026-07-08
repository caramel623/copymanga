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

        add(binding.quality, "quality", arrayOf("原圖", "高 1500px", "中 1000px", "省流 750px"), arrayOf("0", "1500", "1000", "750"), "1500")
        add(binding.preload, "preload", arrayOf("1 頁", "2 頁", "4 頁", "6 頁"), arrayOf("1", "2", "4", "6"), "2")
        add(binding.retry, "retry", arrayOf("不重試", "1 次", "2 次", "3 次"), arrayOf("0", "1", "2", "3"), "1")
        add(binding.cache, "cache", arrayOf("開啟", "關閉"), arrayOf("true", "false"), "true")
        add(binding.direction, "r2l", arrayOf("由右至左", "由左至右"), arrayOf("true", "false"), "true")
        add(binding.displayMode, "noAnimation", arrayOf("單頁無動畫", "滑頁動畫"), arrayOf("true", "false"), "true")
        add(binding.pageOrientation, "vertical", arrayOf("上下滑頁", "左右滑頁"), arrayOf("true", "false"), "true")
        add(binding.ranobeTraditional, "ranobeTraditional", arrayOf("開啟：簡體轉繁體", "關閉：保留原文"), arrayOf("true", "false"), "true")
        add(binding.loadSpeed, "loadSpeed", arrayOf("慢", "標準", "快"), arrayOf("160", "320", "640"), "320")
        add(binding.downloadBatchSize, "downloadBatchSize", arrayOf("1 張", "2 張", "3 張", "4 張", "5 張"), arrayOf("1", "2", "3", "4", "5"), "5")
        add(binding.compressZip, "compressZip", arrayOf("每個章節壓縮成單一 ZIP", "儲存為未壓縮圖片"), arrayOf("true", "false"), "true")
        add(binding.bookrackSortField, "bookrackSortField", arrayOf("作品更新時間", "加入書架時間", "閱讀時間"), arrayOf("update", "added", "read"), "update")
        add(binding.bookrackSortDirection, "bookrackSortDirection", arrayOf("由新到舊", "由舊到新"), arrayOf("desc", "asc"), "desc")
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
            properties["siteUrl"] = siteUrl
            fields.forEach { (key, spinner, values) -> properties[key] = values[spinner.selectedItemPosition] }
            Toast.makeText(this, "設定已儲存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DOWNLOAD_FOLDER_REQUEST || resultCode != RESULT_OK) return
        data?.data?.let { uri ->
            contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            properties["downloadTreeUri"] = uri.toString()
            updateDownloadLocationText()
        }
    }

    private fun updateDownloadLocationText() {
        val uri = properties["downloadTreeUri"]
        binding.downloadLocation.text = if (uri == "null") "應用程式預設資料夾" else Uri.parse(uri).toString()
    }

    private fun add(spinner: Spinner, key: String, labels: Array<String>, values: Array<String>, default: String) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val current = properties[key].takeUnless { it == "null" } ?: default
        spinner.setSelection(values.indexOf(current).coerceAtLeast(0))
        fields += Triple(key, spinner, values)
    }

    companion object {
        private const val DOWNLOAD_FOLDER_REQUEST = 41
    }
}
