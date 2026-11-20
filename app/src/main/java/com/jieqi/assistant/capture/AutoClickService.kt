package com.jieqi.assistant.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureDescription
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.jieqi.assistant.core.Const
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P6：无障碍服务——dispatchGesture 自动落子（Android 唯一可靠的自动点击方式）。
 * 只响应外部显式调用 performMove，不在事件回调里自作主张。
 */
class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        @Volatile var instance: AutoClickService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 不主动触发 */ }
    override fun onInterrupt() {}

    /**
     * 执行一步走子：点击起点 -> 延时 -> 点击终点。
     * 坐标为屏幕物理像素（display 空间）。
     */
    fun performMove(sx: Float, sy: Float, ex: Float, ey: Float, onDone: ((Boolean) -> Unit)? = null) {
        if (!busy.compareAndSet(false, true)) { onDone?.invoke(false); return }
        tap(sx, sy) { ok1 ->
            if (!ok1) { busy.set(false); onDone?.invoke(false); return@tap }
            main.postDelayed({
                tap(ex, ey) { ok2 ->
                    busy.set(false)
                    onDone?.invoke(ok2)
                }
            }, Const.CLICK_INTERVAL_MS)
        }
    }

    private fun tap(x: Float, y: Float, cb: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                main.post { cb(true) }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                main.post { cb(false) }
            }
        }, main)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
