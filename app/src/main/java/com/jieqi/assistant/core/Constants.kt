package com.jieqi.assistant.core

/**
 * 全局常量：所有可调阈值集中于此，便于按机型微调。
 * 参数来源：Jieqi-Moonlink 与 YoungerIOS/chess-helper-app 参考实现。
 */
object Const {

    // ==================== 识别 ====================
    /** 置信度阈值：低于此值判为存疑，宁可不推荐也不给错建议 */
    const val CONFIDENCE_MIN = 0.85f

    /** ONNX 输入尺寸（模型固定 80x80） */
    const val MODEL_INPUT_SIZE = 80

    /** 模型类别映射（jj_piece_model.onnx，16类），索引->棋子码 */
    val CLASS_MAP = mapOf(
        0 to "a", 1 to "b", 2 to "c", 3 to "k", 4 to "n", 5 to "p", 6 to "r",
        7 to "-", 8 to ".", 9 to "A", 10 to "B", 11 to "C", 12 to "K", 13 to "N", 14 to "P", 15 to "R"
    )

    // ==================== 稳定帧门控 (dHash) ====================
    const val DHASH_SIZE = 20           // dHash 位宽
    const val DHASH_STABLE_COUNT = 2    // 连续 N 帧一致才确认
    const val DHASH_DIFF_THRESHOLD = 5  // 汉明距离 <=5 视为同一帧

    /** 高频刷新间隔（刷悬浮窗显示） */
    const val UI_REFRESH_MS = 150L
    /** 截图轮询间隔（低频喂稳定帧检测） */
    const val CAPTURE_INTERVAL_MS = 180L

    // ==================== ROI 网格 ====================
    /** 9列 x 10行交叉点 */
    const val COLS = 9
    const val ROWS = 10

    // ==================== 走法/引擎 ====================
    const val ENGINE_THINK_MS = 2000    // go movetime
    const val CLICK_INTERVAL_MS = 500L  // 起点->终点点击间隔
    const val ENGINE_ASSET = "engine/pikajie"
    const val NNUE_ASSET = "engine/pikafish.nnue"

    // ==================== 棋盘方向 ====================
    /** 我方永远在下方；true=初始截图红方在上（按需翻转） */
    const val BOARD_RED_ON_TOP = true

    // ==================== 暗子库初始计数 ====================
    // 每方：车2 马2 相2 士2 炮2 兵5 = 15（将帅不涉及暗子）
    val DARK_POOL_INIT = mapOf(
        'R' to 2, 'N' to 2, 'B' to 2, 'A' to 2, 'C' to 2, 'P' to 5
    )
}
