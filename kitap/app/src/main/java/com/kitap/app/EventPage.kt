package com.kitap.app

import android.app.Activity
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.util.concurrent.Executor

/**
 * Eğitim ayrıntısı: eğitim metni (emojiler dahil) tamamen görünür, en altta "Katıl" düğmesi
 * telefondaki Zoom uygulamasını açar. Altında kişinin bu eğitim hakkındaki notları (yalnızca kendisi ve yönetim görür).
 */
class EventPage(private val a: Activity, private val io: Executor) {
    private fun dp(v: Int) = Ui.dp(a, v)
    private var shown: Event? = null

    fun render(host: FrameLayout, ev: Event) {
        shown = ev
        host.removeAllViews()
        val scroll = ScrollView(a).apply { isFillViewport = true }
        val box = Ui.vbox(a).apply { setPadding(dp(18), dp(16), dp(18), dp(110)) }
        scroll.addView(box)

        box.addView(Ui.text(a, ev.title, 22f, bold = true))
        val whenText = Format.dayLong(ev.date) + if (ev.start != null) "  ·  " + ev.timeText() else ""
        box.addView(Ui.chip(a, whenText), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })

        val text = ev.desc.trim()
        box.addView(Ui.text(a, if (text.isNotEmpty()) text else a.getString(R.string.edu_no_text), 17f,
            if (text.isNotEmpty()) Ui.color(a, R.color.on_background) else Ui.color(a, R.color.on_background_muted)).apply {
            setLineSpacing(0f, 1.25f)
            background = Ui.card(a)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }, Ui.matchWrap(14, a))

        // Notlarım
        box.addView(Ui.section(a, a.getString(R.string.edu_notes)))
        box.addView(Ui.muted(a, a.getString(R.string.edu_notes_info), 13f))
        val input = EditText(a).apply {
            hint = a.getString(R.string.edu_note_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            background = a.getDrawable(R.drawable.bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(Ui.color(a, R.color.on_background))
            setHintTextColor(Ui.color(a, R.color.on_background_muted))
        }
        box.addView(input, Ui.matchWrap(8, a))
        val list = Ui.vbox(a)
        val save = MaterialButton(a).apply { setText(R.string.edu_note_save) }
        save.setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            save.isEnabled = false
            send(JSONObject().put("action", "eduNote").put("not", t), ev) { r ->
                save.isEnabled = true
                if (r?.optBoolean("ok") == true) {
                    input.text?.clear()
                    Toast.makeText(a, R.string.edu_note_saved, Toast.LENGTH_SHORT).show()
                    renderNotes(list, ev, r)
                } else Toast.makeText(a, R.string.err_save, Toast.LENGTH_LONG).show()
            }
        }
        box.addView(save, Ui.matchWrap(8, a))
        box.addView(list, Ui.matchWrap(10, a))
        list.addView(Ui.muted(a, a.getString(R.string.loading), 13f))
        send(JSONObject().put("action", "eduNotes"), ev) { r -> renderNotes(list, ev, r) }

        host.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        // En altta sabit "Katıl"
        val bar = Ui.vbox(a).apply {
            setPadding(dp(16), dp(10), dp(16), dp(12))
            setBackgroundColor(Ui.withAlpha(Ui.color(a, R.color.background), 240))
            elevation = Ui.dpf(a, 8f)
        }
        if (ev.zoom.isNotEmpty()) {
            bar.addView(MaterialButton(a).apply {
                setText(R.string.zoom_join)
                setIconResource(R.drawable.ic_video)
                textSize = 18f
                minHeight = dp(56)
                setOnClickListener { ZoomOpen.open(a, ev.zoom) }
            }, Ui.matchWrap())
        } else {
            bar.addView(Ui.muted(a, a.getString(R.string.event_no_zoom), 14f).apply { gravity = Gravity.CENTER }, Ui.matchWrap())
        }
        host.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun send(body: JSONObject, ev: Event, done: (JSONObject?) -> Unit) {
        val id = Account.identity(a) ?: return done(null)
        body.put("date", ev.date.toString()).put("title", ev.title)
        io.execute {
            val r = Api.authed(id, body)
            a.runOnUiThread { if (!a.isDestroyed && shown === ev) done(r) }
        }
    }

    private fun renderNotes(list: LinearLayout, ev: Event, r: JSONObject?) {
        list.removeAllViews()
        if (r == null || !r.optBoolean("ok")) {
            list.addView(Ui.muted(a, a.getString(R.string.err_network), 13f))
            return
        }
        val notes = r.optJSONArray("notes")
        if (notes == null || notes.length() == 0) {
            list.addView(Ui.muted(a, a.getString(R.string.edu_notes_empty), 13f))
            return
        }
        for (i in 0 until notes.length()) {
            val n = notes.optJSONObject(i) ?: continue
            val card = Ui.vbox(a).apply { background = Ui.card(a); setPadding(dp(14), dp(10), dp(14), dp(10)) }
            val top = Ui.hbox(a)
            top.addView(Ui.muted(a, Format.stamp(n.optString("at")), 12f), Ui.weight1())
            top.addView(Ui.text(a, a.getString(R.string.delete), 13f, 0xFFC0392B.toInt(), true).apply {
                setPadding(dp(8), dp(4), dp(4), dp(4))
                setOnClickListener {
                    HomePage.secure(AlertDialog.Builder(a).setMessage(R.string.edu_note_delete_q)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            send(JSONObject().put("action", "eduNoteDelete").put("row", n.optInt("row")).put("at", n.optString("at")), ev) { rr ->
                                if (rr?.optBoolean("ok") == true) renderNotes(list, ev, rr)
                            }
                        }.setNegativeButton(R.string.cancel, null).create())
                }
            })
            card.addView(top)
            card.addView(Ui.text(a, n.optString("not"), 15f).apply { setLineSpacing(0f, 1.15f) }, Ui.matchWrap(4, a))
            list.addView(card, Ui.matchWrap().apply { bottomMargin = dp(8) })
        }
    }

    fun clear() { shown = null }
}
