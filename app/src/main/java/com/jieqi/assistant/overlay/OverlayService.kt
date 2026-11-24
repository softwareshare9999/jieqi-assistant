package com.jieqi.assistant.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ToggleButton
import androidx.core.app.NotificationCompat
import com.jieqi.assistant.R
import com.jieqi.assistant.capture.AutoClickService
import com.jieqi.assistant.capture.ProjectionService
import com.jieqi.assistant.core.*
import com.jieqi.assistant.databinding.OverlayPanelBinding
import com.jieqi.assistant.engine.MoveAnalyzer
import com.jieqi.assistant.engine.UciEngine
import com.jieqi.assistant.vision.BoardRecognizer
import com.jieqi.assistant.vision.PieceRecognizer
import com.jieqi.assistant.vision.StableFrameDetector

/**
 * P5+P6：悬浮窗 + 总调度。
 * 高频(150ms)刷新显示，低频(稳定帧)触发识别与引擎。
 * 两个开关：自动辅助、自动落子。
 */
class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "jieqi_overlay"
        const val NOTIFY_ID = 2002
    }

    private val main = Handler(Looper.getMainLooper())
    private val work = HandlerThread("jieqi_work").apply { start() }.looper.let { Handler(it) }
    private var binding: OverlayPanelBinding? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    @Volatile private var assistOn = false
    @Volatile private var autoMoveOn = false
    @Volatile private var running = true

    private var recognizer: BoardRecognizer? = null
    private var analyzer: MoveAnalyzer? = null
    private var engine: UciEngine? = null
    private val stable = StableFrameDetector()
    private var lastFp = ""
    private var lastMover: Char = 'b'
    private var pendingCandidates: List<MoveAnalyzer.Candidate> = emptyList()

    private val frameSink = object : ProjectionService.FrameSink {
        override fun onFrame(bmp: Bitmap) {
            work.post { onFrame(bmp) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        diagText = getString(R.string.diag_waiting)
        createChannel()
        startForeground(NOTIFY_ID, buildNotification())
        initEngineAndVision()
        addOverlay()
    }

    private fun initEngineAndVision() {
        val pr = PieceRecognizer.create(this)
        val roi = RoiConfig.load(this) ?: return
        if (pr != null) recognizer = BoardRecognizer(pr, roi)
        engine = UciEngine(this)
        val ok = engine?.prepare() ?: false
        Log.d("Overlay", "引擎准备=${ok}")
        analyzer = MoveAnalyzer(this, engine)
    }

    private fun addOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = View.inflate(this, R.layout.overlay_panel, null)
        binding = OverlayPanelBinding.bind(view)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 200 }

        binding?.toggleAssist?.setOnCheckedChangeListener { _, on ->
            assistOn = on
            if (!on) { lastFp = ""; stable.reset() }
            diag(if (on) getString(R.string.diag_assist_on) else getString(R.string.diag_paused))
        }
        binding?.toggleAutoMove?.setOnCheckedChangeListener { _, on ->
            autoMoveOn = on
            diag(if (on) getString(R.string.diag_automove_on) else getString(R.string.diag_hint_only))
        }
        // 拖动悬浮窗
        var sx = 0f; var sy = 0f
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX - params!!.x; sy = e.rawY - params!!.y }
                MotionEvent.ACTION_MOVE -> {
                    params!!.x = (e.rawX - sx).toInt(); params!!.y = (e.rawY - sy).toInt()
                    try { wm?.updateViewLayout(view, params) } catch (_: Throwable) {}
                }
            }
            false
        }
        try { wm?.addView(view, params) } catch (e: Throwable) { Log.e("Overlay", "addView失败: ${e.message}") }
    }

    private fun onFrame(bmp: Bitmap) {
        if (!assistOn) return
        val r = recognizer ?: run { diag(getString(R.string.diag_no_recognizer)); return }
        if (!stable.update(bmp)) return // 稳定帧门控

        val res = r.recognize(bmp)
        val fp = res.board.fingerprint()
        val stats = getString(R.string.stats_pieces, res.board.countPieces(), res.board.countDark(), res.board.countRed()) +
                (if (res.suspects > 0) getString(R.string.stats_suspect, res.suspects) else "")
        main.post { binding?.tvStats?.text = stats }

        // 局面没变 -> 不重复分析/落子
        if (fp == lastFp) return
        lastFp = fp
        val an = analyzer ?: return
        val cands = an.analyze(res.board, DarkPool().apply { rebuildFrom(res.board) }, 'r', false)
        pendingCandidates = cands
        val mode = if (engine?.ready == true) getString(R.string.mode_engine) else getString(R.string.mode_stats)
        val movesText = cands.joinToString("\n") { "${it.notation}  ${String.format("%.2f", it.score)}" }
        main.post {
            binding?.tvMoves?.text = movesText
            binding?.tvDiag?.text = getString(R.string.diag_mode, mode, diagText)
            if (cands.isNotEmpty()) {
                val mv = com.jieqi.assistant.core.Move.fromUci(cands[0].uci, false)
                binding?.boardMini?.setBoard(res.board.snapshot(),
                    mv?.let { it.fromRow to it.fromCol }, mv?.let { it.toRow to it.toCol }, 'r')
            } else binding?.boardMini?.setBoard(res.board.snapshot(), null, null, 'r')
        }

        if (autoMoveOn && cands.isNotEmpty()) doAutoMove(cands[0].uci)
    }

    private fun doAutoMove(uci: String) {
        val mv = com.jieqi.assistant.core.Move.fromUci(uci, false) ?: return
        val svc = ProjectionService.instance ?: return
        val roi = RoiConfig.load(this) ?: return
        fun pt(col: Int, row: Int): FloatArray {
            val x = roi.xs[col].toFloat(); val y = roi.ys[row].toFloat()
            // 截图坐标 -> 屏幕坐标（通常一致）；dispatchGesture 用 display 像素
            return floatArrayOf(x, y)
        }
        val a = pt(mv.fromCol, mv.fromRow); val b = pt(mv.toCol, mv.toRow)
        AutoClickService.instance?.performMove(a[0], a[1], b[0], b[1]) { ok ->
            main.post { diag(if (ok) getString(R.string.diag_moved, uci) else getString(R.string.diag_move_failed)) }
        } ?: main.post { diag(getString(R.string.diag_no_a11y)) }
    }

    @Volatile private var diagText = ""
    private fun diag(t: String) { diagText = t; main.post { binding?.tvDiag?.text = t } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ProjectionService.instance?.sink = frameSink
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.notif_overlay_title)).setContentText(getString(R.string.notif_overlay_text))
        .setSmallIcon(R.drawable.ic_stat_notify).setOngoing(true).build()

    override fun onDestroy() {
        running = false
        try { binding?.root?.let { wm?.removeView(it) } } catch (_: Throwable) {}
        if (ProjectionService.instance?.sink === frameSink) ProjectionService.instance?.sink = null
        work.post { engine?.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
