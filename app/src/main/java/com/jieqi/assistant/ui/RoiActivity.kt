package com.jieqi.assistant.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jieqi.assistant.R
import com.jieqi.assistant.core.RoiConfig

class RoiActivity : AppCompatActivity() {
    private lateinit var roiView: RoiView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roi)
        roiView = findViewById(R.id.roiView)

        val dm = resources.displayMetrics
        val existing = RoiConfig.load(this)
        val cfg = existing ?: RoiConfig.fromRect(
            (dm.widthPixels * 0.07f).toInt(),
            (dm.heightPixels * 0.29f).toInt(),
            (dm.widthPixels * 0.86f).toInt(),
            (dm.heightPixels * 0.43f).toInt()
        )
        roiView.setConfig(cfg)

        findViewById<Button>(R.id.btnSaveRoi).setOnClickListener {
            val c = roiView.getConfig() ?: return@setOnClickListener
            RoiConfig.save(this, c)
            Toast.makeText(this, R.string.toast_roi_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
