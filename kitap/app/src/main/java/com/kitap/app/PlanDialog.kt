package com.kitap.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executor

/**
 * Kişisel not ekle / düzenle: "12:00 kitap okuyacağım" gibi. Notun saatinde telefona hatırlatma gelir;
 * yönetim de Üye Yönetim'de görür.
 */
object PlanDialog {
    fun show(a: Activity, io: Executor, plan: Plan?, defaultDate: LocalDate, done: (List<Plan>) -> Unit, authError: () -> Unit) {
        var date = plan?.date ?: defaultDate
        var time: LocalTime? = if (plan != null) plan.time else LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        val dp = { v: Int -> Ui.dp(a, v) }
        val box = Ui.vbox(a).apply { setPadding(dp(20), dp(4), dp(20), dp(8)) }
        box.addView(Ui.muted(a, a.getString(R.string.plan_info), 13f))
        box.addView(Ui.label(a, a.getString(R.string.adm_f_date)))
        val dateBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun showDate() { dateBtn.text = Format.dayLong(date) }
        showDate()
        dateBtn.setOnClickListener {
            DatePickerDialog(a, { _, y, m, d -> date = LocalDate.of(y, m + 1, d); showDate() }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        box.addView(dateBtn, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.adm_f_time)))
        val row = Ui.hbox(a)
        val timeBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun showTime() { timeBtn.text = time?.let { Format.time(it) } ?: a.getString(R.string.plan_no_time) }
        showTime()
        timeBtn.setOnClickListener {
            val t = time ?: LocalTime.of(12, 0)
            TimePickerDialog(a, { _, h, m -> time = LocalTime.of(h, m); showTime() }, t.hour, t.minute, true).show()
        }
        row.addView(timeBtn, Ui.weight1())
        row.addView(MaterialButton(a, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            setText(R.string.plan_clear_time)
            setOnClickListener { time = null; showTime() }
        })
        box.addView(row, Ui.matchWrap())
        box.addView(Ui.label(a, a.getString(R.string.plan_text)))
        val text = Ui.input(a, a.getString(R.string.plan_hint), plan?.text ?: "", multiline = true).apply { minLines = 3 }
        box.addView(text, Ui.matchWrap())
        val msg = Ui.text(a, "", 13f, 0xFFC0392B.toInt(), true)
        box.addView(msg, Ui.matchWrap(6, a))

        val b = AlertDialog.Builder(a)
            .setTitle(if (plan == null) R.string.plan_add else R.string.plan_edit)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (plan != null) b.setNeutralButton(R.string.delete, null)
        val d = HomePage.secure(b.create())

        fun send(body: JSONObject, btn: android.widget.Button, okMsg: Int) {
            val id = Account.identity(a) ?: return
            btn.isEnabled = false
            msg.setText(R.string.saving)
            io.execute {
                val r = Api.authed(id, body)
                a.runOnUiThread {
                    if (a.isDestroyed) return@runOnUiThread
                    btn.isEnabled = true
                    when {
                        r?.optBoolean("ok") == true -> {
                            d.dismiss()
                            Toast.makeText(a, okMsg, Toast.LENGTH_SHORT).show()
                            done(HomeData.list(r.optJSONArray("plans")) { Plan.parse(it) })
                        }
                        r?.optString("error") == "auth" -> { d.dismiss(); authError() }
                        r?.optString("error") == "changed" -> msg.setText(R.string.adm_err_changed)
                        else -> msg.setText(R.string.err_save)
                    }
                }
            }
        }

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
            val t = text.text.toString().trim()
            if (t.isEmpty()) { msg.setText(R.string.plan_err_empty); return@setOnClickListener }
            send(JSONObject().put("action", "planSave").put("row", plan?.row ?: 0).put("at", plan?.at ?: "")
                .put("date", date.toString()).put("time", time?.let { Format.time(it) } ?: "").put("not", t),
                btn as android.widget.Button, R.string.plan_saved)
        }
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { btn ->
            if (plan == null) return@setOnClickListener
            send(JSONObject().put("action", "planDelete").put("row", plan.row).put("at", plan.at), btn as android.widget.Button, R.string.deleted)
        }
    }
}
