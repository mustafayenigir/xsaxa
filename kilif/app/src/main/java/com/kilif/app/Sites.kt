package com.kilif.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class Site(val name: String, val url: String, val desc: String)

/** Kullanıcının kendi eklediği siteler (yalnızca bu cihazda saklanır). */
object Sites {
    private const val PREFS = "kilif_sites"
    private val HOST = Regex("^[\\p{L}\\p{N}.-]+$")

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(c: Context): List<Site> {
        val raw = p(c).getString("sites", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (normalizeUrl(url) == null) null
                else Site(o.optString("name").ifBlank { defaultName(url) }, url, o.optString("desc"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(c: Context, list: List<Site>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("desc", it.desc)) }
        p(c).edit().putString("sites", arr.toString()).apply()
    }

    fun selectedUrl(c: Context): String? = p(c).getString("selected", null)
    fun setSelected(c: Context, url: String?) {
        p(c).edit().putString("selected", url).apply()
    }

    /** Açılacak site: seçili olan, yoksa listedeki ilk site; liste boşsa null. */
    fun siteToOpen(c: Context): String? {
        val l = list(c)
        val sel = selectedUrl(c)
        return (l.firstOrNull { it.url == sel } ?: l.firstOrNull())?.url
    }

    /** "ornek.com" gibi yazılan adresi https://ornek.com biçimine getirir; geçersizse null. */
    fun normalizeUrl(input: String?): String? {
        var t = input?.trim() ?: return null
        if (t.isEmpty() || t.contains(' ')) return null
        t = when {
            t.startsWith("https://", ignoreCase = true) -> "https://" + t.substring(8)
            t.startsWith("http://", ignoreCase = true) -> "https://" + t.substring(7)
            t.contains("://") -> return null
            else -> "https://$t"
        }
        val host = Uri.parse(t).host ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.') || !HOST.matches(host)) return null
        return t
    }

    fun defaultName(url: String): String =
        (Uri.parse(url).host ?: url).removePrefix("www.")

    private fun sameSite(a: String, b: String) = a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)

    /** Yeni site ekler ya da [oldUrl] verilmişse o siteyi günceller. Aynı adres listede varsa false. */
    fun put(c: Context, site: Site, oldUrl: String? = null): Boolean {
        val l = list(c).toMutableList()
        if (l.any { sameSite(it.url, site.url) && (oldUrl == null || !sameSite(it.url, oldUrl)) }) return false
        val i = if (oldUrl == null) -1 else l.indexOfFirst { it.url == oldUrl }
        if (i >= 0) l[i] = site else l.add(site)
        save(c, l)
        if (oldUrl != null && selectedUrl(c) == oldUrl) setSelected(c, site.url)
        return true
    }

    fun remove(c: Context, url: String) {
        save(c, list(c).filter { it.url != url })
        if (selectedUrl(c) == url) setSelected(c, null)
    }
}
