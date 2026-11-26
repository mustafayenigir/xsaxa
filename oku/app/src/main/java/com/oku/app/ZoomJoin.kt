package com.oku.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Takvimdeki Zoom linkine katılma.
 * Önce uygulamanın İÇİNDE (Zoom Meeting SDK, eklendiğinde) denenir; olmazsa telefondaki Zoom
 * uygulaması doğrudan toplantıya açılır (link / numara yazmak gerekmez).
 */
object ZoomJoin {
    data class Meeting(val number: String, val password: String)

    /** https://us05web.zoom.us/j/88170373640?pwd=... → numara + şifre */
    fun parse(link: String): Meeting? {
        val u = runCatching { Uri.parse(link) }.getOrNull() ?: return null
        val host = u.host?.lowercase() ?: return null
        if (host != "zoom.us" && !host.endsWith(".zoom.us")) return null
        val seg = u.pathSegments
        val i = seg.indexOfFirst { it == "j" || it == "join" || it == "w" }
        val num = (if (i >= 0 && i + 1 < seg.size) seg[i + 1] else u.getQueryParameter("confno"))?.filter { it.isDigit() }
        if (num.isNullOrEmpty() || num.length !in 9..12) return null
        return Meeting(num, u.getQueryParameter("pwd").orEmpty())
    }

    /** Uygulama içi katılma (SDK) kuruluysa onu kullanır. */
    var inApp: ((Activity, String, Meeting) -> Boolean)? = null

    fun join(a: Activity, link: String) {
        val m = parse(link)
        if (m != null && inApp?.invoke(a, link, m) == true) return
        openZoomApp(a, link, m)
    }

    fun openZoomApp(a: Activity, link: String, m: Meeting?) {
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
