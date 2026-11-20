package com.jieqi.assistant.vision

import android.graphics.Bitmap
import com.jieqi.assistant.core.Const
import java.util.ArrayDeque

/**
 * P2：dHash 稳定帧门控（复刻 chess-helper-app.StableFrameDetector）。
 * 计算整图差分哈希，连续 N 帧汉明距离 <= 阈值才确认新局面，过滤走子动画中间帧。
 */
class StableFrameDetector(
    private val hashSize: Int = Const.DHASH_SIZE,
    private val stableCount: Int = Const.DHASH_STABLE_COUNT,
    private val diffThreshold: Int = Const.DHASH_DIFF_THRESHOLD
) {
    private var prevHash: BooleanArray? = null
    private val buffer = ArrayDeque<BooleanArray>()
    private var lastConfirmed: BooleanArray? = null

    companion object {
        /** 计算 dHash：灰度缩小到 (size+1)x size，逐像素比较左右邻 */
        fun dHash(bmp: Bitmap, size: Int): BooleanArray {
            val small = Bitmap.createScaledBitmap(bmp, size + 1, size, true)
            val px = IntArray((size + 1) * size)
            small.getPixels(px, 0, size + 1, 0, 0, size + 1, size)
            val out = BooleanArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val a = px[y * (size + 1) + x]
                    val b = px[y * (size + 1) + x + 1]
                    val ga = (a shr 16 and 0xFF) * 299 + (a shr 8 and 0xFF) * 587 + (a and 0xFF) * 114
                    val gb = (b shr 16 and 0xFF) * 299 + (b shr 8 and 0xFF) * 587 + (b and 0xFF) * 114
                    out[y * size + x] = ga > gb
                }
            }
            small.recycle()
            return out
        }

        fun dist(a: BooleanArray, b: BooleanArray): Int {
            if (a.size != b.size) return Int.MAX_VALUE
            var d = 0
            for (i in a.indices) if (a[i] != b[i]) d++
            return d
        }
    }

    /** 返回 true=检测到稳定的新局面（应触发识别）；false=还在变化或重复帧 */
    fun update(frame: Bitmap, force: Boolean = false): Boolean {
        val curr = dHash(frame, hashSize)
        if (prevHash != null) {
            if (dist(prevHash!!, curr) <= diffThreshold) {
                buffer.addLast(curr)
            } else {
                buffer.clear(); buffer.addLast(curr)
            }
        } else buffer.addLast(curr)
        prevHash = curr

        while (buffer.size > stableCount) buffer.removeFirst()
        if (buffer.size == stableCount && buffer.all { dist(buffer.first(), it) <= diffThreshold }) {
            val isNew = lastConfirmed == null || dist(lastConfirmed!!, curr) > diffThreshold
            if (force || isNew) {
                lastConfirmed = curr
                return true
            }
        }
        return false
    }

    fun reset() { prevHash = null; buffer.clear(); lastConfirmed = null }
}
