package com.kitap.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

data class SiteLink(val name: String, val url: String, val desc: String)

/** Takvimdeki bir eğitim. start null ise saat belirtilmemiş (gün boyu). */
data class Event(
    val id: String,
    val date: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val title: String,
    val zoom: String,
    val desc: String,
    // yalnızca Üye Yönetim'de dolu
    val row: Int = 0,
    val key: String = "",
    val who: String = "",
    val zoomRaw: String = "",
    val notes: Int = 0
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

    companion object {
        fun parse(j: JSONObject): Event? {
            val d = runCatching { LocalDate.parse(j.optString("date")) }.getOrNull() ?: return null
            return Event(
                j.optString("id"), d, time(j.optString("start")), time(j.optString("end")),
                j.optString("title"), j.optString("zoom"), j.optString("desc"),
                j.optInt("row"), j.optString("key"), j.optString("who"), j.optString("zoomRaw"), j.optInt("notes")
            )
        }

        fun time(s: String): LocalTime? = runCatching { if (s.isBlank()) null else LocalTime.parse(s) }.getOrNull()
    }
}

data class Notice(val id: String, val date: String, val title: String, val msg: String,
                  val row: Int = 0, val key: String = "", val who: String = "", val until: String = "")

/** Sunucudaki "home" yanıtı: ana sayfa, takvim ve bildirimler için her şey. */
class HomeData(
    val name: String,
    val links: List<SiteLink>,
    val events: List<Event>,
    val notices: List<Notice>
) {
    fun eventsOn(d: LocalDate) = events.filter { it.date == d }.sortedBy { if (it.start == null) -1 else it.startMin }

    companion object {
        val EMPTY = HomeData("", emptyList(), emptyList(), emptyList())

        fun parse(o: JSONObject?): HomeData? {
            if (o == null || !o.optBoolean("ok")) return null
            return try {
                HomeData(
                    o.optString("name"),
                    list(o.optJSONArray("links")) { j ->
                        val url = j.optString("url")
                        if (url.startsWith("https://", true)) SiteLink(j.optString("name").ifBlank { url }, url, j.optString("desc")) else null
                    },
                    list(o.optJSONArray("events")) { Event.parse(it) },
                    list(o.optJSONArray("notices")) { notice(it) }
                )
            } catch (_: Throwable) {
                null
            }
        }

        fun notice(j: JSONObject) = Notice(
            j.optString("id"), j.optString("date"), j.optString("title"), j.optString("msg"),
            j.optInt("row"), j.optString("key"), j.optString("who"), j.optString("until")
        )

        fun <T> list(a: JSONArray?, f: (JSONObject) -> T?): List<T> {
            if (a == null) return emptyList()
            val out = ArrayList<T>()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { j -> f(j)?.let { out.add(it) } }
            return out
        }
    }
}

/** Üye Yönetim: bir kişi. */
data class Person(
    val code: String, val name: String, val group: String, val pwd: String, val passive: Boolean, val registered: Boolean,
    val since: String, val last: String, val total: Long, val count: Int, val today: Long, val week: Long,
    val read: Long, val notes: Int, val eduNotes: Int
) {
    companion object {
        fun parse(j: JSONObject) = Person(
            j.optString("code"), j.optString("name"), j.optString("group"), j.optString("pwd"), j.optBoolean("passive"),
            j.optBoolean("registered"), j.optString("since"), j.optString("last"), j.optLong("total"), j.optInt("count"),
            j.optLong("today"), j.optLong("week"), j.optLong("read"), j.optInt("notes"), j.optInt("eduNotes")
        )
    }
}

class AdminData(val name: String, val people: List<Person>, val events: List<Event>, val notices: List<Notice>) {
    companion object {
        val EMPTY = AdminData("", emptyList(), emptyList(), emptyList())
        fun parse(o: JSONObject?): AdminData? {
            if (o == null || !o.optBoolean("ok")) return null
            return try {
                AdminData(
                    o.optString("name"),
                    HomeData.list(o.optJSONArray("people")) { Person.parse(it) },
                    HomeData.list(o.optJSONArray("events")) { Event.parse(it) },
                    HomeData.list(o.optJSONArray("notices")) { HomeData.notice(it) }
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}
