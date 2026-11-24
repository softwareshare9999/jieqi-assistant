package com.jieqi.assistant.core

import android.content.Context
import com.jieqi.assistant.R

/** 记谱：文案取自资源，默认越南语，中文见 values-zh。如「Pháo 2 bình 5」「炮二平五」 */
object Notation {

    fun format(ctx: Context, board: BoardModel, mv: Move, piece: Piece): String {
        val red = piece.isRed
        val isDark = piece.isDark
        val name = ctx.getString(pieceNameRes(piece.code))
        val prefix = if (isDark) ctx.getString(R.string.notation_dark, name) else name
        val digits = ctx.resources.getStringArray(R.array.notation_red_digits)
        fun fileNum(col: Int): String = if (red) digits.getOrElse(9 - col) { (col + 1).toString() } else (col + 1).toString()
        val fromFile = fileNum(mv.fromCol)
        val toFile = fileNum(mv.toCol)
        val action = when {
            mv.fromRow == mv.toRow -> R.string.notation_flat
            (mv.toRow - mv.fromRow) * (if (red) -1 else 1) > 0 -> R.string.notation_forward
            else -> R.string.notation_backward
        }
        return ctx.getString(R.string.notation_format, prefix, fromFile, ctx.getString(action), toFile)
    }

    private fun pieceNameRes(code: Char): Int = when (code.uppercaseChar()) {
        'R' -> R.string.piece_r
        'N' -> R.string.piece_n
        'B' -> R.string.piece_b
        'A' -> R.string.piece_a
        'C' -> R.string.piece_c
        'P' -> R.string.piece_p
        'K' -> R.string.piece_k
        else -> R.string.piece_unknown
    }
}
