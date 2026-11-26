package com.oku.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/** Ayarlar sayfası (alt menünün sağ sekmesi). */
class SettingsPage(
    private val c: Context,
    private val changed: () -> Unit,
    private val lock: () -> Unit
) {
    private fun dp(v: Int) = Ui.dp(c, v)

    fun render(box: LinearLayout) {
        box.removeAllViews()

        // Hesap
        box.addView(Ui.section(c, c.getString(R.string.set_account)))
        val acc = Ui.vbox(c).apply { background = Ui.card(c); setPadding(dp(16), dp(14), dp(16), dp(14)) }
        val ident = Account.identity(c)
        acc.addView(Ui.text(c, Account.name(c).ifBlank { "—" }, 17f, bold = true))
        acc.addView(Ui.muted(c, c.getString(if (BuildConfig.PARENT) R.string.set_parent_no else R.string.set_student_no, ident?.no ?: "—"), 14f), Ui.matchWrap(2, c))
        acc.addView(MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.menu_lock)
            setIconResource(R.drawable.ic_lock)
            setOnClickListener { lock() }
        }, Ui.matchWrap(10, c))
        box.addView(acc, Ui.matchWrap())

        if (!BuildConfig.PARENT) {
            // Ses
            box.addView(Ui.section(c, c.getString(R.string.set_audio)))
            box.addView(switch(R.string.keep_audio_title, AppSettings.keepAudio(c)) { AppSettings.setKeepAudio(c, it); changed() })
            box.addView(Ui.muted(c, c.getString(R.string.keep_audio_desc)))
            box.addView(MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                setText(R.string.battery_button)
                setOnClickListener { openBattery() }
            }, Ui.matchWrap(8, c))

            // Reklam
            box.addView(Ui.section(c, c.getString(R.string.set_ads)))
            box.addView(switch(R.string.ad_block_title, AppSettings.adBlock(c)) { AppSettings.setAdBlock(c, it); changed() })
            box.addView(Ui.muted(c, c.getString(R.string.ad_block_desc)))
        }

        // Görünüm
        box.addView(Ui.section(c, c.getString(R.string.set_theme)))
        val group = RadioGroup(c)
        val modes = intArrayOf(AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK)
        val labels = intArrayOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
        val ids = IntArray(3)
        modes.forEachIndexed { i, m ->
            val rb = RadioButton(c).apply {
                id = android.view.View.generateViewId()
                setText(labels[i])
                setTextColor(Ui.color(c, R.color.on_background))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                minHeight = dp(44)
            }
            ids[i] = rb.id
            group.addView(rb)
            if (AppSettings.themeMode(c) == m) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checked ->
            val m = modes[ids.indexOf(checked).coerceAtLeast(0)]
            if (m != AppSettings.themeMode(c)) {
                AppSettings.setThemeMode(c, m)
                AppSettings.applyNightMode(c)
            }
        }
        box.addView(group)

        if (!BuildConfig.PARENT) {
            // Yazı boyutu (sitelerde)
            box.addView(Ui.section(c, c.getString(R.string.set_font)))
            val label = Ui.text(c, "%" + AppSettings.textZoom(c), 15f, bold = true).apply { gravity = Gravity.CENTER }
            val seek = SeekBar(c).apply {
                max = (AppSettings.ZOOM_MAX - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP
                progress = (AppSettings.textZoom(c) - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        val z = AppSettings.ZOOM_MIN + p * AppSettings.ZOOM_STEP
                        AppSettings.setTextZoom(c, z)
                        label.text = "%$z"
                        changed()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            val row = Ui.hbox(c)
            row.addView(small("A−") { seek.progress = (seek.progress - 1).coerceAtLeast(0) })
            row.addView(seek, Ui.weight1())
            row.addView(small("A+") { seek.progress = (seek.progress + 1).coerceAtMost(seek.max) })
            box.addView(row, Ui.matchWrap())
            box.addView(label, Ui.matchWrap())
            box.addView(MaterialButton(c, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                setText(R.string.font_reset)
                setOnClickListener { seek.progress = (AppSettings.ZOOM_DEFAULT - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP }
            })
        }

        // Hakkında
        box.addView(Ui.section(c, c.getString(R.string.set_about)))
        box.addView(Ui.muted(c, c.getString(R.string.about_text, c.getString(R.string.app_name), BuildConfig.VERSION_NAME), 13f))
    }

    private fun switch(title: Int, on: Boolean, set: (Boolean) -> Unit) = SwitchMaterial(c).apply {
        setText(title)
        setTextColor(Ui.color(c, R.color.on_background))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isChecked = on
        minHeight = dp(48)
        setOnCheckedChangeListener { _, v -> set(v) }
    }

    private fun small(t: String, click: () -> Unit) =
        MaterialButton(c, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = t
            setOnClickListener { click() }
        }

    private fun openBattery() {
        try {
            c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            try {
                c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)))
            } catch (_: Exception) {
                Toast.makeText(c, R.string.battery_open_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
