package top.fumiama.copymangaweb.activity

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.activity.MainActivity.Companion.wm
import top.fumiama.copymangaweb.activity.template.ToolsBoxActivity
import top.fumiama.copymangaweb.databinding.ActivityViewmangaBinding
import top.fumiama.copymangaweb.handler.MainHandler
import top.fumiama.copymangaweb.handler.TimeThread
import top.fumiama.copymangaweb.tool.PropertiesTools
import top.fumiama.copymangaweb.tool.PagesManager
import top.fumiama.copymangaweb.tool.ToolsBox
import top.fumiama.copymangaweb.view.ScaleImageView
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class ViewMangaActivity : ToolsBoxActivity() {
    lateinit var handler: Handler
    lateinit var tt: TimeThread
    lateinit var mBinding: ActivityViewmangaBinding

    var count = 0
    var clicked = false
    var r2l = true
    var infoDrawerDelta = 0f

    private var dialog: Dialog? = null
    private lateinit var p: PropertiesTools
    private var isInSeek = false
    private var currentItem = 0
    private var notUseVP = true
    private var onlineAdapter: RecyclerView.Adapter<*>? = null
    private var readerPrepared = false
    private var mangaZip = zipFile
    val dlZip2View = mangaZip != null
    private val volTurnPage get() = p["volturn"] == "true"
    private val quality get() = p["quality"].toIntOrNull() ?: 1500
    private val preload get() = p["preload"].toIntOrNull() ?: 3
    private val retry get() = p["retry"].toIntOrNull() ?: 1
    private val useCache get() = p["cache"] != "false"
    private val verticalReading get() = p["vertical"] == "true"
    var pageNum = 1
        get() {
            field = getPageNumber()
            return field
        }
        set(value) {
            setPageNumber(value)
            if (notUseVP) {
                //currentItem += delta
                try {
                    loadOneImg()
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    toolsBox.toastError("页数${currentItem}不合法")
                }
            }// else vp.currentItem += delta
            field = getPageNumber()
        }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityViewmangaBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        va = WeakReference(this)
        p = PropertiesTools(File("$filesDir/settings.properties"))
        applyReaderAppearance()
        r2l = p["r2l"] == "true"
        notUseVP = p["noAnimation"] == "true" && !verticalReading
        handler = MyHandler(toolsBox)
        tt = TimeThread(handler, 22)
        tt.canDo = true
        tt.start()
        if (dlZip2View) {
            dialog = Dialog(this)
            dialog?.apply {
                setContentView(R.layout.dialog_unzipping)
                show()
            }
        }
        mBinding.oneinfo.inftitle.ttitle.apply { post { text = titleText } }
        Log.d("MyVM", "dlZip2View: $dlZip2View, mangaZip: $mangaZip")
        if(dlZip2View && mangaZip?.exists() != true) toolsBox.toastError("已经到头了~")
        else Thread {
            try {
                count = if (dlZip2View) countZipItems() else imgUrls.size
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { toolsBox.toastError("分析图片url错误") }
            }
            runOnUiThread {
                try {
                    if (!dlZip2View && count == 0) mBinding.readerWaiting.visibility = View.VISIBLE
                    else prepareReaderItems()
                } catch (e: Exception) {
                    e.printStackTrace()
                    toolsBox.toastError("准备控件错误")
                } finally {
                    dialog?.dismiss()
                    dialog = null
                }
            }
        }.start()
    }

    private fun applyReaderAppearance() {
        val dark = p["webDarkMode"] == "true"
        val background = if (dark) Color.rgb(7, 21, 34) else Color.WHITE
        val panel = if (dark) Color.rgb(13, 34, 56) else Color.WHITE
        val foreground = if (dark) Color.rgb(241, 245, 249) else Color.BLACK
        val controlTint = ColorStateList.valueOf(
            if (dark) Color.rgb(20, 48, 77) else Color.WHITE
        )

        listOf(mBinding.vcp, mBinding.vone.root, mBinding.vp, mBinding.vcontinuous).forEach {
            it.setBackgroundColor(background)
        }
        mBinding.oneinfo.inftitle.titleCard.setCardBackgroundColor(panel)
        mBinding.oneinfo.inftitle.ttitle.setTextColor(foreground)
        mBinding.oneinfo.inftitle.isearch.setColorFilter(foreground)
        mBinding.oneinfo.infoProgress.backgroundTintList = ColorStateList.valueOf(panel)
        mBinding.oneinfo.inftxtprogress.setTextColor(foreground)
        mBinding.infcard.idc.setCardBackgroundColor(panel)
        mBinding.infcard.idtime.setTextColor(foreground)
        listOf(
            mBinding.infcard.idtbvolturn,
            mBinding.infcard.idtbvh,
            mBinding.infcard.idtbvp,
            mBinding.infcard.idtblr
        ).forEach {
            it.setTextColor(foreground)
            it.backgroundTintList = controlTint
        }
        listOf(mBinding.continuousPrevious, mBinding.continuousNext).forEach {
            it.setTextColor(foreground)
            it.backgroundTintList = controlTint
        }

        window.statusBarColor = background
        window.navigationBarColor = background
        var systemUi = window.decorView.systemUiVisibility
        systemUi = if (dark) systemUi and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        else systemUi or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUi = if (dark) systemUi and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            else systemUi or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = systemUi
    }

    private fun prepareReaderItems() {
        if (readerPrepared || count <= 0) return
        prepareItems()
        readerPrepared = true
        mBinding.readerWaiting.visibility = View.GONE
        if(pn > 0) {
            pageNum = pn
            pn = -1
        } else if(pn == -2){
            pageNum = count
            pn = -1
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        var flag = false
        if(volTurnPage) when(keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                scrollBack()
                flag = true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                scrollForward()
                flag = true
            }
        }
        return if(flag) true else super.onKeyDown(keyCode, event)
    }

    private fun getPageNumber(): Int {
        if (verticalReading && !notUseVP) {
            val manager = mBinding.vcontinuous.layoutManager as? LinearLayoutManager
            return ((manager?.findFirstVisibleItemPosition() ?: 1) - 1).coerceAtLeast(0) + 1
        }
        return if (r2l && !notUseVP) count - mBinding.vp.currentItem
        else (if (notUseVP) currentItem else mBinding.vp.currentItem) + 1
    }

    private fun setPageNumber(num: Int) {
        if (verticalReading && !notUseVP) {
            mBinding.vcontinuous.apply { post { scrollToPosition((num - 1).coerceIn(0, (count - 1).coerceAtLeast(0))) } }
            return
        }
        if (r2l && !notUseVP) mBinding.vp.apply { post { currentItem = count - num } }
        else if (notUseVP) currentItem = num - 1 else mBinding.vp.currentItem = num - 1
    }

    private fun getImgBitmap(position: Int): Bitmap? {
        if (position >= count || position < 0) return null
        else {
            val zip = ZipFile(mangaZip)
            val entry = zip.getEntry("%03d.JPG".format(position + 1))
                ?: zip.getEntry("${position}.webp")
                ?: zip.getEntry("${position}.JPG")
                ?: return null
            return BitmapFactory.decodeStream(zip.getInputStream(entry))
        }
    }

    private fun loadOneImg() {
        if(dlZip2View) mBinding.vone.onei.apply { post { setImageBitmap(getImgBitmap(currentItem)) } }
        else loadOnlineImage(imgUrls[currentItem], mBinding.vone.onei)
        updateSeekBar()
    }

    private fun loadOnlineImage(url: String, target: ScaleImageView) {
        val imageUrl = toolsBox.resolution.wrap(url, quality)
        var request = Glide.with(this).load(imageUrl)
            .diskCacheStrategy(if (useCache) DiskCacheStrategy.AUTOMATIC else DiskCacheStrategy.NONE)
            .skipMemoryCache(!useCache).placeholder(R.drawable.ic_dl).dontAnimate().timeout(10000)
        repeat(retry.coerceIn(0, 3)) { request = request.error(Glide.with(this).load(imageUrl)) }
        request.into(target)
    }

    private fun setIdPosition(position: Int) {
        infoDrawerDelta = position.toFloat()
        mBinding.infcard.root.apply { post { translationY = infoDrawerDelta } }
    }

    @SuppressLint("SetTextI18n")
    private fun prepareItems() {
        prepareVP()
        prepareInfoBar(count)
        if (notUseVP) loadOneImg() else prepareIdBtVH()
        toolsBox.dp2px(67)?.let { setIdPosition(it) }
        prepareIdBtVolTurn()
        prepareIdBtVP()
        prepareIdBtLR()
    }

    private fun prepareIdBtLR() {
        mBinding.infcard.idtblr.apply { post {
            isChecked = r2l
            setOnClickListener {
                if (mBinding.infcard.idtblr.isChecked) p["r2l"] = "true"
                else p["r2l"] = "false"
                Toast.makeText(this@ViewMangaActivity, "下次浏览生效", Toast.LENGTH_SHORT).show()
            }
        } }
    }

    private fun prepareIdBtVP() {
        mBinding.infcard.idtbvp.apply { post {
            isChecked = notUseVP
            setOnClickListener {
                if (mBinding.infcard.idtbvp.isChecked) p["noAnimation"] = "true"
                else p["noAnimation"] = "false"
                Toast.makeText(this@ViewMangaActivity, "下次浏览生效", Toast.LENGTH_SHORT).show()
            }
        } }
    }

    private fun prepareVP() {
        mBinding.vp.apply { post {
            orientation = if (verticalReading) ViewPager2.ORIENTATION_VERTICAL else ViewPager2.ORIENTATION_HORIZONTAL
        } }
        if (notUseVP) {
            mBinding.vp.apply { post { visibility = View.INVISIBLE } }
            mBinding.vcontinuous.apply { post { visibility = View.INVISIBLE } }
            mBinding.vone.root.apply { post { visibility = View.VISIBLE } }
        } else if (verticalReading) {
            mBinding.vp.apply { post { visibility = View.INVISIBLE } }
            mBinding.vone.root.apply { post { visibility = View.INVISIBLE } }
            mBinding.vcontinuous.apply { post {
                visibility = View.VISIBLE
                setPadding(0, 0, 0, toolsBox.dp2px(56) ?: 56)
                clipToPadding = true
                layoutManager = LinearLayoutManager(this@ViewMangaActivity)
                setItemViewCacheSize(preload.coerceIn(1, 10))
                adapter = ContinuousViewData(this).RecyclerViewAdapter().also { onlineAdapter = it }
                clearOnScrollListeners()
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        updateSeekBar()
                        super.onScrolled(recyclerView, dx, dy)
                    }
                })
            } }
        } else {
            mBinding.vp.apply { post {
                visibility = View.VISIBLE
                orientation = if (verticalReading) ViewPager2.ORIENTATION_VERTICAL else ViewPager2.ORIENTATION_HORIZONTAL
                offscreenPageLimit = preload.coerceIn(1, 10)
                adapter = ViewData(this).RecyclerViewAdapter().also { onlineAdapter = it }
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updateSeekBar()
                        super.onPageSelected(position)
                    }
                })
                if (r2l) currentItem = count - 1
            } }
            mBinding.vone.root.apply { post { visibility = View.INVISIBLE } }
            mBinding.vcontinuous.apply { post { visibility = View.INVISIBLE } }
        }
    }

    private fun updateSeekBar() {
        if (!isInSeek) hideSettings()
        updateSeekText()
        updateSeekProgress()
    }

    @SuppressLint("SetTextI18n")
    private fun prepareInfoBar(size: Int) {
        mBinding.oneinfo.root.apply { post { alpha = 0F } }
        mBinding.oneinfo.infseek.apply { post {
            visibility = View.INVISIBLE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, p1: Int, isHuman: Boolean) {
                    if (isHuman) {
                        val target = ((p1 * count) / 100).coerceIn(1, count.coerceAtLeast(1))
                        if (target != pageNum) setPageNumber(target)
                    }
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {
                    isInSeek = true
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                    isInSeek = false
                }
            })
        } }
        mBinding.oneinfo.inftitle.isearch.apply { post {
            visibility = View.INVISIBLE
            setOnClickListener {
                this@ViewMangaActivity.handler.sendEmptyMessage(3)
            }
        } }
        mBinding.oneinfo.inftxtprogress.apply { post { text = "$pageNum/$size" } }
    }

    private fun prepareIdBtVH() {
        mBinding.infcard.idtbvh.apply { post {
            isChecked = verticalReading
            setOnClickListener {
                if (mBinding.infcard.idtbvh.isChecked) {
                    mBinding.vp.apply { post { orientation = ViewPager2.ORIENTATION_VERTICAL } }
                    p["vertical"] = "true"
                    p["noAnimation"] = "false"
                } else {
                    mBinding.vp.apply { post { orientation = ViewPager2.ORIENTATION_HORIZONTAL } }
                    p["vertical"] = "false"
                }
                Toast.makeText(this@ViewMangaActivity, "下次浏览生效", Toast.LENGTH_SHORT).show()
            }
        } }
    }

    private fun prepareIdBtVolTurn() {
        mBinding.infcard.idtbvolturn.apply { post {
            isChecked = volTurnPage
            setOnClickListener {
                if (mBinding.infcard.idtbvolturn.isChecked) p["volturn"] = "true"
                else p["volturn"] = "false"
            }
        } }
    }

    private fun countZipItems(): Int {
        var c = 0
        try {
            val exist = mangaZip?.exists() == true
            if (!exist) return 0
            else {
                Log.d("Myvm", "zipf: $mangaZip")
                ZipFile(mangaZip).use { zip ->
                    c = zip.size()
                }
            }
        } catch (e: Exception) {
            runOnUiThread { toolsBox.toastError("读取zip错误!") }
        }
        return c
    }

    fun scrollBack() {
        pageNum--
    }

    fun scrollForward() {
        pageNum++
    }

    fun openOnlineChapter(chapterUrl: String, goNext: Boolean) {
        val main = wm?.get() ?: return
        val comicTitle = titleText.substringBeforeLast(" - ", titleText)
        if (!main.prepareAdjacentChapterInReader(chapterUrl, "$comicTitle - 載入中")) return
        if (!goNext) pn = -2
        tt.canDo = false
        startActivity(Intent(this, ViewMangaActivity::class.java))
        overridePendingTransition(0, 0)
        main.loadHiddenUrl(chapterUrl)
        finish()
        overridePendingTransition(0, 0)
    }

    fun openChapterFromBoundary(goNext: Boolean) {
        val chapterUrl = if (goNext) nextChapterUrl else previousChapterUrl
        if (chapterUrl != null) {
            openOnlineChapter(chapterUrl, goNext)
            return
        }
        val newPosition = zipPosition + if (goNext) 1 else -1
        if (dlZip2View && newPosition >= 0 && newPosition < (zipList?.size ?: 0)) {
            if (!goNext) pn = -2
            zipPosition = newPosition
            titleText = zipList?.get(newPosition) ?: "null"
            zipFile = File(cd, titleText)
            tt.canDo = false
            startActivity(Intent(this, ViewMangaActivity::class.java))
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSeekText() {
        mBinding.oneinfo.inftxtprogress.apply { post { text = "$pageNum/$count" } }
    }

    private fun updateSeekProgress() {
        mBinding.oneinfo.infseek.apply { post { progress = if (count > 0) pageNum * 100 / count else 0 } }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        tt.canDo = false
        wm?.get()?.let { main ->
            main.visibleWebView().stopLoading()
            // Reader startup can leave the hidden scanner running while the
            // remaining image URLs are collected.  It must not remain visible
            // after returning to the chapter selection page.
            main.mBinding.wh.stopLoading()
            MainActivity.mh?.sendEmptyMessage(MainHandler.HIDE_LOADING_DIALOG)
            main.returnToChapterSelection()
        }
        finish()
    }

    override fun onDestroy() {
        tt.canDo = false
        handler.removeCallbacksAndMessages(null)
        if (va?.get() === this) va = null
        super.onDestroy()
    }

    inner class ViewData(itemView: View) : RecyclerView.ViewHolder(itemView) {
        inner class RecyclerViewAdapter :
            RecyclerView.Adapter<ViewData>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewData {
                return ViewData(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.page_imgview, parent, false)
                )
            }

            @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
            override fun onBindViewHolder(holder: ViewData, position: Int) {
                val pos = if (r2l) count - position - 1 else position
                holder.itemView.findViewById<ScaleImageView>(R.id.onei)?.let { oneImage ->
                    if(dlZip2View) getImgBitmap(pos)?.let {
                        //Glide.with(this@ViewMangaActivity).load(it).placeholder(R.drawable.bg_comment).into(holder.itemView.onei)
                        oneImage.setImageBitmap(it)
                    }
                    else loadOnlineImage(imgUrls[pos], oneImage)
                }
            }

            override fun getItemCount(): Int {
                return count
            }
        }
    }

    inner class ContinuousViewData(itemView: View) : RecyclerView.ViewHolder(itemView) {
        inner class RecyclerViewAdapter : RecyclerView.Adapter<ContinuousViewData>() {
            private val header = 0
            private val image = 1
            private val footer = 2
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContinuousViewData {
                if (viewType != image) {
                    val button = android.widget.Button(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        setPadding(0, 12, 0, 12)
                        minHeight = (48 * resources.displayMetrics.density).toInt()
                        if (p["webDarkMode"] == "true") {
                            setTextColor(Color.rgb(241, 245, 249))
                            backgroundTintList = ColorStateList.valueOf(Color.rgb(20, 48, 77))
                        }
                    }
                    return ContinuousViewData(button)
                }
                return ContinuousViewData(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.page_imgview_continuous, parent, false)
                )
            }

            override fun onBindViewHolder(holder: ContinuousViewData, position: Int) {
                if (position == 0 || position == count + 1) {
                    val goNext = position == count + 1
                    val available = if (goNext) nextChapterUrl != null || (dlZip2View && zipPosition + 1 < (zipList?.size ?: 0))
                    else previousChapterUrl != null || (dlZip2View && zipPosition > 0)
                    (holder.itemView as android.widget.Button).apply {
                        if (goNext) {
                            minHeight = (48 * resources.displayMetrics.density).toInt()
                            setPadding(0, 12, 0, 12)
                            elevation = (8 * resources.displayMetrics.density)
                        }
                        text = if (available) { if (goNext) "下一章節" else "上一章節" } else { if (goNext) "已到結尾" else "已到開頭" }
                        isEnabled = available
                        setOnClickListener { this@ViewMangaActivity.openChapterFromBoundary(goNext) }
                    }
                    return
                }
                val imagePosition = position - 1
                holder.itemView.findViewById<ScaleImageView>(R.id.onei)?.let { oneImage ->
                    if (dlZip2View) getImgBitmap(imagePosition)?.let { oneImage.setImageBitmap(it) }
                    else {
                        loadOnlineImage(imgUrls[imagePosition], oneImage)
                        for (offset in 1..preload.coerceIn(1, 10)) {
                            val next = imagePosition + offset
                            if (next < imgUrls.size) Glide.with(this@ViewMangaActivity)
                                .load(toolsBox.resolution.wrap(imgUrls[next], quality))
                                .diskCacheStrategy(if (useCache) DiskCacheStrategy.AUTOMATIC else DiskCacheStrategy.NONE)
                                .skipMemoryCache(!useCache)
                                .preload()
                        }
                    }
                }
            }

            override fun getItemViewType(position: Int): Int = when (position) {
                0 -> header
                count + 1 -> footer
                else -> image
            }

            override fun getItemCount(): Int = count + 2
        }
    }

    fun showSettings() {
        mBinding.oneinfo.infseek.visibility = View.VISIBLE
        mBinding.oneinfo.inftitle.isearch.visibility = View.VISIBLE
        val v = mBinding.oneinfo.root
        ObjectAnimator.ofFloat(
            v,
            "alpha",
            v.alpha,
            1F
        ).setDuration(233).start()
        clicked = true
    }

    fun hideSettings() {
        val v = mBinding.oneinfo.root
        ObjectAnimator.ofFloat(
            v,
            "alpha",
            v.alpha,
            0F
        ).setDuration(233).start()
        clicked = false
        mBinding.oneinfo.infseek.postDelayed({
            mBinding.oneinfo.infseek.visibility = View.INVISIBLE
            mBinding.oneinfo.inftitle.isearch.visibility = View.INVISIBLE
        }, 300)
        handler.sendEmptyMessage(1)
    }

    class MyHandler(
        private val toolsBox: ToolsBox
    ) : Handler(Looper.myLooper()!!) {
        private var infoShown = false
        private var delta = -1f
            get() {
                if (field < 0) field = va?.get()?.infoDrawerDelta ?: 0f
                return field
            }

        @SuppressLint("SimpleDateFormat", "SetTextI18n")
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                1 -> if (infoShown) {
                    hideInfCard(); infoShown = false
                }
                2 -> if (!infoShown) {
                    showInfCard(); infoShown = true
                }
                3 -> infoShown = if (infoShown) {
                    hideInfCard(); false
                } else {
                    showInfCard(); true
                }
                22 -> (toolsBox.zis as? ViewMangaActivity)?.mBinding?.infcard?.idtime?.apply { post {
                    text = SimpleDateFormat("HH:mm")
                        .format(Date()) + toolsBox.week + toolsBox.netInfo
                } }
            }
        }

        private fun showInfCard() {
            Log.d("MyVM", "showInfCard delta $delta")
            va?.get()?.mBinding?.infcard?.apply {
                ObjectAnimator.ofFloat(idc, "alpha", 0.3F, 0.8F).setDuration(233).start()
                ObjectAnimator.ofFloat(root, "translationY", delta, 0F).setDuration(233).start()
            }
        }

        private fun hideInfCard() {
            Log.d("MyVM", "hideInfCard delta $delta")
            va?.get()?.mBinding?.infcard?.apply {
                ObjectAnimator.ofFloat(idc, "alpha", 0.8F, 0.3F).setDuration(233).start()
                ObjectAnimator.ofFloat(root, "translationY", 0F, delta).setDuration(233).start()
            }
        }
    }

    companion object {
        var va: WeakReference<ViewMangaActivity>? = null
        var imgUrls = arrayOf<String>()
        var zipFile: File? = null
        get() {
            val re = field
            if(field != null) field = null
            return re
        }
        var titleText = "Null"
        var nextChapterUrl: String? = null
        var previousChapterUrl: String? = null
        var zipPosition = 0
        var zipList: Array<String>? = null
        var cd: File? = null
        var pn = -1

        @Synchronized
        fun updateOnlineChapterHeader(title: String, nextUrl: String?, previousUrl: String?) {
            titleText = title
            nextChapterUrl = nextUrl
            previousChapterUrl = previousUrl
            va?.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.runOnUiThread {
                va?.get()?.mBinding?.oneinfo?.inftitle?.ttitle?.text = title
            }
        }

        @Synchronized
        fun appendOnlineImages(urls: Array<String>, finished: Boolean) {
            if (urls.isEmpty()) return
            val start = imgUrls.size
            imgUrls += urls
            Log.d("MyVM", "Online images collected: ${imgUrls.size}, finished=$finished")
            va?.get()?.runOnUiThread {
                va?.get()?.apply {
                    count = imgUrls.size
                    if (!readerPrepared) prepareReaderItems()
                    else if (r2l) onlineAdapter?.notifyDataSetChanged()
                    else onlineAdapter?.notifyItemRangeInserted(
                        if (verticalReading) start + 1 else start,
                        urls.size
                    )
                    updateSeekText()
                    if (finished) Log.d("MyVM", "All streamed images loaded: $count")
                }
            }
        }

    }
}
