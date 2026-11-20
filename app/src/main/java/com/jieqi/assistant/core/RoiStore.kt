package com.jieqi.assistant.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ROI 配置：棋盘框选矩形 + 9x10 格点像素坐标。
 * 格点坐标由 ROI 均分得到：x[i]=left+i*w/9, y[j]=top+j*h/10。
 */
data class RoiConfig(
    val left: Int, val top: Int, val width: Int, val height: Int,
    val xs: IntArray, val ys: IntArray
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("left", left); o.put("top", top); o.put("width", width); o.put("height", height)
        val xa = JSONArray(); xs.forEach { xa.put(it) }; o.put("xs", xa)
        val ya = JSONArray(); ys.forEach { ya.put(it) }; o.put("ys", ya)
        return o.toString()
    }

    companion object {
        private const val PREF = "roi_cfg"
        private const val KEY = "roi"

        /** 由矩形生成均分格点 */
        fun fromRect(l: Int, t: Int, w: Int, h: Int): RoiConfig {
            val xs = IntArray(Const.COLS) { l + (it.toFloat() * w / (Const.COLS - 1)).toInt() }
            val ys = IntArray(Const.ROWS) { t + (it.toFloat() * h / (Const.ROWS - 1)).toInt() }
            return RoiConfig(l, t, w, h, xs, ys)
        }

        fun load(ctx: Context): RoiConfig? {
            val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
            return try {
                val o = JSONObject(s)
                val xa = o.getJSONArray("xs"); val ya = o.getJSONArray("ys")
                RoiConfig(
                    o.getInt("left"), o.getInt("top"), o.getInt("width"), o.getInt("height"),
                    IntArray(xa.length()) { xa.getInt(it) },
                    IntArray(ya.length()) { ya.getInt(it) }
                )
            } catch (e: Throwable) { null }
        }

        fun save(ctx: Context, cfg: RoiConfig) {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, cfg.toJson()).apply()
        }
    }
}
