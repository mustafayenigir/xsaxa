package com.scjegitim.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sitenin "web site linkleri" sayfasındaki bir satır. */
data class SiteLink(val name: String, val url: String, val desc: String)

/**
 * Bu cihazdaki kullanıcı kimliği ve uygulama durumu. Yalnızca uygulamanın özel alanında tutulur.
 * Şifrenin kendisi saklanmaz; sunucuya ve burada yalnızca tuzlu özeti (token) gider.
 */
object Account {
    private const val PREFS = "meyvem_account"
    private const val DEFAULT_SITE = "https://scjegitim.blogspot.com"
    const val DEFAULT_URL = DEFAULT_SITE

    data class Identity(val no: String, val uuid: String, val token: String)

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Adım durumu ---------------------------------------------------------
    fun pendingNo(c: Context): String? = p(c).getString("pending_no", null)
    fun setPendingNo(c: Context, no: String?) {
        p(c).edit().apply { if (no == null) remove("pending_no") else putString("pending_no", no) }.apply()
    }

    fun gmailDone(c: Context) = p(c).getBoolean("gmail_done", false)
    fun setGmailDone(c: Context) { p(c).edit().putBoolean("gmail_done", true).apply() }

    /** Kayıt tamamlandıysa kimlik; aksi halde null. */
    fun identity(c: Context): Identity? {
        val sp = p(c)
        val no = sp.getString("no", null) ?: return null
        val uuid = sp.getString("uuid", null) ?: return null
        val token = sp.getString("token", null) ?: return null
        return Identity(no, uuid, token)
    }

    fun saveRegistration(c: Context, no: String, uuid: String, token: String) {
        p(c).edit()
            .putString("no", no).putString("uuid", uuid).putString("token", token)
            .putBoolean("gmail_done", false)
            .remove("pending_no")
            .apply()
    }

    // --- Site seçimi ---------------------------------------------------------
    fun selectedUrl(c: Context): String? = p(c).getString("site_url", null)?.takeIf { isHttps(it) }
    fun setSelectedUrl(c: Context, url: String?) {
        p(c).edit().apply { if (url == null) remove("site_url") else putString("site_url", url) }.apply()
    }

    fun cachedLinks(c: Context): List<SiteLink> = parseLinks(p(c).getString("links_json", null))
    fun setCachedLinks(c: Context, links: List<SiteLink>) {
        val arr = JSONArray()
        links.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("desc", it.desc)) }
        p(c).edit().putString("links_json", arr.toString()).apply()
    }

    fun parseLinks(json: String?): List<SiteLink> {
        if (json.isNullOrBlank()) return emptyList()
        return try { parseLinks(JSONArray(json)) } catch (_: Throwable) { emptyList() }
    }

    fun parseLinks(arr: JSONArray?): List<SiteLink> {
        if (arr == null) return emptyList()
        val out = ArrayList<SiteLink>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (!isHttps(url)) continue
            out.add(SiteLink(o.optString("name").ifBlank { url }, url, o.optString("desc")))
        }
        return out
    }

    fun isHttps(url: String?): Boolean =
        url != null && url.startsWith("https://", ignoreCase = true) && url.length > 10

    /** Açılacak adres: seçili → listenin ilki → varsayılan. */
    fun siteToOpen(c: Context): String {
        selectedUrl(c)?.let { return it }
        return cachedLinks(c).firstOrNull()?.url ?: DEFAULT_SITE
    }

    /** Sunucudan listeyi yeniler ve önbelleğe yazar. Ağ işlemi yapar: arka planda çağırın. */
    fun refreshLinks(c: Context): List<SiteLink>? {
        val id = identity(c) ?: return null
        val r = Api.links(id) ?: return null
        if (!r.optBoolean("ok")) return null
        val list = parseLinks(r.optJSONArray("links"))
        setCachedLinks(c, list)
        return list
    }

    // --- Uygulamada geçirilen süre (sunucuya toplu gönderilir) ----------------
    private val lock = Any()

    fun addPendingSeconds(c: Context, s: Long) {
        if (s <= 0) return
        synchronized(lock) {
            val sp = p(c)
            sp.edit().putLong("pending_sec", sp.getLong("pending_sec", 0L) + s).apply()
        }
    }

    fun pendingSeconds(c: Context): Long = synchronized(lock) { p(c).getLong("pending_sec", 0L) }

    /** Başarıyla gönderilen kadarını düşer (arada eklenenler korunur). */
    fun consumePendingSeconds(c: Context, sent: Long) {
        synchronized(lock) {
            val sp = p(c)
            sp.edit().putLong("pending_sec", (sp.getLong("pending_sec", 0L) - sent).coerceAtLeast(0L)).apply()
        }
    }

    /** Birikmiş süreyi sunucuya yollar. Arka plan iş parçacığında çağırın. */
    fun flushUsage(c: Context) {
        // Aynı anda iki gönderim aynı süreyi iki kez yazmasın
        synchronized(flushLock) {
            val id = identity(c) ?: return
            val pending = pendingSeconds(c)
            if (pending <= 0) return
            val chunk = minOf(pending, 7200L)
            val r = Api.usage(id, chunk) ?: return
            if (r.optBoolean("ok")) consumePendingSeconds(c, chunk)
        }
    }

    private val flushLock = Any()
}
