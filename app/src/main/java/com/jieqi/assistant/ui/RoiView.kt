package com.jieqi.assistant.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.jieqi.assistant.core.Const
import com.jieqi.assistant.core.RoiConfig
import kotlin.math.max
import kotlin.math.min

/**
 * 棋盘 ROI 框选 View：拖动四角/四边调整矩形，实时绘制 9x10 格点预览。
 * 背景为半透明遮罩，矩形内透亮。
 */
class RoiView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var cfg: RoiConfig? = null
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700"); strokeWidth = 5f; style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF00"); strokeWidth = 2f; style = Paint.Style.STROKE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081"); style = Paint.Style.FILL
    }
    private val maskPaint = Paint().apply { color = Color.parseColor("#AA000000") }
    private val handleR = 26f
    private var dragCorner = -1

    fun setConfig(c: RoiConfig) { cfg = c; invalidate() }
    fun getConfig(): RoiConfig? = cfg

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = cfg ?: return
        // 四周遮罩
        canvas.drawRect(0f, 0f, width.toFloat(), c.top.toFloat(), maskPaint)
        canvas.drawRect(0f, c.top + c.height.toFloat(), width.toFloat(), height.toFloat(), maskPaint)
        canvas.drawRect(0f, c.top.toFloat(), c.left.toFloat(), c.top + c.height.toFloat(), maskPaint)
        canvas.drawRect((c.left + c.width).toFloat(), c.top.toFloat(), width.toFloat(), c.top + c.height.toFloat(), maskPaint)
        // 外框
        canvas.drawRect(c.left.toFloat(), c.top.toFloat(), (c.left + c.width).toFloat(), (c.top + c.height).toFloat(), linePaint)
        // 9x10 格点
        for (x in c.xs) for (y in c.ys) canvas.drawCircle(x.toFloat(), y.toFloat(), 5f, gridPaint)
        // 四角把手
        val hs = corners(c)
        hs.forEach { canvas.drawCircle(it.first, it.second, handleR, handlePaint) }
    }

    private fun corners(c: RoiConfig) = listOf(
        c.left.toFloat() to c.top.toFloat(),
        (c.left + c.width).toFloat() to c.top.toFloat(),
        (c.left + c.width).toFloat() to (c.top + c.height).toFloat(),
        c.left.toFloat() to (c.top + c.height).toFloat()
    )

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val c = cfg ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragCorner = corners(c).indexOfFirst {
                    Math.hypot(it.first - e.x, it.second - e.y) < handleR * 2
                }
                return dragCorner >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragCorner < 0) return false
                var l = c.left; var t = c.top; var r = c.left + c.width; var b = c.top + c.height
                when (dragCorner) {
                    0 -> { l = e.x.toInt(); t = e.y.toInt() }
                    1 -> { r = e.x.toInt(); t = e.y.toInt() }
                    2 -> { r = e.x.toInt(); b = e.y.toInt() }
                    3 -> { l = e.x.toInt(); b = e.y.toInt() }
                }
                l = l.coerceIn(0, width - 40); r = r.coerceIn(l + 40, width)
                t = t.coerceIn(0, height - 40); b = b.coerceIn(t + 40, height)
                cfg = RoiConfig.fromRect(l, t, r - l, b - t)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> { dragCorner = -1; return true }
        }
        return super.onTouchEvent(e)
    }
}
