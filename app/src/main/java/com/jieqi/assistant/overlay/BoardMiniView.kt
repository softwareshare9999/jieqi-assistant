package com.jieqi.assistant.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.jieqi.assistant.core.Const

/**
 * P5：悬浮窗棋盘缩略图。
 * 用 PNG 贴图画棋子（含暗子背面），不用 drawText；红色箭头标 bestmove。
 * 分层绘制顺序：棋盘线 -> 靶点 -> 箭头 -> 棋子（棋子最后画，防止被箭头裁剪）。
 */
class BoardMiniView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    // 精确四向 margin（参考 chess-helper-app）
    private val marginLeft = 14f; private val marginTop = 11f
    private val marginRight = 14f; private val marginBottom = 14f

    private var grid: Array<CharArray>? = null
    private var moveFrom: Pair<Int, Int>? = null
    private var moveTo: Pair<Int, Int>? = null
    private var ourSide = 'r'

    private val pieceBmps = mutableMapOf<Char, Bitmap>()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E4A"); strokeWidth = 2f
    }
    private val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E4A"); strokeWidth = 1.5f
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2222"); strokeWidth = 6f; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF2222"); style = Paint.Style.FILL }

    init { loadPieces() }

    private fun loadPieces() {
        val names = mapOf(
            'R' to "red_R", 'N' to "red_N", 'B' to "red_B", 'A' to "red_A", 'C' to "red_C", 'P' to "red_P", 'K' to "red_K",
            'r' to "black_r", 'n' to "black_n", 'b' to "black_b", 'a' to "black_a", 'c' to "black_c", 'p' to "black_p", 'k' to "black_k",
            'x' to "dark"
        )
        for ((code, name) in names) {
            try {
                context.assets.open("pics/$name.png").use { pieceBmps[code] = BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) {}
        }
    }

    fun setBoard(g: Array<CharArray>, from: Pair<Int, Int>?, to: Pair<Int, Int>?, side: Char) {
        grid = g; moveFrom = from; moveTo = to; ourSide = side; invalidate()
    }

    override fun onMeasure(wms: Int, hms: Int) {
        val w = MeasureSpec.getSize(wms)
        // 棋盘比例 9:10，加上 margin
        val h = (w * 10f / 9f).toInt() + (marginTop + marginBottom).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = grid ?: return
        val L = marginLeft; val T = marginTop
        val bw = width - marginLeft - marginRight
        val bh = height - marginTop - marginBottom
        val cw = bw / (Const.COLS - 1).toFloat()
        val ch = bh / (Const.ROWS - 1).toFloat()

        // 背景
        canvas.drawRect(L - 8, T - 8, L + bw + 8, T + bh + 8, Paint().apply { color = Color.parseColor("#E8C989") })

        // 棋盘网格（9列10行交叉点，含楚河汉界留白）
        for (c in 0 until Const.COLS) canvas.drawLine(L + c * cw, T, L + c * cw, T + bh, linePaint)
        for (r in 0 until Const.ROWS) {
            if (r == 4 || r == 5) continue // 河界
            canvas.drawLine(L, T + r * ch, L + bw, T + r * ch, linePaint)
        }
        canvas.drawRect(L, T + 4 * ch, L + bw, T + 5 * ch, Paint().apply { color = Color.parseColor("#E8C989") })

        // 位置换算：我方永远在下方。红方在下=不翻转；黑方在下=翻转
        fun px(row: Int, col: Int): Pair<Float, Float> {
            val rr = if (ourSide == 'r') row else Const.ROWS - 1 - row
            val cc = if (ourSide == 'r') col else Const.COLS - 1 - col
            return L + cc * cw to T + rr * ch
        }

        // 箭头（棋子下层）
        if (moveFrom != null && moveTo != null) {
            val (x1, y1) = px(moveFrom!!.first, moveFrom!!.second)
            val (x2, y2) = px(moveTo!!.first, moveTo!!.second)
            canvas.drawLine(x1, y1, x2, y2, arrowPaint)
            val ang = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
            val ah = 22f
            val p = Path()
            p.moveTo(x2, y2)
            p.lineTo((x2 - ah * Math.cos(ang - 0.4)).toFloat(), (y2 - ah * Math.sin(ang - 0.4)).toFloat())
            p.lineTo((x2 - ah * Math.cos(ang + 0.4)).toFloat(), (y2 - ah * Math.sin(ang + 0.4)).toFloat())
            p.close(); canvas.drawPath(p, triPaint)
        }

        // 棋子（PNG 贴图，最后画，防裁剪）
        val size = (minOf(cw, ch) * 0.94f).toInt()
        for (r in 0 until Const.ROWS) for (c in 0 until Const.COLS) {
            val code = g[r][c]
            if (code == '.' || code == '-' || code == ' ') continue
            val bmp = pieceBmps[code] ?: pieceBmps['x'] ?: continue
            val (cx, cy) = px(r, c)
            val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
            canvas.drawBitmap(scaled, cx - size / 2f, cy - size / 2f, null)
        }
    }
}
