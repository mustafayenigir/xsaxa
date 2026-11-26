package com.oku.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Takvim: Ay / Hafta / Gün görünümleri. Bir güne dokununca o günün saatli görünümü açılır;
 * derse dokununca ayrıntı (ve Zoom'a katıl) gösterilir.
 */
@SuppressLint("ViewConstructor")
class CalendarView(context: Context, private val onEvent: (Event) -> Unit) : LinearLayout(context) {

    private var events: List<Event> = emptyList()
    private var mode = AppSettings.calendarMode(context).coerceIn(0, 2)
    private var focus: LocalDate = LocalDate.now()

    private val title: TextView
    private val toggle: MaterialButtonToggleGroup
    private val body = FrameLayout(context)
    private val ids = intArrayOf(View.generateViewId(), View.generateViewId(), View.generateViewId())

    private val primary = Ui.color(context, R.color.primary)
    private val fg = Ui.color(context, R.color.on_background)
    private val muted = Ui.color(context, R.color.on_background_muted)
    private val outline = Ui.color(context, R.color.outline)

    private fun dp(v: Int) = Ui.dp(context, v)

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), 0)

        // Başlık satırı: ‹  Kasım 2026  ›  [Bugün]
        val head = Ui.hbox(context)
        val prev = arrowButton(R.drawable.ic_chevron_left, R.string.cal_prev) { step(-1) }
        val next = arrowButton(R.drawable.ic_chevron_right, R.string.cal_next) { step(1) }
        title = Ui.text(context, "", 19f, fg, true).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val today = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.cal_today)
            minimumHeight = 0
            minHeight = dp(40)
            insetTop = 0; insetBottom = 0
            setOnClickListener { focus = LocalDate.now(); render() }
        }
        head.addView(prev, LayoutParams(dp(44), dp(44)))
        head.addView(title, Ui.weight1())
        head.addView(next, LayoutParams(dp(44), dp(44)))
        head.addView(today, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply { marginStart = dp(6) })
        addView(head, Ui.matchWrap())

        // Ay / Hafta / Gün
        toggle = MaterialButtonToggleGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        intArrayOf(R.string.cal_month, R.string.cal_week, R.string.cal_day).forEachIndexed { i, res ->
            val b = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = ids[i]
                setText(res)
                insetTop = 0; insetBottom = 0
            }
            toggle.addView(b, LayoutParams(0, dp(42), 1f))
        }
        toggle.check(ids[mode])
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val m = ids.indexOf(id)
            if (m >= 0 && m != mode) {
                mode = m
                AppSettings.setCalendarMode(context, m)
                render()
            }
        }
        addView(toggle, Ui.matchWrap(8, context))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        render()
    }

    fun setEvents(list: List<Event>) {
        events = list
        render()
    }

    /** Belirli bir günün saatli görünümünü açar. */
    fun showDay(d: LocalDate) {
        focus = d
        setMode(2)
    }

    private fun setMode(m: Int) {
        if (mode == m) render() else toggle.check(ids[m]) // dinleyici render eder
    }

    private fun arrowButton(icon: Int, desc: Int, click: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        setColorFilter(fg)
        background = null
        contentDescription = context.getString(desc)
        setOnClickListener { click() }
    }

    private fun step(dir: Int) {
        focus = when (mode) {
            0 -> focus.plusMonths(dir.toLong())
            1 -> focus.plusWeeks(dir.toLong())
            else -> focus.plusDays(dir.toLong())
        }
        render()
    }

    private fun render() {
        body.removeAllViews()
        when (mode) {
            0 -> { title.text = Format.monthYear(focus); body.addView(monthView()) }
            1 -> { title.text = weekTitle(); body.addView(weekView()) }
            else -> { title.text = Format.dayLong(focus); body.addView(dayView()) }
        }
    }

    private fun eventsOn(d: LocalDate) = events.filter { it.date == d }.sortedBy { if (it.start == null) -1 else it.startMin }

    // ------------------------------------------------------------ Ay

    private fun monthView(): View {
        val today = LocalDate.now()
        val first = focus.withDayOfMonth(1)
        val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val box = Ui.vbox(context)

        val head = Ui.hbox(context)
        Format.SHORT_DAYS.forEach { d ->
            head.addView(Ui.muted(context, d, 12f).apply { gravity = Gravity.CENTER }, Ui.weight1())
        }
        box.addView(head, Ui.matchWrap())

        var day = start
        for (w in 0 until 6) {
            if (w == 5 && day.month != focus.month) break // 5 haftaya sığan aylarda boş satır olmasın
            val row = Ui.hbox(context)
            for (i in 0 until 7) {
                row.addView(dayCell(day, today), LayoutParams(0, dp(56), 1f))
                day = day.plusDays(1)
            }
            box.addView(row, Ui.matchWrap(2, context))
        }

        // Ay boyunca dersi olan gün yoksa küçük bir not
        if (events.none { it.date.year == focus.year && it.date.month == focus.month }) {
            box.addView(Ui.muted(context, context.getString(R.string.cal_empty_month)).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
            }, Ui.matchWrap())
        }
        return ScrollView(context).apply { addView(box) }
    }

    private fun dayCell(d: LocalDate, today: LocalDate): View {
        val inMonth = d.month == focus.month
        val list = eventsOn(d)
        val cell = Ui.vbox(context).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val num = Ui.text(context, d.dayOfMonth.toString(), 15f, if (inMonth) fg else Ui.withAlpha(muted, 120), d == today).apply {
            gravity = Gravity.CENTER
            if (d == today) {
                setTextColor(0xFFFFFFFF.toInt())
                background = Ui.circle(primary)
            }
        }
        cell.addView(num, LayoutParams(dp(34), dp(34)))
        val dots = Ui.hbox(context).apply { gravity = Gravity.CENTER }
        list.take(3).forEach { _ ->
            dots.addView(View(context).apply { background = Ui.circle(if (inMonth) primary else Ui.withAlpha(primary, 110)) },
                LayoutParams(dp(6), dp(6)).apply { marginStart = dp(1); marginEnd = dp(1); topMargin = dp(3) })
        }
        if (list.size > 3) dots.addView(Ui.muted(context, "+", 10f))
        cell.addView(dots)
        cell.background = Ui.rounded(context, 0, 12)
        cell.isClickable = true
        cell.isFocusable = true
        cell.foreground = context.getDrawable(android.R.drawable.list_selector_background)
        cell.contentDescription = Format.dayLong(d) + if (list.isNotEmpty()) ", ${list.size} ders" else ""
        cell.setOnClickListener { showDay(d) }
        return cell
    }

    // ------------------------------------------------------------ Hafta

    private fun weekStart() = focus.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun weekTitle(): String {
        val s = weekStart()
        val e = s.plusDays(6)
        return if (s.month == e.month) "${s.dayOfMonth}–${e.dayOfMonth} ${Format.month(e.monthValue)}"
        else "${s.dayOfMonth} ${Format.month(s.monthValue)} – ${e.dayOfMonth} ${Format.month(e.monthValue)}"
    }

    private fun weekView(): View {
        val today = LocalDate.now()
        val box = Ui.vbox(context)
        var d = weekStart()
        repeat(7) {
            val day = d
            val row = Ui.hbox(context).apply {
                gravity = Gravity.TOP
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = Ui.rounded(context, if (day == today) Ui.withAlpha(primary, 28) else Ui.color(context, R.color.surface), 16, outline)
                isClickable = true
                setOnClickListener { showDay(day) }
            }
            val badge = Ui.vbox(context).apply { gravity = Gravity.CENTER_HORIZONTAL }
            badge.addView(Ui.muted(context, Format.SHORT_DAYS[day.dayOfWeek.value - 1], 12f).apply { gravity = Gravity.CENTER })
            badge.addView(Ui.text(context, day.dayOfMonth.toString(), 20f, if (day == today) primary else fg, true).apply { gravity = Gravity.CENTER })
            row.addView(badge, LayoutParams(dp(48), LayoutParams.WRAP_CONTENT))

            val col = Ui.vbox(context)
            val list = eventsOn(day)
            if (list.isEmpty()) {
                col.addView(Ui.muted(context, "—", 15f).apply { setPadding(0, dp(8), 0, 0) })
            } else list.forEach { ev -> col.addView(eventChip(ev), Ui.matchWrap(0).apply { bottomMargin = dp(4) }) }
            row.addView(col, Ui.weight1().apply { marginStart = dp(8) })
            box.addView(row, Ui.matchWrap().apply { bottomMargin = dp(8) })
            d = d.plusDays(1)
        }
        return ScrollView(context).apply { addView(box) }
    }

    private fun eventChip(ev: Event): View = Ui.text(
        context,
        (if (ev.start != null) ev.timeText() + "  " else "") + ev.title + if (ev.zoom.isNotEmpty()) "  🎥" else "",
        14f, fg
    ).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        background = Ui.rounded(context, Ui.withAlpha(primary, 34), 10)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setOnClickListener { onEvent(ev) }
    }

    // ------------------------------------------------------------ Gün (saatli)

    private fun dayView(): View {
        val list = eventsOn(focus)
        val box = Ui.vbox(context)
        val timed = list.filter { it.start != null }
        val allDay = list.filter { it.start == null }
        allDay.forEach { box.addView(eventChip(it), Ui.matchWrap().apply { bottomMargin = dp(4) }) }
        if (list.isEmpty()) {
            box.addView(Ui.muted(context, context.getString(R.string.cal_empty_day)).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            }, Ui.matchWrap())
        }

        val startHour = minOf(7, timed.minOfOrNull { it.startMin / 60 } ?: 7)
        val endHour = maxOf(23, timed.maxOfOrNull { (it.endMin + 59) / 60 } ?: 23).coerceAtMost(24)
        val grid = DayGrid(context, startHour, endHour, focus == LocalDate.now())
        val placed = layoutColumns(timed)
        placed.forEach { (ev, col, cols) ->
            val v = Ui.text(context, ev.timeText() + "\n" + ev.title + if (ev.zoom.isNotEmpty()) "  🎥" else "", 13f, 0xFFFFFFFF.toInt(), true).apply {
                background = Ui.rounded(context, primary, 10)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                ellipsize = TextUtils.TruncateAt.END
                contentDescription = ev.timeText() + " " + ev.title
                setOnClickListener { onEvent(ev) }
            }
            grid.add(v, ev.startMin, ev.endMin, col, cols)
        }
        val scroll = ScrollView(context)
        box.addView(grid, Ui.matchWrap())
        scroll.addView(box)
        // İlk derse (yoksa 08:00'e / şu ana) kaydır
        val target = timed.minOfOrNull { it.startMin }
            ?: if (focus == LocalDate.now()) LocalTime.now().let { it.hour * 60 + it.minute } else 8 * 60
        scroll.post { scroll.scrollTo(0, (grid.top + grid.yFor(target) - dp(24)).coerceAtLeast(0)) }
        return scroll
    }

    /** Çakışan dersleri yan yana sütunlara yerleştirir: (ders, sütun, sütun sayısı) */
    private fun layoutColumns(list: List<Event>): List<Triple<Event, Int, Int>> {
        val sorted = list.sortedWith(compareBy({ it.startMin }, { it.endMin }))
        val out = ArrayList<Triple<Event, Int, Int>>()
        var cluster = ArrayList<Pair<Event, Int>>()
        val colEnds = ArrayList<Int>()
        var clusterEnd = -1
        fun flush() {
            val n = colEnds.size.coerceAtLeast(1)
            cluster.forEach { (e, c) -> out.add(Triple(e, c, n)) }
            cluster = ArrayList()
            colEnds.clear()
        }
        sorted.forEach { e ->
            if (e.startMin >= clusterEnd && cluster.isNotEmpty()) flush()
            var col = colEnds.indexOfFirst { it <= e.startMin }
            if (col < 0) { colEnds.add(e.endMin); col = colEnds.size - 1 } else colEnds[col] = e.endMin
            cluster.add(e to col)
            clusterEnd = maxOf(clusterEnd, e.endMin)
        }
        if (cluster.isNotEmpty()) flush()
        return out
    }
}

/** Günlük görünümün saat ızgarası; dersler dakikasına göre yerleştirilir. */
@SuppressLint("ViewConstructor")
class DayGrid(context: Context, private val startHour: Int, private val endHour: Int, private val showNow: Boolean) : ViewGroup(context) {
    private val hourH = Ui.dp(context, 64)
    private val labelW = Ui.dp(context, 50)
    private val minH = Ui.dp(context, 30)
    private val pos = HashMap<View, IntArray>()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.color(context, R.color.outline); strokeWidth = Ui.dpf(context, 1f) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.color(context, R.color.on_background_muted)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val now = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt(); strokeWidth = Ui.dpf(context, 2f) }

    init { setWillNotDraw(false) }

    fun yFor(min: Int): Int = (min - startHour * 60) * hourH / 60 + Ui.dp(context, 8)

    fun add(v: View, startMin: Int, endMin: Int, col: Int, cols: Int) {
        pos[v] = intArrayOf(startMin.coerceAtLeast(startHour * 60), endMin, col, cols)
        addView(v)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (endHour - startHour) * hourH + Ui.dp(context, 16)
        val area = w - labelW - Ui.dp(context, 4)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val p = pos[c] ?: continue
            val cw = area / p[3] - Ui.dp(context, 4)
            val ch = maxOf((p[1] - p[0]) * hourH / 60 - Ui.dp(context, 2), minH)
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val area = width - labelW - Ui.dp(context, 4)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val p = pos[c] ?: continue
            val left = labelW + p[2] * (area / p[3])
            val top = yFor(p[0])
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (h in startHour..endHour) {
            val y = yFor(h * 60).toFloat()
            canvas.drawLine(labelW.toFloat() - Ui.dpf(context, 4f), y, width.toFloat(), y, line)
            if (h < 24) canvas.drawText("%02d:00".format(h), Ui.dpf(context, 4f), y + label.textSize / 3, label)
        }
        if (showNow) {
            val t = LocalTime.now()
            val m = t.hour * 60 + t.minute
            if (m in startHour * 60..endHour * 60) {
                val y = yFor(m).toFloat()
                canvas.drawCircle(labelW.toFloat(), y, Ui.dpf(context, 4f), now)
                canvas.drawLine(labelW.toFloat(), y, width.toFloat(), y, now)
            }
        }
    }
}
