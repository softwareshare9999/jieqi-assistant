package com.jieqi.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.jieqi.assistant.R
import com.jieqi.assistant.capture.AutoClickService
import com.jieqi.assistant.capture.ProjectionService
import com.jieqi.assistant.core.RoiConfig

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private val REQ_PROJECTION = 100
    private val REQ_NOTIFY = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnProjection).setOnClickListener { requestProjection() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btnA11y).setOnClickListener { openA11y() }
        findViewById<Button>(R.id.btnRoi).setOnClickListener {
            startActivity(Intent(this, RoiActivity::class.java))
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startAssist() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopAssist() }

        refreshStatus()
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun refreshStatus() {
        val roi = RoiConfig.load(this) != null
        val ov = Settings.canDrawOverlays(this)
        val a11y = isA11yOn()
        val proj = ProjectionService.instance != null
        fun mark(on: Boolean) = if (on) "✓" else "✗"
        tvStatus.text = getString(R.string.status_line, mark(proj), mark(ov), mark(a11y), mark(roi))
    }

    private fun requestProjection() {
        // Android 13+ 通知权限先给，否则前台服务通知不显示
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJECTION && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ProjectionService::class.java).apply {
                action = ProjectionService.ACTION_START
                putExtra(ProjectionService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ProjectionService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            Toast.makeText(this, R.string.toast_projection_granted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(i)
        } else Toast.makeText(this, R.string.toast_overlay_enabled, Toast.LENGTH_SHORT).show()
    }

    private fun openA11y() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isA11yOn(): Boolean {
        val expected = "$packageName/${AutoClickService::class.java.name}"
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return flat.split(':').any { it.equals(expected, true) || it.contains(AutoClickService::class.java.name) }
    }

    private fun startAssist() {
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_SHORT).show(); return }
        if (RoiConfig.load(this) == null) { Toast.makeText(this, R.string.toast_need_roi, Toast.LENGTH_SHORT).show(); return }
        if (ProjectionService.instance == null) { Toast.makeText(this, R.string.toast_need_projection, Toast.LENGTH_SHORT).show(); return }
        val i = Intent(this, com.jieqi.assistant.overlay.OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        Toast.makeText(this, R.string.toast_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopAssist() {
        stopService(Intent(this, com.jieqi.assistant.overlay.OverlayService::class.java))
        stopService(Intent(this, ProjectionService::class.java))
        Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show()
    }
}
