package top.fumiama.copymangaweb.tool

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

object InsetsTools {
    fun applySafeContentInsets(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val caption = insets.getInsets(WindowInsetsCompat.Type.captionBar())
            view.updatePadding(
                left = left + max(bars.left, cutout.left),
                top = top + maxOf(bars.top, cutout.top, caption.top),
                right = right + max(bars.right, cutout.right),
                bottom = bottom + max(bars.bottom, cutout.bottom)
            )
            insets
        }
        if (ViewCompat.isAttachedToWindow(root)) ViewCompat.requestApplyInsets(root)
        else root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }

    // Inset reader content so it does not slip under the status / navigation
    // bars or the display cutout. Optional top/bottom bars get the matching
    // single-edge padding.
    fun applyReaderContentInsets(activity: Activity, content: View, topBar: View? = null, bottomBar: View? = null) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val left = max(bars.left, cutout.left)
            val top = maxOf(bars.top, cutout.top)
            val right = max(bars.right, cutout.right)
            val bottom = max(bars.bottom, cutout.bottom)
            view.updatePadding(left, top, right, bottom)
            topBar?.updatePadding(left, top, right, 0)
            bottomBar?.updatePadding(left, 0, right, bottom)
            insets
        }
        if (ViewCompat.isAttachedToWindow(content)) ViewCompat.requestApplyInsets(content)
        else content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }

    // Counterpart of [applyReaderContentInsets]: drop the inset padding so the
    // views return to an edge-to-edge (immersive) layout.
    fun clearReaderContentInsets(activity: Activity, content: View, topBar: View? = null, bottomBar: View? = null) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        content.updatePadding(0, 0, 0, 0)
        topBar?.updatePadding(0, 0, 0, 0)
        bottomBar?.updatePadding(0, 0, 0, 0)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            view.updatePadding(0, 0, 0, 0)
            topBar?.updatePadding(0, 0, 0, 0)
            bottomBar?.updatePadding(0, 0, 0, 0)
            insets
        }
    }

    fun immersiveSystemUiFlags(): Int =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
}
