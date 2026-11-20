package com.jieqi.assistant.capture

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.jieqi.assistant.R
import com.jieqi.assistant.core.Const
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * P1：MediaProjection + ImageReader 截图前台服务。
 * 通过 startProjection 注入授权结果；内部循环抓帧，经 FrameSink 回调。
 * 系统投屏授权 semantically 无法静默恢复——每次冷启动需重新授权一次，
 * 但服务存活期间复用同一 MediaProjection，不会重复弹窗。
 */
class ProjectionService : Service() {

    companion object {
        private const val TAG = "ProjectionService"
        const val CHANNEL_ID = "jieqi_projection"
        const val NOTIFY_ID = 2001
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_RESULT_CODE = "code"
        const val EXTRA_RESULT_DATA = "data"

        @Volatile var instance: ProjectionService? = null
    }

    /** 帧回调：bitmap 为 ARGB_8888 截图 */
    interface FrameSink { fun onFrame(bmp: Bitmap) }
    @Volatile var sink: FrameSink? = null

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var vDisplay: VirtualDisplay? = null
    private var display: Display? = null
    private var density = 1
    private var width = 0
    private var height = 0
    private val grabbing = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val runnable = object : Runnable {
        override fun run() {
            grabOnce()
            handler.postDelayed(this, Const.CAPTURE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startForeground(NOTIFY_ID, buildNotification())
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        density = resources.displayMetrics.densityDpi
        width = resources.displayMetrics.widthPixels
        height = resources.displayMetrics.heightPixels
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.extras?.let {
                val code = it.getInt(EXTRA_RESULT_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= 33)
                    it.getParcelable(EXTRA_RESULT_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") it.getParcelable<Intent>(EXTRA_RESULT_DATA)
                if (code != -1 && data != null) startProjection(code, data)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startProjection(code: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        val d = display ?: return
        val realW = d.mode?.physicalWidth ?: width
        val realH = d.mode?.physicalHeight ?: height
        imageReader = ImageReader.newInstance(realW, realH, ImageFormat.RGB_565, 2)
        val surface: Surface = imageReader!!.surface
        vDisplay = projection?.createVirtualDisplay(
            name = "jieqi_capture", width = realW, height = realH, densityDpi = density,
            surface = surface, flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            callback = null, handler = null
        )
        handler.post(runnable)
        Log.d(TAG, "投影已启动 ${realW}x${realH}")
    }

    private fun grabOnce() {
        val reader = imageReader ?: return
        if (!grabbing.compareAndSet(false, true)) return
        val image: Image? = try { reader.acquireLatestImage() } catch (e: Throwable) { null }
        if (image == null) { grabbing.set(false); return }
        try {
            val plane = image.planes[0]
            val buf = plane.buffer
            val w = plane.rowStride / plane.pixelStride
            val h = image.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            bmp.copyPixelsFromBuffer(buf)
            // RGB_565 -> ARGB_8888 供 ONNX 预处理
            val argb = bmp.copy(Bitmap.Config.ARGB_8888, false)
            generation.incrementAndGet()
            sink?.onFrame(argb)
        } catch (e: Throwable) {
            Log.w(TAG, "grab失败: ${e.message}")
        } finally {
            image.close()
            grabbing.set(false)
        }
    }

    fun currentGeneration(): Int = generation.get()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("揭棋助手截图中")
            .setContentText("正在读取棋盘，可在通知栏停止")
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        try { vDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
