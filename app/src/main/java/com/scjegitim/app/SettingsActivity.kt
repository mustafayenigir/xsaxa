package com.scjegitim.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var zoomSeek: SeekBar
    private lateinit var zoomLabel: TextView
    private lateinit var zoomPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ayarlar ekranında da ekran görüntüsü engeli geçerli
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        // --- Ses ---
        val keepAudio = findViewById<SwitchMaterial>(R.id.switchKeepAudio)
        keepAudio.isChecked = AppSettings.keepAudio(this)
        keepAudio.setOnCheckedChangeListener { _, checked ->
            AppSettings.setKeepAudio(this, checked)
        }
        findViewById<Button>(R.id.batteryButton).setOnClickListener { openBatterySettings() }

        // --- Görünüm (gece / gündüz) ---
        val group = findViewById<RadioGroup>(R.id.themeGroup)
        val checkedId = when (AppSettings.themeMode(this)) {
            AppSettings.THEME_LIGHT -> R.id.themeLight
            AppSettings.THEME_DARK -> R.id.themeDark
            else -> R.id.themeSystem
        }
        group.check(checkedId)
        group.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.themeLight -> AppSettings.THEME_LIGHT
                R.id.themeDark -> AppSettings.THEME_DARK
                else -> AppSettings.THEME_SYSTEM
            }
            AppSettings.setThemeMode(this, mode)
            AppSettings.applyNightMode(this)
        }

        // --- Yazı boyutu ---
        zoomSeek = findViewById(R.id.zoomSeek)
        zoomLabel = findViewById(R.id.zoomLabel)
        zoomPreview = findViewById(R.id.zoomPreview)

        zoomSeek.max = (AppSettings.ZOOM_MAX - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP
        val current = AppSettings.textZoom(this)
        zoomSeek.progress = (current - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP
        renderZoom(current)

        zoomSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val z = AppSettings.ZOOM_MIN + progress * AppSettings.ZOOM_STEP
                AppSettings.setTextZoom(this@SettingsActivity, z)
                renderZoom(z)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<Button>(R.id.zoomMinus).setOnClickListener {
            zoomSeek.progress = (zoomSeek.progress - 1).coerceAtLeast(0)
        }
        findViewById<Button>(R.id.zoomPlus).setOnClickListener {
            zoomSeek.progress = (zoomSeek.progress + 1).coerceAtMost(zoomSeek.max)
        }
        findViewById<Button>(R.id.zoomReset).setOnClickListener {
            zoomSeek.progress = (AppSettings.ZOOM_DEFAULT - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP
        }
    }

    private fun renderZoom(z: Int) {
        zoomLabel.text = "%$z"
        zoomPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * z / 100f)
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e2: Exception) {
                Toast.makeText(this, R.string.battery_open_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
