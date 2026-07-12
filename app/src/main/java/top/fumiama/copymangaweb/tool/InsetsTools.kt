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
}
