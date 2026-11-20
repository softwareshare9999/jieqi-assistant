package com.jieqi.assistant.core

/** 中文记谱，如「炮二平五」「暗车九进一」 */
object Notation {
    private val CN_NUM = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val NAME = mapOf(
        'R' to "车", 'N' to "马", 'B' to "相", 'A' to "仕",
        'C' to "炮", 'P' to "兵", 'K' to "帅", 'x' to "暗", 'X' to "暗"
    )

    /** 红方列号从左到右=九..一；黑方=1..9（用阿拉伯数字简化黑方） */
    private fun fileNum(col: Int, red: Boolean): String = if (red) CN_NUM[9 - col] else (col + 1).toString()

    fun cn(board: BoardModel, mv: Move, piece: Piece): String {
        val red = piece.isRed
        val isDark = piece.isDark
        val name = NAME[if (isDark) 'x' else piece.code.uppercaseChar()] ?: "子"
        val prefix = if (isDark) "暗$name" else name
        val fromFile = fileNum(mv.fromCol, red)
        return when {
            mv.fromRow == mv.toRow -> "$prefix$fromFile平${fileNum(mv.toCol, red)}"
            (mv.toRow - mv.fromRow) * (if (red) -1 else 1) > 0 -> "$prefix$fromFile进${fileNum(mv.toCol, red)}"
            else -> "$prefix$fromFile退${fileNum(mv.toCol, red)}"
        }
    }
}
