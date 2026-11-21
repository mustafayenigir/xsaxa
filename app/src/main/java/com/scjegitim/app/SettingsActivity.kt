package com.scjegitim.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    companion object {
        /** Kayıt sırasında ilk kez açılırken true: bir logoya dokunmak seçip devam ettirir. */
        const val EXTRA_FIRST_RUN = "first_run"

        /** true ise bir logoya dokununca site seçilir ve bu ekran kapanır (site açılır). */
        const val EXTRA_OPEN_ON_PICK = "open_on_pick"

        /** true ise geri tuşu uygulamadan çıkış sonucu döndürür (siteden geri → Ana Sayfa → geri = çıkış). */
        const val EXTRA_EXIT_ON_BACK = "exit_on_back"

        const val RESULT_EXIT = Activity.RESULT_FIRST_USER
    }

    private var firstRun = false
    private var openOnPick = false
    private var exitOnBack = false
    private lateinit var recStatus: TextView
    private var recCallback: android.media.AudioManager.AudioRecordingCallback? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private lateinit var zoomSeek: SeekBar
    private lateinit var zoomLabel: TextView
    private lateinit var zoomPreview: TextView
    private lateinit var siteGrid: GridLayout
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
        openOnPick = firstRun || intent.getBooleanExtra(EXTRA_OPEN_ON_PICK, false)
        exitOnBack = intent.getBooleanExtra(EXTRA_EXIT_ON_BACK, false)
        findViewById<Button>(R.id.backButton).setOnClickListener { onBackPressed() }

        // --- Web siteleri (logo kutucukları) ---
        siteGrid = findViewById(R.id.siteGrid)
        siteMessage = findViewById(R.id.siteMessage)
        siteRefresh = findViewById(R.id.siteRefresh)
        siteRefresh.setOnClickListener { refreshSites() }
        renderSites(Account.cachedLinks(this))
        if (Account.identity(this) != null) refreshSites()

        // --- Reklam engelleme ---
        val adBlock = findViewById<SwitchMaterial>(R.id.switchAdBlock)
        adBlock.isChecked = AppSettings.adBlock(this)
        adBlock.setOnCheckedChangeListener { _, checked -> AppSettings.setAdBlock(this, checked) }

        // --- Ses ---
        val keepAudio = findViewById<SwitchMaterial>(R.id.switchKeepAudio)
        keepAudio.isChecked = AppSettings.keepAudio(this)
        keepAudio.setOnCheckedChangeListener { _, checked ->
            AppSettings.setKeepAudio(this, checked)
        }
        findViewById<Button>(R.id.batteryButton).setOnClickListener { openBatterySettings() }

        val pauseRec = findViewById<SwitchMaterial>(R.id.switchPauseRec)
        pauseRec.isChecked = AppSettings.pauseOnRecording(this)
        pauseRec.setOnCheckedChangeListener { _, checked -> AppSettings.setPauseOnRecording(this, checked) }
        recStatus = findViewById(R.id.recStatus)
        showRecStatus(RecordingWatch.current(this))

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

    private fun showRecStatus(sources: List<Int>) {
        recStatus.text = if (sources.isEmpty()) getString(R.string.rec_status_none)
        else getString(R.string.rec_status_some, sources.size, sources.joinToString(","))
    }

    override fun onStart() {
        super.onStart()
        recCallback = RecordingWatch.register(this, uiHandler) { showRecStatus(it) }
    }

    override fun onStop() {
        RecordingWatch.unregister(this, recCallback)
        recCallback = null
        super.onStop()
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Her site için tek bir küçük logo kutucuğu çizer. Logoya dokunmak siteyi seçer. */
    private fun renderSites(list: List<SiteLink>) {
        siteGrid.removeAllViews()

        if (Account.identity(this) == null) {
            siteRefresh.visibility = View.GONE
            showSiteMessage(R.string.site_not_registered)
            return
        }
        siteRefresh.visibility = View.VISIBLE
        showSiteMessage(if (list.isEmpty()) R.string.site_empty else null)

        // İlk kurulumda hiçbiri hazır seçili gelmez; kullanıcı kendisi seçer
        val current = if (firstRun) Account.selectedUrl(this) else Account.siteToOpen(this)
        val textColor = resources.getColor(R.color.on_background, theme)
        val accent = resources.getColor(R.color.accent_text, theme)
        val tileColor = (textColor and 0x00FFFFFF) or 0x14000000 // yazı renginin %8'i: her iki temada uyumlu
        val cols = 4

        list.forEachIndexed { index, link ->
            val selected = link.url == current
            val tile = FrameLayout(this)
            tile.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(tileColor)
                if (selected) setStroke(dp(2), accent)
            }
            tile.contentDescription = link.name

            // Logo gelene kadar (veya hiç gelmezse) sitenin baş harfi gösterilir
            val letter = TextView(this).apply {
                text = link.name.trim().take(1).uppercase()
                setTextColor(accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val icon = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(10).toFloat()) }
                }
            }
            tile.addView(letter, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            tile.addView(icon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))

            tile.setOnClickListener { pickSite(link) }
            tile.setOnLongClickListener {
                Toast.makeText(this, if (link.desc.isBlank()) link.name else link.name + " — " + link.desc, Toast.LENGTH_SHORT).show()
                true
            }

            val lp = GridLayout.LayoutParams(GridLayout.spec(index / cols), GridLayout.spec(index % cols, 1f))
            lp.width = 0
            lp.height = dp(84)
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            siteGrid.addView(tile, lp)

            val app = applicationContext
            worker.execute {
                val bmp = Favicons.load(app, link.url)
                if (bmp != null) runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    icon.setImageBitmap(bmp)
                    icon.visibility = View.VISIBLE
                    letter.visibility = View.GONE
                }
            }
        }
    }

    private fun pickSite(link: SiteLink) {
        Account.setSelectedUrl(this, link.url)
        if (openOnPick) {
            setResult(RESULT_OK) // Ana ekran siteyi açar
            finish()
        } else {
            Toast.makeText(this, R.string.site_saved, Toast.LENGTH_SHORT).show()
            renderSites(Account.cachedLinks(this))
        }
    }

    override fun onBackPressed() {
        if (exitOnBack) {
            setResult(RESULT_EXIT) // siteden geri ile gelindi: bir daha geri = uygulamadan çık
            finish()
        } else {
            super.onBackPressed()
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
