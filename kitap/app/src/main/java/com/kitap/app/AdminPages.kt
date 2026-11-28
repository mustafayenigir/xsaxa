package com.kitap.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executor

/**
 * Üye Yönetim ekranları: Kişiler (liste + ayrıntı + kişiler arası geçiş), Eğitimler, Bildirimler.
 * Hepsi Excel'deki sayfalardan okunur ve oraya yazar.
 */
class AdminPages(
    private val a: Activity,
    private val io: Executor,
    private val reload: () -> Unit,
    private val openPerson: (String) -> Unit,
    private val openCrashes: () -> Unit,
    private val onAuthError: () -> Unit
) {
    var data: AdminData = AdminData.EMPTY
    private var query = ""
    private var visible: List<Person> = emptyList()
    private var personToken = 0

    private fun dp(v: Int) = Ui.dp(a, v)
    private val primary get() = Ui.color(a, R.color.primary)
    private val red = 0xFFC0392B.toInt()

    private fun call(body: JSONObject, done: (JSONObject?) -> Unit) {
        val id = Account.identity(a) ?: return done(null)
        io.execute {
            val r = Api.authed(id, body)
            a.runOnUiThread {
                if (a.isDestroyed) return@runOnUiThread
                if (r?.optString("error") == "auth") onAuthError() else done(r)
            }
        }
    }

    private fun errText(r: JSONObject?): String = when (r?.optString("error")) {
        null -> a.getString(R.string.err_network)
        "exists" -> a.getString(R.string.adm_err_exists)
        "bad_password" -> a.getString(R.string.err_short)
        "bad_link" -> a.getString(R.string.adm_err_link)
        "changed" -> a.getString(R.string.adm_err_changed)
        else -> a.getString(R.string.err_save)
    }

    private fun scrollPage(host: FrameLayout): LinearLayout {
        host.removeAllViews()
        val sc = ScrollView(a).apply { isFillViewport = true }
        val box = Ui.vbox(a).apply { setPadding(dp(14), dp(12), dp(14), dp(28)) }
        sc.addView(box)
        host.addView(sc, FrameLayout.LayoutParams(-1, -1))
        return box
    }

    private fun addButton(text: Int, click: () -> Unit) = MaterialButton(a).apply {
        setText(text)
        setOnClickListener { click() }
    }

    private fun avatar(name: String, size: Int = 44): View {
        val letters = name.filter { it.isLetterOrDigit() }
        val t = (letters.take(1).uppercase(Format.TR) + letters.drop(1).take(1).lowercase(Format.TR)).ifEmpty { "?" }
        return Ui.text(a, t, 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = Ui.circle(primary)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    }

    private fun chips(vararg s: String): View {
        val row = Ui.hbox(a)
        s.filter { it.isNotEmpty() }.forEach { row.addView(Ui.chip(a, it), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }) }
        return HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    // ============================================================ Kişiler

    fun renderPeople(host: FrameLayout) {
        val box = scrollPage(host)
        val all = data.people
        val today = LocalDate.now().toString()
        box.addView(chips(
            a.getString(R.string.adm_total, all.size),
            a.getString(R.string.adm_active, all.count { !it.passive }),
            a.getString(R.string.adm_today_in, all.count { it.last.startsWith(today) }),
            a.getString(R.string.adm_no_pwd, all.count { it.pwd.isEmpty() })
        ), Ui.matchWrap())
        if (data.crashCount > 0) {
            box.addView(Ui.text(a, a.getString(R.string.adm_crash_banner, data.crashCount), 14f, 0xFF8A4B00.toInt(), true).apply {
                background = Ui.rounded(a, 0x33FFB300, 14, 0x99FFB300.toInt(), 1)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { openCrashes() }
            }, Ui.matchWrap(10, a))
        }
        val search = Ui.input(a, a.getString(R.string.adm_search), query)
        box.addView(search, Ui.matchWrap(10, a))
        box.addView(addButton(R.string.adm_add_person) { editPerson(null) }, Ui.matchWrap(8, a))
        val list = Ui.vbox(a)
        box.addView(list, Ui.matchWrap(8, a))
        fun fill() {
            list.removeAllViews()
            val q = fold(query)
            visible = all.filter { q.isEmpty() || fold(it.name + " " + it.code + " " + it.group).contains(q) }
                .sortedWith(compareBy({ it.passive }, { it.name.lowercase(Format.TR) }))
            if (visible.isEmpty()) list.addView(Ui.muted(a, a.getString(if (all.isEmpty()) R.string.adm_people_empty else R.string.adm_not_found), 14f).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(20))
            }, Ui.matchWrap())
            visible.forEach { list.addView(personCard(it), Ui.matchWrap().apply { bottomMargin = dp(8) }) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); fill() }
        })
        fill()
    }

    private fun fold(s: String) = s.lowercase(Format.TR).replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
        .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c').trim()

    private fun personCard(p: Person): View {
        val card = Ui.hbox(a).apply {
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = Ui.card(a)
            isClickable = true
            isFocusable = true
            setOnClickListener { openPerson(p.code) }
            alpha = if (p.passive) 0.6f else 1f
        }
        card.addView(avatar(p.name.ifBlank { p.code }))
        val col = Ui.vbox(a)
        val top = Ui.hbox(a)
        top.addView(Ui.text(a, p.name.ifBlank { p.code }, 16f, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, Ui.weight1())
        if (p.restricted) top.addView(Ui.chip(a, a.getString(R.string.adm_restricted), 0x33FFB300, 0xFF8A4B00.toInt()), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        if (p.passive) top.addView(Ui.chip(a, a.getString(R.string.adm_passive), Ui.withAlpha(red, 40), red), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        col.addView(top)
        col.addView(Ui.muted(a, a.getString(R.string.adm_code_line, p.code) + if (p.group.isNotEmpty()) "  ·  " + p.group else "", 13f), Ui.matchWrap(2, a))
        col.addView(Ui.text(a, if (p.pwd.isNotEmpty()) a.getString(R.string.adm_pwd_line, p.pwd) else a.getString(R.string.adm_pwd_none), 13f,
            Ui.color(a, R.color.accent_text), true), Ui.matchWrap(2, a))
        col.addView(Ui.muted(a, a.getString(R.string.adm_last_line, if (p.last.isBlank()) a.getString(R.string.adm_never) else Format.stamp(p.last)), 13f), Ui.matchWrap(2, a))
        col.addView(chips(
            a.getString(R.string.adm_chip_today, Format.duration(p.today)),
            a.getString(R.string.adm_chip_total, Format.duration(p.total)),
            if (p.read > 0) a.getString(R.string.adm_chip_read, Format.duration(p.read)) else "",
            if (p.notes > 0) a.getString(R.string.adm_chip_notes, p.notes) else ""
        ), Ui.matchWrap(6, a))
        card.addView(col, Ui.weight1().apply { marginStart = dp(12) })
        return card
    }

    // ------------------------------------------------------------ kişi ayrıntısı

    fun renderPerson(host: FrameLayout, code: String) {
        val box = scrollPage(host)
        val list = visible.ifEmpty { data.people }
        val idx = list.indexOfFirst { it.code == code }
        val p = list.getOrNull(idx) ?: data.people.firstOrNull { it.code == code }

        // Kişiler arası geçiş
        val nav = Ui.hbox(a)
        val prev = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = a.getString(R.string.adm_prev)
            isEnabled = idx > 0
            setOnClickListener { openPerson(list[idx - 1].code) }
        }
        val next = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = a.getString(R.string.adm_next)
            isEnabled = idx >= 0 && idx < list.size - 1
            setOnClickListener { openPerson(list[idx + 1].code) }
        }
        nav.addView(prev)
        nav.addView(Ui.muted(a, if (idx >= 0) "${idx + 1} / ${list.size}" else "", 14f).apply { gravity = Gravity.CENTER }, Ui.weight1())
        nav.addView(next)
        box.addView(nav, Ui.matchWrap())

        if (p == null) {
            box.addView(Ui.muted(a, a.getString(R.string.adm_not_found), 14f), Ui.matchWrap(16, a))
            return
        }
        val info = Ui.vbox(a).apply { background = Ui.card(a); setPadding(dp(16), dp(14), dp(16), dp(14)) }
        val head = Ui.hbox(a)
        head.addView(avatar(p.name.ifBlank { p.code }, 52))
        val hc = Ui.vbox(a)
        hc.addView(Ui.text(a, p.name.ifBlank { p.code }, 20f, bold = true))
        hc.addView(Ui.muted(a, a.getString(R.string.adm_code_line, p.code) + if (p.group.isNotEmpty()) "  ·  " + p.group else "", 14f))
        head.addView(hc, Ui.weight1().apply { marginStart = dp(12) })
        info.addView(head)
        fun line(label: Int, value: String, strong: Boolean = false) {
            val r = Ui.hbox(a).apply { setPadding(0, dp(5), 0, dp(5)) }
            r.addView(Ui.muted(a, a.getString(label), 14f), Ui.weight1())
            r.addView(Ui.text(a, value, 15f, if (strong) Ui.color(a, R.color.accent_text) else Ui.color(a, R.color.on_background), strong))
            info.addView(r, Ui.matchWrap())
        }
        info.addView(View(a).apply { setBackgroundColor(Ui.color(a, R.color.outline)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(6) })
        line(R.string.adm_f_pwd, p.pwd.ifEmpty { a.getString(R.string.adm_pwd_none_short) }, true)
        line(R.string.adm_f_status, a.getString(if (p.passive) R.string.adm_passive else R.string.adm_active_one))
        line(R.string.adm_f_device, a.getString(if (p.registered) R.string.adm_device_yes else R.string.adm_device_no))
        line(R.string.adm_f_since, if (p.since.isBlank()) "—" else Format.stamp(p.since))
        line(R.string.adm_f_last, if (p.last.isBlank()) a.getString(R.string.adm_never) else Format.stamp(p.last))
        line(R.string.adm_f_count, p.count.toString())
        line(R.string.adm_f_today, Format.duration(p.today))
        line(R.string.adm_f_week, Format.duration(p.week))
        line(R.string.adm_f_total, Format.duration(p.total))
        line(R.string.adm_f_read, Format.duration(p.read))
        // Görebilir / Göremez tiki
        val access = SwitchMaterial(a).apply {
            setText(R.string.adm_access)
            isChecked = !p.restricted
            setTextColor(Ui.color(a, R.color.on_background))
            minHeight = dp(48)
        }
        access.setOnCheckedChangeListener { v, on ->
            v.isEnabled = false
            call(JSONObject().put("action", "access").put("target", p.code).put("restricted", !on)) { r ->
                v.isEnabled = true
                if (r?.optBoolean("ok") == true) {
                    Toast.makeText(a, if (on) R.string.adm_access_on else R.string.adm_access_off, Toast.LENGTH_SHORT).show()
                    reload()
                } else {
                    v.setOnCheckedChangeListener(null); v.isChecked = !on
                    Toast.makeText(a, errText(r), Toast.LENGTH_LONG).show()
                }
            }
        }
        info.addView(access, Ui.matchWrap(8, a))
        info.addView(Ui.muted(a, a.getString(R.string.adm_access_desc), 12f))
        val btns = Ui.hbox(a)
        btns.addView(MaterialButton(a).apply {
            setText(R.string.adm_edit)
            setOnClickListener { editPerson(p) }
        }, Ui.weight1())
        if (p.pwd.isNotEmpty()) btns.addView(MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.adm_copy_pwd)
            setOnClickListener {
                (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("şifre", p.pwd))
                Toast.makeText(a, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }, Ui.weight1().apply { marginStart = dp(8) })
        info.addView(btns, Ui.matchWrap(12, a))
        box.addView(info, Ui.matchWrap(10, a))

        val detail = Ui.vbox(a)
        box.addView(detail, Ui.matchWrap())
        detail.addView(Ui.muted(a, a.getString(R.string.loading), 13f), Ui.matchWrap(12, a))
        val token = ++personToken
        call(JSONObject().put("action", "person").put("target", p.code)) { r ->
            if (token != personToken) return@call
            detail.removeAllViews()
            if (r == null || !r.optBoolean("ok")) {
                detail.addView(Ui.muted(a, a.getString(R.string.err_network), 13f), Ui.matchWrap(12, a))
                return@call
            }
            personDetail(detail, r)
        }
    }

    private fun personDetail(box: LinearLayout, r: JSONObject) {
        fun section(title: Int, empty: Int, arr: org.json.JSONArray?, row: (JSONObject) -> View) {
            box.addView(Ui.section(a, a.getString(title)))
            if (arr == null || arr.length() == 0) {
                box.addView(Ui.muted(a, a.getString(empty), 13f))
                return
            }
            val card = Ui.vbox(a).apply { background = Ui.card(a); setPadding(dp(14), dp(6), dp(14), dp(6)) }
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let {
                    if (i > 0) card.addView(View(a).apply { setBackgroundColor(Ui.color(a, R.color.outline)) }, LinearLayout.LayoutParams(-1, dp(1)))
                    card.addView(row(it), Ui.matchWrap())
                }
            }
            box.addView(card, Ui.matchWrap())
        }
        fun twoCol(left: String, right: String, sub: String = ""): View {
            val v = Ui.vbox(a).apply { setPadding(0, dp(8), 0, dp(8)) }
            val h = Ui.hbox(a)
            h.addView(Ui.text(a, left, 15f), Ui.weight1())
            h.addView(Ui.text(a, right, 14f, Ui.color(a, R.color.accent_text), true))
            v.addView(h)
            if (sub.isNotEmpty()) v.addView(Ui.muted(a, sub, 12f))
            return v
        }
        val days = r.optJSONArray("days")
        section(R.string.adm_s_days, R.string.adm_s_days_empty, days) { d ->
            val date = Format.isoDate(d.optString("date"))
            twoCol(if (date != null) Format.relDay(date) + " · " + Format.dayName(date.dayOfWeek) else d.optString("date"), Format.duration(d.optLong("sec")))
        }
        section(R.string.adm_s_reading, R.string.adm_s_reading_empty, r.optJSONArray("reading")) { d ->
            twoCol(d.optInt("sure").toString() + ". " + d.optString("name"), Format.duration(d.optLong("sec")),
                a.getString(R.string.adm_last_read, Format.stamp(d.optString("last"))))
        }
        val colors = intArrayOf(0, 0xFFFFE066.toInt(), 0xFF7CC67C.toInt(), 0xFF6AA9E6.toInt())
        section(R.string.adm_s_marks, R.string.adm_s_marks_empty, r.optJSONArray("marks")) { d ->
            val v = Ui.vbox(a).apply { setPadding(0, dp(8), 0, dp(8)) }
            val h = Ui.hbox(a)
            val vur = d.optInt("vurgu")
            if (vur in 1..3) h.addView(View(a).apply { background = Ui.circle(colors[vur]) }, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(8) })
            h.addView(Ui.text(a, d.optString("name") + " " + d.optInt("sure") + ":" + d.optInt("ayet"), 15f, bold = true), Ui.weight1())
            h.addView(Ui.muted(a, Format.stamp(d.optString("tarih")), 12f))
            v.addView(h)
            val n = d.optString("not")
            if (n.isNotBlank()) v.addView(Ui.text(a, n, 14f), Ui.matchWrap(4, a))
            v
        }
        section(R.string.adm_s_plans, R.string.adm_s_plans_empty, r.optJSONArray("plans")) { d ->
            val date = Format.isoDate(d.optString("date"))
            val v = Ui.vbox(a).apply { setPadding(0, dp(8), 0, dp(8)) }
            v.addView(Ui.text(a, (if (date != null) Format.dayLong(date) else d.optString("date")) +
                d.optString("time").let { if (it.isNotEmpty()) "  ·  $it" else "" }, 13f, 0xFFE08600.toInt(), true))
            v.addView(Ui.text(a, d.optString("not"), 15f), Ui.matchWrap(2, a))
            v
        }
        section(R.string.adm_s_edu, R.string.adm_s_edu_empty, r.optJSONArray("eduNotes")) { d ->
            val v = Ui.vbox(a).apply { setPadding(0, dp(8), 0, dp(8)) }
            val date = Format.isoDate(d.optString("date"))
            v.addView(Ui.text(a, d.optString("title"), 15f, bold = true))
            v.addView(Ui.muted(a, (if (date != null) Format.dayLong(date) else d.optString("date")) + " · " + a.getString(R.string.adm_written, Format.stamp(d.optString("at"))), 12f))
            v.addView(Ui.text(a, d.optString("not"), 14f), Ui.matchWrap(4, a))
            v
        }
        section(R.string.adm_s_logins, R.string.adm_s_logins_empty, r.optJSONArray("logins")) { d ->
            twoCol(Format.stamp(d.optString("at")), d.optString("ev"))
        }
    }

    // ------------------------------------------------------------ kişi ekle / düzenle

    private fun editPerson(p: Person?) {
        val box = Ui.vbox(a).apply { setPadding(dp(20), dp(4), dp(20), dp(8)) }
        box.addView(Ui.label(a, a.getString(R.string.adm_f_code)))
        val code = Ui.input(a, a.getString(R.string.adm_f_code_hint), p?.code ?: "")
        box.addView(code, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_name)))
        val name = Ui.input(a, a.getString(R.string.adm_f_name), p?.name ?: "", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        box.addView(name, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_group)))
        val group = Ui.input(a, a.getString(R.string.adm_f_group_hint), p?.group ?: "")
        box.addView(group, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_pwd)))
        val pwd = Ui.input(a, a.getString(R.string.adm_f_pwd_hint), p?.pwd ?: "", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        box.addView(pwd, Ui.matchWrap())
        val active = SwitchMaterial(a).apply {
            setText(R.string.adm_f_active)
            isChecked = p?.passive != true
            setTextColor(Ui.color(a, R.color.on_background))
            minHeight = dp(48)
        }
        box.addView(active, Ui.matchWrap(8, a))
        val see = SwitchMaterial(a).apply {
            setText(R.string.adm_access)
            isChecked = p?.restricted != true
            setTextColor(Ui.color(a, R.color.on_background))
            minHeight = dp(48)
        }
        box.addView(see, Ui.matchWrap())
        val reset = CheckBox(a).apply {
            setText(R.string.adm_f_reset)
            setTextColor(Ui.color(a, R.color.on_background))
            visibility = if (p != null && p.registered) View.VISIBLE else View.GONE
        }
        box.addView(reset, Ui.matchWrap())
        val msg = Ui.text(a, "", 13f, red, true)
        box.addView(msg, Ui.matchWrap(6, a))
        val d = HomePage.secure(AlertDialog.Builder(a)
            .setTitle(if (p == null) R.string.adm_add_person else R.string.adm_edit_person)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create())
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
            val c = code.text.toString().trim()
            val n = name.text.toString().trim()
            val w = pwd.text.toString().trim()
            when {
                c.length < 2 -> { msg.setText(R.string.adm_err_code); return@setOnClickListener }
                n.isEmpty() -> { msg.setText(R.string.adm_err_name); return@setOnClickListener }
                w.isNotEmpty() && w.length < 4 -> { msg.setText(R.string.err_short); return@setOnClickListener }
            }
            btn.isEnabled = false
            msg.setText(R.string.saving)
            val body = JSONObject().put("action", "personSave").put("orig", p?.code ?: "").put("pcode", c).put("name", n)
                .put("group", group.text.toString().trim()).put("ppwd", w).put("passive", !active.isChecked).put("resetDevice", reset.isChecked)
                .put("restricted", !see.isChecked)
            call(body) { r ->
                btn.isEnabled = true
                if (r?.optBoolean("ok") == true) {
                    d.dismiss()
                    Toast.makeText(a, R.string.saved, Toast.LENGTH_SHORT).show()
                    reload()
                } else msg.text = errText(r)
            }
        }
    }

    // ============================================================ Eğitimler

    fun renderEvents(host: FrameLayout) {
        val box = scrollPage(host)
        box.addView(addButton(R.string.adm_add_event) { editEvent(null) }, Ui.matchWrap())
        val today = LocalDate.now()
        val upcoming = data.events.filter { !it.date.isBefore(today) }.sortedWith(compareBy({ it.date }, { it.startMin }))
        val past = data.events.filter { it.date.isBefore(today) }.sortedWith(compareByDescending<Event> { it.date }.thenByDescending { it.startMin })
        box.addView(Ui.section(a, a.getString(R.string.adm_upcoming)))
        if (upcoming.isEmpty()) box.addView(Ui.muted(a, a.getString(R.string.adm_events_empty), 14f))
        upcoming.forEach { box.addView(eventCard(it), Ui.matchWrap().apply { bottomMargin = dp(8) }) }
        box.addView(Ui.section(a, a.getString(R.string.adm_past)))
        if (past.isEmpty()) box.addView(Ui.muted(a, a.getString(R.string.adm_events_empty), 14f))
        past.forEach { box.addView(eventCard(it), Ui.matchWrap().apply { bottomMargin = dp(8) }) }
    }

    private fun eventCard(e: Event): View {
        val card = Ui.vbox(a).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = if (e.date == LocalDate.now()) Ui.rounded(a, Ui.withAlpha(primary, 30), 18, primary, 1) else Ui.card(a)
            isClickable = true
            setOnClickListener { eventDetail(e) }
        }
        card.addView(Ui.text(a, Format.relDay(e.date) + " · " + Format.dayName(e.date.dayOfWeek) + if (e.start != null) "  " + e.timeText() else "",
            13f, Ui.color(a, R.color.accent_text), true))
        card.addView(Ui.text(a, e.title, 16f, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, Ui.matchWrap(2, a))
        card.addView(chips(
            if (e.who.isBlank()) a.getString(R.string.adm_everyone) else a.getString(R.string.adm_who, e.who),
            if (e.zoom.isNotEmpty()) "🎥 Zoom" else a.getString(R.string.adm_no_zoom),
            a.getString(R.string.adm_note_count, e.notes)
        ), Ui.matchWrap(6, a))
        return card
    }

    private fun eventDetail(e: Event) {
        val box = Ui.vbox(a).apply { setPadding(dp(20), dp(4), dp(20), dp(8)) }
        box.addView(Ui.text(a, Format.dayLong(e.date) + if (e.start != null) "  ·  " + e.timeText() else "", 14f, Ui.color(a, R.color.accent_text), true))
        box.addView(Ui.muted(a, if (e.who.isBlank()) a.getString(R.string.adm_everyone) else a.getString(R.string.adm_who, e.who), 13f), Ui.matchWrap(2, a))
        box.addView(Ui.text(a, e.desc.ifBlank { a.getString(R.string.edu_no_text) }, 15f).apply { setLineSpacing(0f, 1.2f) }, Ui.matchWrap(10, a))
        box.addView(Ui.muted(a, if (e.zoomRaw.isNotEmpty()) a.getString(R.string.adm_zoom_line, e.zoomRaw) else a.getString(R.string.adm_no_zoom), 12f), Ui.matchWrap(8, a))
        box.addView(Ui.section(a, a.getString(R.string.adm_event_notes)))
        val notes = Ui.vbox(a)
        notes.addView(Ui.muted(a, a.getString(R.string.loading), 13f))
        box.addView(notes, Ui.matchWrap())
        val d = HomePage.secure(AlertDialog.Builder(a)
            .setTitle(e.title)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.adm_edit) { _, _ -> editEvent(e) }
            .setNeutralButton(R.string.delete) { _, _ -> confirmDelete(a.getString(R.string.adm_delete_event_q, e.title)) {
                call(JSONObject().put("action", "eventDelete").put("row", e.row).put("key", e.key)) { r ->
                    if (r?.optBoolean("ok") == true) { Toast.makeText(a, R.string.deleted, Toast.LENGTH_SHORT).show(); reload() }
                    else Toast.makeText(a, errText(r), Toast.LENGTH_LONG).show()
                }
            } }
            .setNegativeButton(R.string.close, null)
            .create())
        call(JSONObject().put("action", "eventNotes").put("date", e.date.toString()).put("title", e.title)) { r ->
            if (!d.isShowing) return@call
            notes.removeAllViews()
            val arr = r?.optJSONArray("notes")
            if (r == null || !r.optBoolean("ok")) { notes.addView(Ui.muted(a, a.getString(R.string.err_network), 13f)); return@call }
            if (arr == null || arr.length() == 0) { notes.addView(Ui.muted(a, a.getString(R.string.adm_event_notes_empty), 13f)); return@call }
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i) ?: continue
                val c = Ui.vbox(a).apply { background = Ui.card(a); setPadding(dp(12), dp(8), dp(12), dp(8)) }
                c.addView(Ui.text(a, n.optString("name").ifBlank { n.optString("code") }, 14f, bold = true))
                c.addView(Ui.muted(a, Format.stamp(n.optString("at")), 12f))
                c.addView(Ui.text(a, n.optString("not"), 14f), Ui.matchWrap(4, a))
                notes.addView(c, Ui.matchWrap().apply { bottomMargin = dp(6) })
            }
        }
    }

    private fun editEvent(e: Event?) {
        var date: LocalDate = e?.date ?: LocalDate.now()
        var start: LocalTime? = e?.start ?: LocalTime.of(20, 0)
        var end: LocalTime? = e?.end ?: LocalTime.of(21, 0)
        val box = Ui.vbox(a).apply { setPadding(dp(20), dp(4), dp(20), dp(8)) }
        box.addView(Ui.label(a, a.getString(R.string.adm_f_date)))
        val dateBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun showDate() { dateBtn.text = Format.dayLong(date) + " " + date.year }
        showDate()
        dateBtn.setOnClickListener {
            DatePickerDialog(a, { _, y, m, dd -> date = LocalDate.of(y, m + 1, dd); showDate() }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        box.addView(dateBtn, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_time)))
        val times = Ui.hbox(a)
        val sBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val eBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun showTimes() {
            sBtn.text = start?.let { Format.time(it) } ?: a.getString(R.string.adm_no_time)
            eBtn.text = end?.let { Format.time(it) } ?: a.getString(R.string.adm_no_time)
        }
        showTimes()
        sBtn.setOnClickListener {
            val t = start ?: LocalTime.of(20, 0)
            TimePickerDialog(a, { _, h, m -> start = LocalTime.of(h, m); if (end != null && !end!!.isAfter(start)) end = start!!.plusHours(1); showTimes() }, t.hour, t.minute, true).show()
        }
        eBtn.setOnClickListener {
            val t = end ?: (start ?: LocalTime.of(20, 0)).plusHours(1)
            TimePickerDialog(a, { _, h, m -> end = LocalTime.of(h, m); showTimes() }, t.hour, t.minute, true).show()
        }
        sBtn.setOnLongClickListener { start = null; end = null; showTimes(); true }
        times.addView(sBtn, Ui.weight1())
        times.addView(Ui.muted(a, "–", 16f).apply { setPadding(dp(8), 0, dp(8), 0) })
        times.addView(eBtn, Ui.weight1())
        box.addView(times, Ui.matchWrap())
        box.addView(Ui.muted(a, a.getString(R.string.adm_time_hint), 11f))
        box.addView(Ui.label(a, a.getString(R.string.adm_f_title)))
        val title = Ui.input(a, a.getString(R.string.adm_f_title), e?.title ?: "", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        box.addView(title, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_text)))
        val text = Ui.input(a, a.getString(R.string.adm_f_text_hint), e?.desc ?: "", multiline = true)
        box.addView(text, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_zoom)))
        val zoom = Ui.input(a, "https://us05web.zoom.us/j/...", e?.zoomRaw ?: "", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        box.addView(zoom, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_who)))
        val who = Ui.input(a, a.getString(R.string.adm_f_who_hint), e?.who ?: "")
        box.addView(who, Ui.matchWrap())
        val msg = Ui.text(a, "", 13f, red, true)
        box.addView(msg, Ui.matchWrap(6, a))
        val d = HomePage.secure(AlertDialog.Builder(a)
            .setTitle(if (e == null) R.string.adm_add_event else R.string.adm_edit_event)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create())
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
            val t = title.text.toString().trim()
            val z = zoom.text.toString().trim()
            if (t.isEmpty()) { msg.setText(R.string.adm_err_title); return@setOnClickListener }
            if (z.isNotEmpty() && !z.startsWith("https://", true)) { msg.setText(R.string.adm_err_link); return@setOnClickListener }
            btn.isEnabled = false
            msg.setText(R.string.saving)
            val body = JSONObject().put("action", "eventSave").put("row", e?.row ?: 0).put("key", e?.key ?: "")
                .put("date", date.toString()).put("start", start?.let { Format.time(it) } ?: "").put("end", if (start == null) "" else end?.let { Format.time(it) } ?: "")
                .put("title", t).put("zoom", z).put("desc", text.text.toString()).put("who", who.text.toString().trim())
            call(body) { r ->
                btn.isEnabled = true
                if (r?.optBoolean("ok") == true) {
                    d.dismiss()
                    Toast.makeText(a, R.string.saved, Toast.LENGTH_SHORT).show()
                    reload()
                } else msg.text = errText(r)
            }
        }
    }

    // ============================================================ Bildirimler

    fun renderNotices(host: FrameLayout) {
        val box = scrollPage(host)
        box.addView(addButton(R.string.adm_add_notice) { editNotice() }, Ui.matchWrap())
        box.addView(Ui.section(a, a.getString(R.string.notices)))
        if (data.notices.isEmpty()) box.addView(Ui.muted(a, a.getString(R.string.notice_empty), 14f))
        val today = LocalDate.now().toString()
        data.notices.forEach { n ->
            val expired = n.until.isNotEmpty() && n.until < today
            val future = n.date.isNotEmpty() && n.date > today
            val card = Ui.vbox(a).apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = Ui.card(a)
                alpha = if (expired) 0.55f else 1f
            }
            val top = Ui.hbox(a)
            top.addView(Ui.text(a, n.title, 16f, bold = true), Ui.weight1())
            top.addView(Ui.text(a, a.getString(R.string.delete), 13f, red, true).apply {
                setPadding(dp(10), dp(6), dp(4), dp(6))
                setOnClickListener {
                    confirmDelete(a.getString(R.string.adm_delete_notice_q, n.title)) {
                        call(JSONObject().put("action", "noticeDelete").put("row", n.row).put("key", n.key)) { r ->
                            if (r?.optBoolean("ok") == true) { Toast.makeText(a, R.string.deleted, Toast.LENGTH_SHORT).show(); reload() }
                            else Toast.makeText(a, errText(r), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
            card.addView(top)
            if (n.msg.isNotEmpty() && n.msg != n.title) card.addView(Ui.text(a, n.msg, 14f), Ui.matchWrap(4, a))
            val d = Format.isoDate(n.date)
            card.addView(chips(
                if (d != null) Format.relDay(d) else "",
                if (n.who.isBlank()) a.getString(R.string.adm_everyone) else a.getString(R.string.adm_who, n.who),
                when {
                    expired -> a.getString(R.string.adm_expired)
                    future -> a.getString(R.string.adm_future)
                    n.until.isNotEmpty() -> a.getString(R.string.adm_until, Format.isoDate(n.until)?.let { Format.relDay(it) } ?: n.until)
                    else -> ""
                }
            ), Ui.matchWrap(6, a))
            box.addView(card, Ui.matchWrap().apply { bottomMargin = dp(8) })
        }
    }

    private fun editNotice() {
        var until: LocalDate? = null
        val box = Ui.vbox(a).apply { setPadding(dp(20), dp(4), dp(20), dp(8)) }
        box.addView(Ui.label(a, a.getString(R.string.adm_f_ntitle)))
        val title = Ui.input(a, a.getString(R.string.adm_f_ntitle), type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        box.addView(title, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_msg)))
        val msgIn = Ui.input(a, a.getString(R.string.adm_f_msg), multiline = true)
        box.addView(msgIn, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_who)))
        val who = Ui.input(a, a.getString(R.string.adm_f_who_hint))
        box.addView(who, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_until)))
        val uBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { setText(R.string.adm_until_none) }
        uBtn.setOnClickListener {
            val t = until ?: LocalDate.now().plusDays(7)
            DatePickerDialog(a, { _, y, m, dd -> until = LocalDate.of(y, m + 1, dd); uBtn.text = Format.dayLong(until!!) }, t.year, t.monthValue - 1, t.dayOfMonth).show()
        }
        uBtn.setOnLongClickListener { until = null; uBtn.setText(R.string.adm_until_none); true }
        box.addView(uBtn, Ui.matchWrap())
        val msg = Ui.text(a, "", 13f, red, true)
        box.addView(msg, Ui.matchWrap(6, a))
        val d = HomePage.secure(AlertDialog.Builder(a)
            .setTitle(R.string.adm_add_notice)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.adm_send, null)
            .setNegativeButton(R.string.cancel, null)
            .create())
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
            val t = title.text.toString().trim()
            val m = msgIn.text.toString().trim()
            if (t.isEmpty() && m.isEmpty()) { msg.setText(R.string.adm_err_title); return@setOnClickListener }
            btn.isEnabled = false
            msg.setText(R.string.saving)
            call(JSONObject().put("action", "noticeSave").put("title", t).put("msg", m).put("who", who.text.toString().trim())
                .put("until", until?.toString() ?: "")) { r ->
                btn.isEnabled = true
                if (r?.optBoolean("ok") == true) {
                    d.dismiss()
                    Toast.makeText(a, R.string.saved, Toast.LENGTH_SHORT).show()
                    reload()
                } else msg.text = errText(r)
            }
        }
    }

    // ============================================================ Hata raporları

    fun renderCrashes(host: FrameLayout) {
        val box = scrollPage(host)
        box.addView(Ui.muted(a, a.getString(R.string.adm_crash_info), 13f))
        val list = Ui.vbox(a)
        box.addView(list, Ui.matchWrap(8, a))
        list.addView(Ui.muted(a, a.getString(R.string.loading), 13f))
        call(JSONObject().put("action", "crashes")) { r ->
            list.removeAllViews()
            val arr = r?.optJSONArray("crashes")
            if (r == null || !r.optBoolean("ok")) { list.addView(Ui.muted(a, a.getString(R.string.err_network), 13f)); return@call }
            if (arr == null || arr.length() == 0) { list.addView(Ui.muted(a, a.getString(R.string.adm_crash_empty), 14f)); return@call }
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val card = Ui.vbox(a).apply { background = Ui.card(a); setPadding(dp(14), dp(10), dp(14), dp(10)) }
                val who = c.optString("name").ifBlank { c.optString("code").ifBlank { "—" } }
                card.addView(Ui.text(a, who + "  ·  " + c.optString("app"), 15f, bold = true))
                card.addView(Ui.muted(a, listOf(Format.stamp(c.optString("at")), c.optString("version").let { if (it.isNotEmpty()) "sürüm $it" else "" },
                    c.optString("device")).filter { it.isNotEmpty() }.joinToString("  ·  "), 12f))
                val full = c.optString("report")
                val short = full.lines().take(4).joinToString("\n")
                val body = Ui.text(a, short, 12f).apply { typeface = android.graphics.Typeface.MONOSPACE }
                card.addView(body, Ui.matchWrap(6, a))
                if (full.length > short.length) {
                    var open = false
                    card.setOnClickListener { open = !open; body.text = if (open) full else short }
                }
                list.addView(card, Ui.matchWrap().apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun confirmDelete(q: String, yes: () -> Unit) {
        HomePage.secure(AlertDialog.Builder(a).setMessage(q)
            .setPositiveButton(R.string.delete) { _, _ -> yes() }
            .setNegativeButton(R.string.cancel, null).create())
    }
}
