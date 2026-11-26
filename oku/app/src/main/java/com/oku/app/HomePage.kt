package com.oku.app

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.util.concurrent.Executor

/** Ana Sayfa: öğrencide E-Tablodaki siteler, velide öğrencilerin durumu; ikisinde de bugünkü dersler. */
class HomePage(
    private val c: Context,
    private val worker: Executor,
    private val post: (Runnable) -> Unit,
    private val openSite: (SiteLink) -> Unit,
    private val openEvent: (Event) -> Unit,
    private val joinZoom: (String) -> Unit
) {
    private val palette = intArrayOf(0xFF2D9CDB.toInt(), 0xFF1B4F9C.toInt(), 0xFF8E44EC.toInt(), 0xFF1DB98A.toInt(), 0xFFF2A100.toInt(), 0xFFFF5A36.toInt())
    private fun dp(v: Int) = Ui.dp(c, v)
    private fun colorFor(s: String) = palette[Math.abs(s.hashCode()) % palette.size]

    fun render(box: LinearLayout, h: HomeData, loading: Boolean) {
        box.removeAllViews()
        val name = h.name.ifBlank { Account.name(c) }
        val first = name.trim().split(" ").firstOrNull().orEmpty()
        box.addView(Ui.text(c, if (first.isNotEmpty()) c.getString(R.string.home_hello_name, first) else c.getString(R.string.home_hello), 24f, bold = true))
        box.addView(Ui.muted(c, Format.dayLong(LocalDate.now()), 14f), Ui.matchWrap(2, c))
        if (loading) box.addView(Ui.muted(c, c.getString(R.string.loading), 13f), Ui.matchWrap(6, c))

        if (BuildConfig.PARENT) {
            box.addView(Ui.section(c, c.getString(R.string.home_children)))
            if (h.children.isEmpty()) box.addView(empty(R.string.home_children_empty))
            h.children.forEach { box.addView(childCard(it), Ui.matchWrap().apply { bottomMargin = dp(10) }) }
            todayLessons(box, h)
        } else {
            todayLessons(box, h)
            box.addView(Ui.section(c, c.getString(R.string.home_sites)))
            if (h.links.isEmpty()) box.addView(empty(R.string.home_sites_empty)) else box.addView(siteGrid(h.links), Ui.matchWrap())
        }
    }

    private fun empty(res: Int) = Ui.muted(c, c.getString(res), 14f).apply {
        gravity = Gravity.CENTER
        background = Ui.card(c)
        setPadding(dp(16), dp(20), dp(16), dp(20))
    }

    // ------------------------------------------------------------ bugünkü dersler

    private fun todayLessons(box: LinearLayout, h: HomeData) {
        val list = h.eventsOn(LocalDate.now()).sortedBy { if (it.start == null) -1 else it.startMin }
        if (list.isEmpty()) return
        box.addView(Ui.section(c, c.getString(R.string.home_today)))
        val primary = Ui.color(c, R.color.primary)
        list.forEach { ev ->
            val row = Ui.hbox(c).apply {
                setPadding(dp(14), dp(12), dp(10), dp(12))
                background = Ui.rounded(c, Ui.withAlpha(primary, 30), 18, primary, 1)
                isClickable = true
                setOnClickListener { openEvent(ev) }
            }
            val col = Ui.vbox(c)
            col.addView(Ui.text(c, ev.title, 16f, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            if (ev.start != null) col.addView(Ui.text(c, ev.timeText(), 14f, Ui.color(c, R.color.accent_text), true))
            row.addView(col, Ui.weight1())
            if (ev.zoom.isNotEmpty()) row.addView(MaterialButton(c).apply {
                setText(R.string.zoom_join_short)
                setIconResource(R.drawable.ic_video)
                setOnClickListener { joinZoom(ev.zoom) }
            })
            box.addView(row, Ui.matchWrap().apply { bottomMargin = dp(8) })
        }
    }

    // ------------------------------------------------------------ öğrenci: siteler

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
            val logo = FrameLayout(c)
            val letter = TextView(c).apply {
                text = link.name.trim().take(1).uppercase(Format.TR)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = Ui.circle(colorFor(link.name))
            }
            val icon = ImageView(c).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Ui.dpf(c, 12f)) }
                }
            }
            logo.addView(letter, FrameLayout.LayoutParams(dp(48), dp(48)))
            logo.addView(icon, FrameLayout.LayoutParams(dp(48), dp(48)))
            col.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
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
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(8), dp(8)) })

            val lp = GridLayout.LayoutParams(GridLayout.spec(i / cols), GridLayout.spec(i % cols, 1f))
            lp.width = 0
            lp.height = dp(122)
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            grid.addView(tile, lp)

            worker.execute {
                val bmp = Favicons.load(c.applicationContext, link.url)
                if (bmp != null) post(Runnable {
                    icon.setImageBitmap(bmp)
                    icon.visibility = View.VISIBLE
                    letter.visibility = View.GONE
                })
            }
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

    // ------------------------------------------------------------ veli: öğrenciler

    private fun childCard(ch: Child): View {
        val card = Ui.hbox(c).apply {
            gravity = Gravity.TOP
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = Ui.card(c)
            elevation = Ui.dpf(c, 2f)
            isClickable = ch.registered
            if (ch.registered) setOnClickListener { childDetail(ch) }
        }
        val display = ch.name.ifBlank { ch.no }
        card.addView(Ui.text(c, display.trim().take(1).uppercase(Format.TR), 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = Ui.circle(colorFor(display))
        }, LinearLayout.LayoutParams(dp(46), dp(46)))

        val col = Ui.vbox(c)
        col.addView(Ui.text(c, display, 17f, bold = true))
        col.addView(Ui.muted(c, c.getString(R.string.child_no, ch.no) + if (ch.group.isNotEmpty()) " · " + ch.group else ""), Ui.matchWrap(2, c))
        when {
            !ch.found -> col.addView(Ui.text(c, c.getString(R.string.child_not_found), 14f, 0xFFE53935.toInt()), Ui.matchWrap(8, c))
            !ch.registered -> col.addView(Ui.text(c, c.getString(R.string.child_not_registered), 14f, Ui.color(c, R.color.accent_text), true), Ui.matchWrap(8, c))
            else -> {
                col.addView(Ui.text(c, c.getString(R.string.child_last, Format.stamp(ch.last)), 14f), Ui.matchWrap(8, c))
                val chips = Ui.hbox(c)
                listOf(
                    c.getString(R.string.child_today, Format.duration(ch.today)),
                    c.getString(R.string.child_week, Format.duration(ch.week)),
                    c.getString(R.string.child_total, Format.duration(ch.total))
                ).forEach { chips.addView(Ui.chip(c, it), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }) }
                col.addView(android.widget.HorizontalScrollView(c).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(chips)
                }, Ui.matchWrap(8, c))
                col.addView(Ui.muted(c, c.getString(R.string.child_tap), 12f), Ui.matchWrap(6, c))
            }
        }
        card.addView(col, Ui.weight1().apply { marginStart = dp(12) })
        return card
    }

    private fun childDetail(ch: Child) {
        val box = Ui.vbox(c).apply { setPadding(dp(22), dp(8), dp(22), 0) }
        box.addView(Ui.text(c, c.getString(R.string.child_since, Format.stamp(ch.since)), 14f))
        box.addView(Ui.text(c, c.getString(R.string.child_count, ch.count), 14f), Ui.matchWrap(4, c))
        box.addView(Ui.text(c, c.getString(R.string.child_total, Format.duration(ch.total)), 14f), Ui.matchWrap(4, c))
        box.addView(Ui.section(c, c.getString(R.string.child_history)))
        if (ch.logins.isEmpty()) box.addView(Ui.muted(c, "—", 14f))
        ch.logins.forEach { l ->
            val row = Ui.hbox(c).apply { setPadding(0, dp(6), 0, dp(6)) }
            row.addView(Ui.text(c, Format.stamp(l.at), 14f), Ui.weight1())
            row.addView(Ui.chip(c, l.ev))
            box.addView(row, Ui.matchWrap())
        }
        secure(AlertDialog.Builder(c)
            .setTitle(ch.name.ifBlank { ch.no })
            .setView(android.widget.ScrollView(c).apply { addView(box) })
            .setPositiveButton(R.string.close, null)
            .create())
    }

    companion object {
        /** Ekran görüntüsü engeli açılan pencerelerde de geçerli olsun. */
        fun secure(d: AlertDialog) {
            d.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            d.show()
        }
    }
}
