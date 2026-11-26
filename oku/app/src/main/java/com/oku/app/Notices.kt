package com.oku.app

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import java.time.LocalDate

/**
 * Sağ üstteki bildirimler. O güne ait dersler en üstte SABİT ve VURGULU durur;
 * diğer bildirimler sade listelenir. Dokununca açılır (ve okundu sayılır).
 */
class Notices(private val c: Context, private val joinZoom: (String) -> Unit, private val changed: () -> Unit) {

    class Item(val id: String, val pinned: Boolean, val title: String, val sub: String, val body: String, val zoom: String)

    private val expanded = HashSet<String>()

    fun items(h: HomeData): List<Item> {
        val today = LocalDate.now()
        val pinned = h.eventsOn(today).sortedBy { if (it.start == null) -1 else it.startMin }.map {
            Item("ev:" + it.id + ":" + today, true, it.title,
                c.getString(R.string.notice_today_lesson) + if (it.start != null) " · " + it.timeText() else "",
                it.desc, it.zoom)
        }
        val rest = h.notices.map {
            val d = Format.isoDate(it.date)
            Item("no:" + it.id, false, it.title, if (d != null) Format.relDay(d, today) else "", it.msg, "")
        }
        return pinned + rest
    }

    fun unread(h: HomeData): Int {
        val read = Account.readIds(c)
        return items(h).count { it.id !in read }
    }

    fun render(list: LinearLayout, h: HomeData) {
        list.removeAllViews()
        val all = items(h)
        val read = Account.readIds(c)
        if (all.isEmpty()) {
            list.addView(Ui.muted(c, c.getString(R.string.notice_empty), 14f).apply {
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 18))
            }, Ui.matchWrap())
            return
        }
        val primary = Ui.color(c, R.color.primary)
        all.forEach { it ->
            val open = it.id in expanded
            val unread = it.id !in read
            val box = Ui.vbox(c).apply {
                setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
                background = if (it.pinned)
                    Ui.rounded(c, Ui.withAlpha(primary, 46), 14, primary, 2)
                else Ui.rounded(c, Ui.withAlpha(Ui.color(c, R.color.surface), 170), 14)
                isClickable = true
                isFocusable = true
            }
            val top = Ui.hbox(c)
            if (it.pinned) top.addView(Ui.text(c, "📌", 14f), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = Ui.dp(c, 6) })
            top.addView(Ui.text(c, it.title, 15f, Ui.color(c, R.color.on_background), it.pinned || unread).apply {
                maxLines = if (open) 4 else 1
                ellipsize = TextUtils.TruncateAt.END
            }, Ui.weight1())
            if (unread) top.addView(View(c).apply { background = Ui.circle(0xFFE53935.toInt()) },
                LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)).apply { marginStart = Ui.dp(c, 6) })
            box.addView(top)
            if (it.sub.isNotEmpty()) box.addView(Ui.muted(c, it.sub, 12f), Ui.matchWrap(2, c))
            if (open) {
                if (it.body.isNotEmpty()) box.addView(Ui.text(c, it.body, 14f).apply { setTextIsSelectable(false) }, Ui.matchWrap(6, c))
                if (it.zoom.isNotEmpty()) box.addView(MaterialButton(c).apply {
                    setText(R.string.zoom_join)
                    setIconResource(R.drawable.ic_video)
                    setOnClickListener { _ -> joinZoom(it.zoom) }
                }, Ui.matchWrap(8, c))
            }
            box.setOnClickListener { _ ->
                if (!expanded.add(it.id)) expanded.remove(it.id)
                Account.markRead(c, it.id)
                render(list, h)
                changed()
            }
            list.addView(box, Ui.matchWrap().apply { bottomMargin = Ui.dp(c, 8) })
        }
    }

    fun markAllRead(h: HomeData) {
        items(h).forEach { Account.markRead(c, it.id) }
        changed()
    }
}
