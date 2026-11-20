package com.jieqi.assistant.vision

import android.graphics.Bitmap
import com.jieqi.assistant.core.*

/**
 * P2：棋盘识别器——按 ROI 的 9x10 格点裁剪单格图，送 ONNX 识别，组装棋盘。
 * 用逐格 dHash 增量缓存：未变的格子复用上次结果，减少推理次数。
 */
class BoardRecognizer(
    private val recognizer: PieceRecognizer,
    private var roi: RoiConfig
) {
    data class Result(val board: BoardModel, val suspects: Int, val inferred: Int)

    private val cellHashes = Array(Const.ROWS) { arrayOfNulls<Int>(Const.COLS) }
    private val cellCodes = Array(Const.ROWS) { CharArray(Const.COLS) { '.' } }
    private val half = 0.42f // 单格裁剪半径系数（相对格距）

    fun updateRoi(r: RoiConfig) { roi = r }

    fun recognize(frame: Bitmap): Result {
        val board = BoardModel()
        var suspects = 0
        var inferred = 0
        val sx = frame.width.toFloat() / roi.width.coerceAtLeast(1)
        val sy = frame.height.toFloat() / roi.height.coerceAtLeast(1)
        val colStep = if (roi.xs.size > 1) (roi.xs[1] - roi.xs[0]) else roi.width / 9
        val rowStep = if (roi.ys.size > 1) (roi.ys[1] - roi.ys[0]) else roi.height / 10
        val rad = (minOf(colStep, rowStep) * half).toInt().coerceAtLeast(8)

        for (r in 0 until Const.ROWS) {
            for (c in 0 until Const.COLS) {
                val cx = (roi.xs[c] * sx).toInt()
                val cy = (roi.ys[r] * sy).toInt()
                val x0 = (cx - rad).coerceAtLeast(0)
                val y0 = (cy - rad).coerceAtLeast(0)
                val x1 = (cx + rad).coerceAtMost(frame.width - 1)
                val y1 = (cy + rad).coerceAtMost(frame.height - 1)
                if (x1 <= x0 || y1 <= y0) { board.set(r, c, Piece('.')); continue }

                val cell = Bitmap.createBitmap(frame, x0, y0, x1 - x0, y1 - y0)
                val hash = hashOf(cell)
                var code = cellCodes[r][c]
                if (cellHashes[r][c] != hash) {
                    val res = recognizer.recognize(cell)
                    if (res != null && res.conf >= Const.CONFIDENCE_MIN && res.code != '.' && res.code != '-') {
                        code = res.code
                    } else if (res != null && (res.code == '.' || res.code == '-') && res.conf >= Const.CONFIDENCE_MIN) {
                        code = '.'
                    } else {
                        suspects++
                        // 存疑：宁可不推荐也不给错建议 -> 空格
                        code = '.'
                    }
                    cellHashes[r][c] = hash
                    cellCodes[r][c] = code
                } else inferred++
                cell.recycle()
                board.set(r, c, Piece(code))
            }
        }
        return Result(board, suspects, inferred)
    }

    private fun hashOf(bmp: Bitmap): Int {
        val h = StableFrameDetector.dHash(bmp, 8)
        var v = 0
        for (i in h.indices) if (h[i]) v = v or (1 shl (i % 31))
        return v
    }

    fun resetCache() {
        for (r in 0 until Const.ROWS) for (c in 0 until Const.COLS) cellHashes[r][c] = null
    }
}
