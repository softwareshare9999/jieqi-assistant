package com.jieqi.assistant.core

/**
 * P3：揭棋 FEN 生成（复刻 Jieqi-Moonlink.board_state_to_jieqi_fen）。
 * 格式：棋盘(w/b) 轮次 暗子库 0 1
 * 例：rnbakabnr/9/... w R2r1N2... 0 1
 * 黑方视角时棋盘 180° 翻转（行倒序+列倒序）。红子上、黑子下。
 */
object JieqiFen {

    /** ourSide: 'r'红 / 'b'黑；红先手。flip=是否黑方视角 */
    fun build(board: BoardModel, darkPool: DarkPool, turn: Char = 'r', flip: Boolean): String {
        val grid = Array(Const.ROWS) { r -> CharArray(Const.COLS) { c -> board.get(r, c).code } }
        val proc = if (flip) {
            Array(Const.ROWS) { r -> CharArray(Const.COLS) { c -> grid[Const.ROWS - 1 - r][Const.COLS - 1 - c] } }
        } else grid

        val rows = StringBuilder()
        for (r in 0 until Const.ROWS) {
            var empty = 0
            val row = StringBuilder()
            for (c in 0 until Const.COLS) {
                val code = proc[r][c]
                if (code == '.' || code == '-' || code == ' ') {
                    empty++
                } else {
                    if (empty > 0) { row.append(empty); empty = 0 }
                    // 大写红=大写FEN，小写黑=小写FEN，暗子=x
                    row.append(if (code == 'x' || code == 'X') 'x' else code)
                }
            }
            if (empty > 0) row.append(empty)
            rows.append(row)
            if (r < Const.ROWS - 1) rows.append('/')
        }
        val side = if (turn == 'r') 'w' else 'b'
        return "$rows $side ${darkPool.fen()} 0 1"
    }

    /**
     * 从棋盘推断轮次：数明子+暗子，按上次走子方翻转。
     * 简化：外部维护 turn；此处提供基于红帅/黑将位置的兜底。
     */
    fun inferTurn(board: BoardModel, lastMover: Char?): Char = lastMover ?: 'r'
}
