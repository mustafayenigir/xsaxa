package com.kitap.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Eğitimin Zoom linkini telefondaki Zoom uygulamasında açar (link kullanıcıya gösterilmez). */
object ZoomOpen {
    data class Meeting(val number: String, val password: String)

    /** https://us05web.zoom.us/j/88170373640?pwd=... → numara + şifre */
    fun parse(link: String): Meeting? {
        val u = runCatching { Uri.parse(link.trim()) }.getOrNull() ?: return null
        val host = u.host?.lowercase() ?: return null
        if (host != "zoom.us" && !host.endsWith(".zoom.us")) return null
        val seg = u.pathSegments
        val i = seg.indexOfFirst { it == "j" || it == "w" || it == "s" }
        val no = (if (i >= 0) seg.getOrNull(i + 1) else null)?.filter { it.isDigit() } ?: return null
        if (no.length !in 9..12) return null
        return Meeting(no, u.getQueryParameter("pwd").orEmpty())
    }

    fun open(a: Activity, link: String) {
        val m = parse(link)
        if (m != null) {
            val deep = Uri.parse("zoomus://zoom.us/join?confno=${m.number}" + if (m.password.isNotEmpty()) "&pwd=${Uri.encode(m.password)}" else "")
            try {
                a.startActivity(Intent(Intent.ACTION_VIEW, deep).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Throwable) {
            }
        }
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            Toast.makeText(a, R.string.zoom_open_failed, Toast.LENGTH_LONG).show()
        }
    }
}
