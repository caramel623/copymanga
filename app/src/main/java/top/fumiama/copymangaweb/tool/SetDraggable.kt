package top.fumiama.copymangaweb.tool

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class SetDraggable {
    private var screenWidth = 0
    private var screenHeight = 0
    fun with(context: Context): SetDraggable {
        val dm = context.resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        return this
    }

    @SuppressLint("ClickableViewAccessibility")
    fun onto(target: View) {
        var lastX = 0
        var lastY = 0
        var firstX = 0
        var firstY = 0
        var baseLeft = 0f
        var baseTop = 0f
        target.post { target.setOnTouchListener { v: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    firstX = lastX
                    firstY = lastY
                    baseLeft = v.left.toFloat()
                    baseTop = v.top.toFloat()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - lastX
                    val dy = event.rawY.toInt() - lastY
                    var left = baseLeft + v.translationX + dx
                    var top = baseTop + v.translationY + dy
                    if (left < 0) left = 0f
                    if (top < 0) top = 0f
                    if (left + v.width > screenWidth) left = (screenWidth - v.width).toFloat()
                    if (top + v.height > screenHeight) top = (screenHeight - v.height).toFloat()
                    // 用 translationX/Y 而非 v.layout：ConstraintLayout 重排（例如進度文字變動）
                    // 只會重置約束基礎位置、保留位移，拖曳位置不會被重排拉回
                    v.translationX = left - baseLeft
                    v.translationY = top - baseTop
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                }
            }
            abs(firstX - lastX) > 3 || abs(firstY - lastY) > 3      // 移动微小则判断为点击
        } }
    }
}