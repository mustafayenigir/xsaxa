package com.scjegitim.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    companion object {
        /** Kayıt sırasında ilk kez açılırken true: site seçimi + "Devam Et" düğmesi gösterilir. */
        const val EXTRA_FIRST_RUN = "first_run"
    }

    private var firstRun = false
    private lateinit var continueButton: Button

    private lateinit var zoomSeek: SeekBar
    private lateinit var zoomLabel: TextView
    private lateinit var zoomPreview: TextView
    private lateinit var siteGroup: RadioGroup
    private lateinit var siteMessage: TextView
    private lateinit var siteRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ayarlar ekranında da ekran görüntüsü engeli geçerli
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_settings)

        firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        continueButton = findViewById(R.id.siteContinue)
        continueButton.visibility = if (firstRun) View.VISIBLE else View.GONE
        continueButton.isEnabled = Account.selectedUrl(this) != null
        continueButton.setOnClickListener {
            if (Account.selectedUrl(this) == null) {
                Toast.makeText(this, R.string.site_choose_first, Toast.LENGTH_SHORT).show()
            } else {
                setResult(RESULT_OK)
                finish()
            }
        }

        // --- Web sitesi seçimi ---
        siteGroup = findViewById(R.id.siteGroup)
        siteMessage = findViewById(R.id.siteMessage)
        siteRefresh = findViewById(R.id.siteRefresh)
        siteRefresh.setOnClickListener { refreshSites() }
        renderSites(Account.cachedLinks(this))
        if (Account.identity(this) != null) refreshSites()

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

    private fun showSiteMessage(resId: Int?) {
        if (resId == null) {
            siteMessage.visibility = View.GONE
        } else {
            siteMessage.setText(resId)
            siteMessage.visibility = View.VISIBLE
        }
    }

    /** Sunucudaki "web site linkleri" listesini yeniler (arka planda). */
    private fun refreshSites() {
        if (Account.identity(this) == null) return
        siteRefresh.isEnabled = false
        showSiteMessage(R.string.site_loading)
        val app = applicationContext
        worker.execute {
            val list = Account.refreshLinks(app)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                siteRefresh.isEnabled = true
                if (list == null) {
                    renderSites(Account.cachedLinks(this))
                    showSiteMessage(R.string.site_refresh_failed)
                } else {
                    renderSites(list)
                }
            }
        }
    }

    private fun renderSites(list: List<SiteLink>) {
        siteGroup.setOnCheckedChangeListener(null)
        siteGroup.removeAllViews()

        if (Account.identity(this) == null) {
            siteRefresh.visibility = View.GONE
            showSiteMessage(R.string.site_not_registered)
            return
        }
        siteRefresh.visibility = View.VISIBLE
        showSiteMessage(if (list.isEmpty()) R.string.site_empty else null)

        // İlk kurulumda hiçbiri hazır seçili gelmez; kullanıcı kendisi seçer
        val current = if (firstRun) Account.selectedUrl(this) else Account.siteToOpen(this)
        val byId = HashMap<Int, String>()
        for (link in list) {
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = if (link.desc.isBlank()) link.name else link.name + "\n" + link.desc
            rb.setTextColor(resources.getColor(R.color.on_background, theme))
            rb.textSize = 16f
            rb.setPadding(rb.paddingLeft, 12, rb.paddingRight, 12)
            siteGroup.addView(rb, RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT))
            byId[rb.id] = link.url
            if (link.url == current) rb.isChecked = true
        }
        siteGroup.setOnCheckedChangeListener { _, id ->
            val url = byId[id] ?: return@setOnCheckedChangeListener
            Account.setSelectedUrl(this, url)
            continueButton.isEnabled = true
            Toast.makeText(this, R.string.site_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private val worker = Executors.newSingleThreadExecutor()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
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
