package top.fumiama.copymangaweb.web

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.fumiama.copymangaweb.R
import top.fumiama.copymangaweb.tool.SiteConfig
import top.fumiama.copymangaweb.activity.MainActivity.Companion.wm

class WebViewClient(private val context: Context, private val jsFileName: String):WebViewClient() {
    private val js = context.assets.open(jsFileName).readBytes().decodeToString()
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d("MyWC", "Load URL: $url")
        url?.let {
            if (jsFileName == "i.js") wm?.get()?.onVisiblePageStarted(it)
            if(!SiteConfig.isAllowed(context, it)){
                view?.goBack()
                Toast.makeText(context, R.string.blocked_ad, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (jsFileName == "i.js" && url != null) wm?.get()?.onVisiblePageStarted(url)
        wm?.get()?.lifecycleScope?.launch {
            withContext(Dispatchers.IO) {
                delay(500)
                withContext(Dispatchers.Main) {
                    view?.loadUrl(js)
                    Log.d("MyWC", "Inject JS into: $url")
                    super.onPageFinished(view, url)
                }
            }
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.requestHeaders?.set("Access-Control-Allow-Origin", "*")
        return super.shouldInterceptRequest(view, request)
    }
}
