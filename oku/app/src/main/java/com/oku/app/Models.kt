package com.oku.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

data class SiteLink(val name: String, val url: String, val desc: String)

/** Takvimdeki bir ders / etkinlik. start null ise saat belirtilmemiş (gün boyu). */
data class Event(
    val id: String,
    val date: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val title: String,
    val zoom: String,
    val desc: String
) {
    val startMin: Int get() = start?.let { it.hour * 60 + it.minute } ?: 0
    val endMin: Int
        get() {
            val e = end?.let { it.hour * 60 + it.minute } ?: (startMin + 60)
            return if (e <= startMin) (startMin + 60).coerceAtMost(24 * 60) else e
        }

    /** "14:00–15:00", "14:00" ya da "" */
    fun timeText(): String = when {
        start == null -> ""
        end == null -> Format.time(start)
        else -> Format.time(start) + "–" + Format.time(end)
    }
}

data class Notice(val id: String, val date: String, val title: String, val msg: String)

data class LoginEntry(val at: String, val ev: String)

/** Velinin gördüğü öğrenci. */
data class Child(
    val no: String,
    val name: String,
    val group: String,
    val found: Boolean,
    val registered: Boolean,
    val since: String,
    val last: String,
    val total: Long,
    val count: Int,
    val today: Long,
    val week: Long,
    val logins: List<LoginEntry>
)

/** Sunucudaki "home" yanıtı: ana sayfa, takvim ve bildirimler için her şey. */
class HomeData(
    val name: String,
    val links: List<SiteLink>,
    val events: List<Event>,
    val notices: List<Notice>,
    val children: List<Child>,
    val zoomInApp: Boolean
) {
    fun eventsOn(d: LocalDate) = events.filter { it.date == d }

    companion object {
        val EMPTY = HomeData("", emptyList(), emptyList(), emptyList(), emptyList(), false)

        fun parse(o: JSONObject?): HomeData? {
            if (o == null || !o.optBoolean("ok")) return null
            return try {
                HomeData(
                    o.optString("name"),
                    list(o.optJSONArray("links")) { j ->
                        val url = j.optString("url")
                        if (url.startsWith("https://", true)) SiteLink(j.optString("name").ifBlank { url }, url, j.optString("desc")) else null
                    },
                    list(o.optJSONArray("events")) { j ->
                        val d = runCatching { LocalDate.parse(j.optString("date")) }.getOrNull() ?: return@list null
                        Event(
                            j.optString("id"), d, time(j.optString("start")), time(j.optString("end")),
                            j.optString("title"), j.optString("zoom"), j.optString("desc")
                        )
                    },
                    list(o.optJSONArray("notices")) { j ->
                        Notice(j.optString("id"), j.optString("date"), j.optString("title"), j.optString("msg"))
                    },
                    list(o.optJSONArray("children")) { j ->
                        Child(
                            j.optString("no"), j.optString("name"), j.optString("group"),
                            j.optBoolean("found", true), j.optBoolean("registered"),
                            j.optString("since"), j.optString("last"), j.optLong("total"), j.optInt("count"),
                            j.optLong("today"), j.optLong("week"),
                            list(j.optJSONArray("logins")) { l -> LoginEntry(l.optString("at"), l.optString("ev")) }
                        )
                    },
                    o.optBoolean("zoom")
                )
            } catch (_: Throwable) {
                null
            }
        }

        private fun time(s: String): LocalTime? = runCatching { if (s.isBlank()) null else LocalTime.parse(s) }.getOrNull()

        private fun <T> list(a: JSONArray?, f: (JSONObject) -> T?): List<T> {
            if (a == null) return emptyList()
            val out = ArrayList<T>()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { j -> f(j)?.let { out.add(it) } }
            return out
        }
    }
}
