package com.jieqi.assistant.engine

import com.jieqi.assistant.core.*

/**
 * 走法分析：优先引擎 bestmove；引擎不可用时按揭棋开局位置生成合法兜底走法。
 * 揭棋暗子按"所在初始位置对应的棋子类型"走（兵位前进、车位直走、马位走日…）。
 */
class MoveAnalyzer(private val engine: UciEngine?) {

    data class Candidate(val uci: String, val notation: String, val score: Float)

    /** ourSide: 'r'/'b'；flip = 是否黑方视角（引擎坐标翻转） */
    fun analyze(board: BoardModel, pool: DarkPool, ourSide: Char, flip: Boolean): List<Candidate> {
        val fen = JieqiFen.build(board, pool, ourSide, flip)
        if (engine != null && engine.ready) {
            val bm = engine.bestmove(fen)
            if (bm != null && bm.length >= 4) {
                val mv = Move.fromUci(bm, flip)
                if (mv != null) {
                    val from = board.get(mv.fromRow, mv.fromCol)
                    val notation = Notation.cn(board, mv, from)
                    return listOf(Candidate(bm, notation, 1f))
                }
            }
        }
        // 兜底：按位置生成前3个合法走法
        return fallback(board, ourSide)
    }

    private fun fallback(board: BoardModel, ourSide: Char): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (r in 0 until Const.ROWS) {
            for (c in 0 until Const.COLS) {
                val p = board.get(r, c)
                if (p.isEmpty) continue
                val mine = if (ourSide == 'r') p.isRed else !p.isRed && !p.isDark
                // 揭棋我方只能 active 已揭开的明子；暗子首次需"揭子"(走一步即翻)
                if (!mine && !p.isDark) continue
                val kind = if (p.isDark) positionKind(r, c) else p.code.uppercaseChar()
                val steps = pseudoMoves(kind, r, c, ourSide, board)
                for ((tr, tc) in steps.take(3)) {
                    val mv = Move(r, c, tr, tc)
                    val flip = ourSide == 'b'
                    val uci = mv.toUci(flip)
                    out.add(Candidate(uci, Notation.cn(board, mv, p), 0.5f))
                }
            }
        }
        return out.shuffled().take(3)
    }

    /** 暗子按开局位置对应的棋子类型 */
    private fun positionKind(row: Int, col: Int): Char {
        // 红方初始阵型：row0=车马相仕将仕相马车；row2=炮(c1,c7)；row3=兵(偶数列)
        // 黑方镜像：row9/row7/row6
        return when (row) {
            0, 9 -> when (col) { 0, 8 -> 'R'; 1, 7 -> 'N'; 2, 6 -> 'B'; 3, 5 -> 'A'; 4 -> 'K'; else -> 'P' }
            2, 7 -> if (col == 1 || col == 7) 'C' else 'P'
            3, 6 -> 'P'
            else -> 'P'
        }
    }

    /** 简化伪合法走法（不含蹩马腿/塞象眼细节，仅兜底用） */
    private fun pseudoMoves(kind: Char, r: Int, c: Int, side: Char, board: BoardModel): List<Pair<Int, Int>> {
        val dir = if (side == 'r') -1 else 1 // 红向上(row减小)
        val res = mutableListOf<Pair<Int, Int>>()
        fun add(rr: Int, cc: Int) {
            if (rr !in 0 until Const.ROWS || cc !in 0 until Const.COLS) return
            val t = board.get(rr, cc)
            if (t.isEmpty || if (side == 'r') !t.isRed else !(!t.isRed && !t.isDark)) res.add(rr to cc)
        }
        when (kind) {
            'P' -> { add(r + dir, c); if (r == (if (side == 'r') 3 else 6)) { add(r, c - 1); add(r, c + 1) } }
            'R' -> { for (d in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) for (k in 1..8) add(r + d.first * k, c + d.second * k) }
            'C' -> { for (d in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) for (k in 1..8) add(r + d.first * k, c + d.second * k) }
            'N' -> { for (d in listOf(-2 to -1, -2 to 1, 2 to -1, 2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2)) add(r + d.first, c + d.second) }
            'B' -> { for (d in listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2)) add(r + d.first, c + d.second) }
            'A' -> { for (d in listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) add(r + d.first, c + d.second) }
            'K' -> { for (d in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) add(r + d.first, c + d.second) }
        }
        return res
    }
}
