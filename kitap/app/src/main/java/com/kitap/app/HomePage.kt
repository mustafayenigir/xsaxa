package com.kitap.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.time.LocalDate

/** Ana Sayfa: bugünkü ve yarınki eğitimler, sonra E-Tablodaki içerikler. */
class HomePage(
    private val c: Context,
    private val openSite: (SiteLink) -> Unit,
    private val openEvent: (Event) -> Unit,
    private val openPlan: (Plan?) -> Unit
) {
    private val palette = intArrayOf(0xFF0B6B3A.toInt(), 0xFF1E6FD9.toInt(), 0xFF8E44EC.toInt(), 0xFF138A57.toInt(), 0xFFE08600.toInt(), 0xFFD9480F.toInt(), 0xFF0E7C86.toInt(), 0xFF6D4C41.toInt())
    private fun dp(v: Int) = Ui.dp(c, v)
    private fun colorFor(s: String) = palette[Math.abs(s.hashCode()) % palette.size]

    fun render(box: LinearLayout, h: HomeData, loading: Boolean) {
        box.removeAllViews()
        val name = h.name.ifBlank { Account.name(c) }
        val first = name.trim().split(" ").firstOrNull().orEmpty()
        box.addView(Ui.text(c, if (first.isNotEmpty()) c.getString(R.string.home_hello_name, first) else c.getString(R.string.home_hello), 24f, bold = true))
        box.addView(Ui.muted(c, Format.dayLong(LocalDate.now()), 14f), Ui.matchWrap(2, c))
        if (loading) box.addView(Ui.muted(c, c.getString(R.string.loading), 13f), Ui.matchWrap(6, c))

        val today = LocalDate.now()
        eventSection(box, R.string.home_today, h.eventsOn(today), true)
        eventSection(box, R.string.home_tomorrow, h.eventsOn(today.plusDays(1)), false)
        planSection(box, h)

        // "Göremez" yapılmış kişide içerik linkleri hiç gösterilmez
        if (h.access) {
            box.addView(Ui.section(c, c.getString(R.string.home_sites)))
            if (h.links.isEmpty()) box.addView(empty(R.string.home_sites_empty)) else box.addView(siteGrid(h.links), Ui.matchWrap())
        }
    }

    // ------------------------------------------------------------ kişisel notlar

    private fun planSection(box: LinearLayout, h: HomeData) {
        box.addView(Ui.section(c, c.getString(R.string.plan_section)))
        val today = LocalDate.now()
        val list = h.plans.filter { !it.date.isBefore(today) }.sortedWith(compareBy({ it.date }, { it.time ?: java.time.LocalTime.MIN })).take(8)
        val amber = 0xFFE08600.toInt()
        if (list.isEmpty()) box.addView(Ui.muted(c, c.getString(R.string.plan_empty), 14f).apply {
            background = Ui.card(c); setPadding(dp(16), dp(14), dp(16), dp(14))
        }, Ui.matchWrap())
        list.forEach { p ->
            val row = Ui.hbox(c).apply {
                setPadding(dp(14), dp(10), dp(12), dp(10))
                background = Ui.rounded(c, Ui.withAlpha(amber, 28), 16, Ui.withAlpha(amber, 120), 1)
                isClickable = true
                setOnClickListener { openPlan(p) }
            }
            val col = Ui.vbox(c)
            col.addView(Ui.text(c, Format.relDay(p.date, today) + (p.time?.let { "  ·  " + Format.time(it) } ?: ""), 13f, amber, true))
            col.addView(Ui.text(c, p.text, 15f).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END }, Ui.matchWrap(2, c))
            row.addView(Ui.text(c, "📝", 20f), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
            row.addView(col, Ui.weight1())
            box.addView(row, Ui.matchWrap().apply { bottomMargin = dp(8) })
        }
        box.addView(com.google.android.material.button.MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.plan_add)
            setOnClickListener { openPlan(null) }
        }, Ui.matchWrap(2, c))
    }

    private fun empty(res: Int) = Ui.muted(c, c.getString(res), 14f).apply {
        gravity = Gravity.CENTER
        background = Ui.card(c)
        setPadding(dp(16), dp(18), dp(16), dp(18))
    }

    // ------------------------------------------------------------ eğitimler

    private fun eventSection(box: LinearLayout, title: Int, list: List<Event>, strong: Boolean) {
        box.addView(Ui.section(c, c.getString(title)))
        if (list.isEmpty()) {
            box.addView(empty(if (strong) R.string.home_today_empty else R.string.home_tomorrow_empty))
            return
        }
        val primary = Ui.color(c, R.color.primary)
        list.forEach { ev ->
            val row = Ui.hbox(c).apply {
                setPadding(dp(14), dp(12), dp(12), dp(12))
                background = if (strong) Ui.rounded(c, Ui.withAlpha(primary, 30), 18, primary, 1) else Ui.card(c)
                isClickable = true
                isFocusable = true
                setOnClickListener { openEvent(ev) }
            }
            val col = Ui.vbox(c)
            col.addView(Ui.text(c, ev.title, 16f, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            val sub = listOfNotNull(ev.timeText().ifEmpty { null }, if (ev.zoom.isNotEmpty()) c.getString(R.string.edu_has_zoom) else null).joinToString("  ·  ")
            if (sub.isNotEmpty()) col.addView(Ui.text(c, sub, 14f, Ui.color(c, R.color.accent_text), true), Ui.matchWrap(2, c))
            val preview = ev.desc.replace(Regex("\\s+"), " ").trim()
            if (preview.isNotEmpty()) col.addView(Ui.muted(c, preview, 13f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, Ui.matchWrap(4, c))
            row.addView(col, Ui.weight1())
            row.addView(Ui.text(c, "›", 26f, Ui.color(c, R.color.on_background_muted)), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            box.addView(row, Ui.matchWrap().apply { bottomMargin = dp(8) })
        }
    }

    // ------------------------------------------------------------ içerikler

    /** Logo yerine adın ilk iki harfi: benzer adresli siteler karışmasın. */
    private fun twoLetters(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        if (letters.isEmpty()) return "?"
        val a = letters.take(1).uppercase(Format.TR)
        val b = letters.drop(1).take(1).lowercase(Format.TR)
        return a + b
    }

    private fun siteGrid(list: List<SiteLink>): View {
        val cols = 3
        val grid = GridLayout(c).apply { columnCount = cols }
        val surface = Ui.color(c, R.color.surface)
        val outline = Ui.color(c, R.color.outline)
        val muted = Ui.color(c, R.color.on_background_muted)
        list.forEachIndexed { i, link ->
            val tile = FrameLayout(c).apply {
                background = Ui.rounded(c, surface, 20, outline)
                elevation = Ui.dpf(c, 2f)
                contentDescription = link.name
                setOnClickListener { openSite(link) }
                setOnLongClickListener { showInfo(link); true }
            }
            val col = Ui.vbox(c).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(6), dp(14), dp(6), dp(10)) }
            val letter = TextView(c).apply {
                text = twoLetters(link.name)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
                background = Ui.rounded(c, colorFor(link.name), 14)
            }
            col.addView(letter, LinearLayout.LayoutParams(dp(52), dp(52)))
            col.addView(Ui.text(c, link.name, 13f, bold = true).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, Ui.matchWrap(8, c))
            tile.addView(col, FrameLayout.LayoutParams(-1, -1))
            tile.addView(TextView(c).apply {
                text = "i"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                gravity = Gravity.CENTER
                background = Ui.circle(muted)
                contentDescription = c.getString(R.string.site_info_desc, link.name)
                setOnClickListener { showInfo(link) }
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })

            val lp = GridLayout.LayoutParams(GridLayout.spec(i / cols), GridLayout.spec(i % cols, 1f))
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            tile.minimumHeight = dp(124)
            grid.addView(tile, lp)
        }
        return grid
    }

    private fun showInfo(link: SiteLink) {
        secure(AlertDialog.Builder(c)
            .setTitle(link.name)
            .setMessage(link.desc.ifBlank { c.getString(R.string.site_info_none) })
            .setPositiveButton(R.string.open) { _, _ -> openSite(link) }
            .setNegativeButton(R.string.close, null)
            .create())
    }

    companion object {
        /** Ekran görüntüsü engeli açılan pencerelerde de geçerli olsun. */
        fun secure(d: AlertDialog): AlertDialog {
            d.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            d.show()
            return d
        }
    }
}
