package com.kitap.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Türkçe tarih / saat / süre metinleri. */
object Format {
    val TR: Locale = Locale.forLanguageTag("tr-TR")
    private val MONTHS = arrayOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    private val DAYS = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    val SHORT_DAYS = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun month(m: Int) = MONTHS[m - 1]
    fun dayName(d: DayOfWeek) = DAYS[d.value - 1]
    fun time(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    /** "27 Kasım Cuma" */
    fun dayLong(d: LocalDate) = "${d.dayOfMonth} ${month(d.monthValue)} ${dayName(d.dayOfWeek)}"

    /** "Kasım 2026" */
    fun monthYear(d: LocalDate) = "${month(d.monthValue)} ${d.year}"

    /** "Bugün", "Dün", "27 Kasım" */
    fun relDay(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Bugün"
        today.minusDays(1) -> "Dün"
        today.plusDays(1) -> "Yarın"
        else -> "${d.dayOfMonth} ${month(d.monthValue)}"
    }

    /** Sunucu zaman damgası ("2026-11-27 14:05:00") → "Bugün 14:05" */
    fun stamp(s: String): String {
        if (s.isBlank()) return "—"
        return runCatching {
            val t = LocalDateTime.parse(s.trim(), STAMP)
            relDay(t.toLocalDate()) + " " + time(t.toLocalTime())
        }.getOrDefault(s)
    }

    /** 3725 sn → "1 sa 2 dk" */
    fun duration(sec: Long): String {
        if (sec < 60) return if (sec <= 0) "0 dk" else "1 dk"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return when {
            h == 0L -> "$m dk"
            m == 0L -> "$h sa"
            else -> "$h sa $m dk"
        }
    }

    fun isoDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}
