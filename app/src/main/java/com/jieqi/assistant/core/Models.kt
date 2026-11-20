package com.jieqi.assistant.core

/**
 * 数据模型：棋子/棋盘/走法/暗子库。
 * 棋子码约定：R/N/B/A/C/P/K=红车马相仕炮兵帅，小写=黑，x=暗子，'.'=空。
 * 来源：Jieqi-Moonlink 的 piece_names 与 FEN 生成规则。
 */

data class Piece(val code: Char) {
    val isEmpty: Boolean get() = code == '.' || code == '-' || code == ' '
    val isDark: Boolean get() = code == 'x' || code == 'X'
    val isRed: Boolean get() = code.isUpperCase() && !isDark
    /** FEN 字符：暗子统一 x */
    val fenChar: Char get() = if (isDark) 'x' else code
}

/** 棋盘 10行x9列，board[row][col] */
class BoardModel {
    private val b = Array(Const.ROWS) { Array(Const.COLS) { Piece('.') } }

    fun get(row: Int, col: Int): Piece = if (row in 0 until Const.ROWS && col in 0 until Const.COLS) b[row][col] else Piece('-')
    fun set(row: Int, col: Int, p: Piece) { if (row in 0 until Const.ROWS && col in 0 until Const.COLS) b[row][col] = p }
    fun clear() { for (r in 0 until Const.ROWS) for (c in 0 until Const.COLS) b[r][c] = Piece('.') }

    fun countPieces(): Int = b.sumOf { row -> row.count { !it.isEmpty } }
    fun countDark(): Int = b.sumOf { row -> row.count { it.isDark } }
    fun countRed(): Int = b.sumOf { row -> row.count { it.isRed } }

    fun snapshot(): Array<CharArray> = Array(Const.ROWS) { r -> CharArray(Const.COLS) { c -> b[r][c].code } }

    /** 深拷贝 */
    fun copy(): BoardModel {
        val n = BoardModel()
        for (r in 0 until Const.ROWS) for (c in 0 until Const.COLS) n.b[r][c] = b[r][c]
        return n
    }

    fun fingerprint(): String = buildString {
        for (r in 0 until Const.ROWS) for (c in 0 until Const.COLS) append(b[r][c].code)
    }
}

/** 走法：起点(row,col)->终点(row,col)，揭示子reveal(可选，第5位) */
data class Move(val fromRow: Int, val fromCol: Int, val toRow: Int, val toCol: Int, val reveal: Char = ' ') {
    /** UCI 字符串（红方视角，行翻转）；黑方由调用方翻转 */
    fun toUci(flip: Boolean): String {
        val cols = "abcdefghi"
        fun sq(r: Int, c: Int) = "${cols[if (flip) 8 - c else c]}${if (flip) r else 9 - r}"
        return sq(fromRow, fromCol) + sq(toRow, toCol) + (if (reveal != ' ') reveal else "")
    }

    companion object {
        /** 解析5位UCI：h2e2N */
        fun fromUci(uci: String, flip: Boolean): Move? {
            if (uci.length < 4) return null
            val cols = "abcdefghi"
            fun parse(sq: String): Pair<Int, Int>? {
                val c0 = cols.indexOf(sq[0]); if (c0 < 0 || sq[1] !in '0'..'9') return null
                val rr = sq[1] - '0'
                return if (flip) (rr to 8 - c0) else (9 - rr to c0)
            }
            val (fr, fc) = parse(uci.substring(0, 2)) ?: return null
            val (tr, tc) = parse(uci.substring(2, 4)) ?: return null
            val rev = if (uci.length >= 5) uci[4] else ' '
            return Move(fr, fc, tr, tc, rev)
        }
    }
}

/** 暗子库：每方各子剩余计数 */
class DarkPool {
    private val red = Const.DARK_POOL_INIT.toMutableMap()
    private val black = Const.DARK_POOL_INIT.toMutableMap()

    fun reset() {
        red.clear(); red.putAll(Const.DARK_POOL_INIT)
        black.clear(); black.putAll(Const.DARK_POOL_INIT)
    }

    /** 翻开一个明子后减一（code 大写=红，小写=黑） */
    fun reveal(code: Char) {
        val upper = code.uppercaseChar()
        if (upper !in Const.DARK_POOL_INIT) return
        if (code.isUpperCase()) red[upper] = (red[upper] ?: 0).coerceAtLeast(0) - 1
        else black[upper] = (black[upper] ?: 0).coerceAtLeast(0) - 1
    }

    /** 根据棋盘明子重算暗子库（Moonlink.update_library_from_board） */
    fun rebuildFrom(board: BoardModel) {
        reset()
        for (r in 0 until Const.ROWS) for (c in 0 until Const.COLS) {
            val p = board.get(r, c)
            if (!p.isEmpty && !p.isDark && p.code.uppercaseChar() in Const.DARK_POOL_INIT) reveal(p.code)
        }
    }

    /** FEN 第3段：R2r1N2... 顺序 RrNnBbAaCcPp */
    fun fen(): String {
        val order = listOf('R' to red, 'r' to black, 'N' to red, 'n' to black,
            'B' to red, 'b' to black, 'A' to red, 'a' to black,
            'C' to red, 'c' to black, 'P' to red, 'p' to black)
        return buildString {
            for ((ch, pool) in order) {
                val n = pool[ch.uppercaseChar()] ?: 0
                if (n > 0) append(ch).append(n)
            }
        }
    }

    fun summary(): String = "池红${red.values.sum()} 黑${black.values.sum()}"
}
