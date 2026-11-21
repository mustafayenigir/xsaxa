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

    private lateinit var zoomSeek: SeekBar
    private lateinit var zoomLabel: TextView
    private lateinit var zoomPreview: TextView
    private lateinit var siteGrid: GridLayout
    private lateinit var siteMessage: TextView
    private lateinit var siteRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        Palettes.apply(this)
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

        // --- Tema (renk paleti) ---
        renderPalettes()

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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Harf dairesi için canlı renkler (site adından seçilir; aynı site hep aynı rengi alır)
    private val tilePalette = intArrayOf(
        0xFFFF5A36.toInt(), 0xFFFF3D87.toInt(), 0xFF8E44EC.toInt(), 0xFF2D9CDB.toInt(),
        0xFF1DB98A.toInt(), 0xFFF2A100.toInt()
    )

    /** Her site için logo + altında adı olan bir kutucuk çizer. Sağ alttaki "i" açıklamayı gösterir. */
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
        val muted = resources.getColor(R.color.on_background_muted, theme)
        val surface = Palettes.color(this, R.attr.cSurface)
        val primary = Palettes.color(this, R.attr.cPrimary)
        val outline = Palettes.color(this, R.attr.cOutline)
        val cols = 3

        list.forEachIndexed { index, link ->
            val selected = link.url == current
            val tile = FrameLayout(this)
            tile.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(surface)
                setStroke(dp(if (selected) 3 else 1), if (selected) primary else outline)
            }
            tile.elevation = dp(3).toFloat()
            tile.contentDescription = link.name

            val column = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(14), dp(6), dp(10))
            }

            // Logo gelene kadar (veya hiç gelmezse) sitenin baş harfi renkli bir daire içinde gösterilir
            val logoBox = FrameLayout(this)
            val circleColor = tilePalette[Math.abs(link.name.hashCode()) % tilePalette.size]
            val letter = TextView(this).apply {
                text = link.name.trim().take(1).uppercase()
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(circleColor) }
            }
            val icon = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(12).toFloat()) }
                }
            }
            logoBox.addView(letter, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            logoBox.addView(icon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            column.addView(logoBox, android.widget.LinearLayout.LayoutParams(dp(48), dp(48)))

            val name = TextView(this).apply {
                text = link.name
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            column.addView(name, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
            tile.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            // Sağ alt köşe: açıklama (E-Tablodaki C sütunu)
            val info = TextView(this).apply {
                text = "i"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(muted) }
                contentDescription = link.name
                setOnClickListener { showSiteInfo(link) }
            }
            tile.addView(info, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(8), dp(8))
            })

            tile.setOnClickListener { pickSite(link) }

            val lp = GridLayout.LayoutParams(GridLayout.spec(index / cols), GridLayout.spec(index % cols, 1f))
            lp.width = 0
            lp.height = dp(122)
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

    /** Kişilik temaları: iki sütunlu renkli kartlar; birine dokununca tema hemen uygulanır. */
    private fun renderPalettes() {
        val grid = findViewById<GridLayout>(R.id.paletteGrid)
        val desc = findViewById<TextView>(R.id.paletteDesc)
        grid.removeAllViews()
        val current = Palettes.index(this)
        val surface = Palettes.color(this, R.attr.cSurface)
        val primary = Palettes.color(this, R.attr.cPrimary)
        val outline = Palettes.color(this, R.attr.cOutline)
        val textColor = resources.getColor(R.color.on_background, theme)
        val muted = resources.getColor(R.color.on_background_muted, theme)

        Palettes.all.forEachIndexed { index, pal ->
            val card = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(surface)
                    setStroke(dp(if (index == current) 3 else 1), if (index == current) primary else outline)
                }
                elevation = dp(2).toFloat()
                contentDescription = pal.name + ", " + pal.type
            }
            val swatch = TextView(this).apply {
                text = if (index == current) "✓" else ""
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, intArrayOf(pal.start, pal.end)
                ).apply { cornerRadius = dp(12).toFloat() }
            }
            card.addView(swatch, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
            val title = TextView(this).apply {
                text = pal.name
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            card.addView(title, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
            val sub = TextView(this).apply {
                text = pal.type
                setTextColor(muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            card.addView(sub)

            card.setOnClickListener {
                if (index != AppSettings.palette(this)) {
                    AppSettings.setPalette(this, index)
                    recreate() // yeni renklerle yeniden çiz
                }
            }
            val lp = GridLayout.LayoutParams(GridLayout.spec(index / 2), GridLayout.spec(index % 2, 1f))
            lp.width = 0
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            grid.addView(card, lp)
        }
        val sel = Palettes.all[current]
        desc.text = sel.name + " (" + sel.type + "): " + sel.desc
    }

    private fun showSiteInfo(link: SiteLink) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(link.name)
            .setMessage(if (link.desc.isBlank()) getString(R.string.site_info_none) else link.desc)
            .setPositiveButton(R.string.ok, null)
            .create()
        // Ekran görüntüsü engeli bu pencere için de geçerli olsun
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
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
