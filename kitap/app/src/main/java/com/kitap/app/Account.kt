package com.kitap.app

import android.content.Context
import org.json.JSONObject

/**
 * Bu cihazdaki kimlik ve önbellek. Şifre (Excel'de de açıkça durduğu için) burada saklanır;
 * her istekte kod + cihaz + şifre birlikte doğrulanır. Yönetim şifreyi değiştirirse
 * uygulama bir sonraki açılışta yeni şifreyi ister.
 */
object Account {
    private const val PREFS = "kitap_account"
    data class Identity(val code: String, val uuid: String, val pwd: String)

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Kayıt adımları -------------------------------------------------------
    fun pendingCode(c: Context): String? = p(c).getString("pending_code", null)
    fun pendingHasPassword(c: Context): Boolean = p(c).getBoolean("pending_has_pwd", false)
    fun pendingName(c: Context): String = p(c).getString("pending_name", "") ?: ""
    fun setPending(c: Context, code: String?, hasPassword: Boolean = false, name: String = "") {
        p(c).edit().apply {
            if (code == null) remove("pending_code").remove("pending_has_pwd").remove("pending_name")
            else putString("pending_code", code).putBoolean("pending_has_pwd", hasPassword).putString("pending_name", name)
        }.apply()
    }

    fun identity(c: Context): Identity? {
        val sp = p(c)
        val code = sp.getString("code", null) ?: return null
        val uuid = sp.getString("uuid", null) ?: return null
        val pwd = sp.getString("pwd", null) ?: return null
        return Identity(code, uuid, pwd)
    }

    /** Eski cihaz kimliği (yeniden bağlanırken aynı kalsın diye). */
    fun lastUuid(c: Context): String? = p(c).getString("uuid", null) ?: p(c).getString("old_uuid", null)

    fun name(c: Context): String = p(c).getString("name", "") ?: ""
    fun code(c: Context): String = p(c).getString("code", null) ?: pendingCode(c) ?: ""

    fun save(c: Context, code: String, uuid: String, pwd: String, name: String) {
        p(c).edit()
            .putString("code", code).putString("uuid", uuid).putString("pwd", pwd).putString("name", name)
            .remove("pending_code").remove("pending_has_pwd").remove("pending_name")
            .apply()
    }

    /** Sunucu kimliği reddetti (şifre değişti / cihaz değişti): kodu hatırla, şifreyi yeniden iste. */
    fun invalidate(c: Context) {
        val sp = p(c)
        val code = sp.getString("code", null) ?: return
        sp.edit().putString("old_uuid", sp.getString("uuid", null)).remove("uuid").remove("pwd")
            .putString("pending_code", code).putBoolean("pending_has_pwd", true)
            .putString("pending_name", sp.getString("name", "") ?: "").apply()
    }

    /** Başka bir kodla giriş: her şeyi sil. */
    fun reset(c: Context) {
        p(c).edit().clear().apply()
    }

    fun checkPassword(c: Context, pwd: String): Boolean = pwd.isNotEmpty() && identity(c)?.pwd == pwd

    // --- Ana sayfa verisi (son başarılı yanıt saklanır; çevrimdışı da açılır) ---
    fun cachedHome(c: Context): HomeData? =
        p(c).getString("home_json", null)?.let { runCatching { HomeData.parse(JSONObject(it)) }.getOrNull() }

    fun cacheHome(c: Context, json: String, name: String) = p(c).edit().putString("home_json", json).putString("name", name).apply()

    /** Sunucudan tazeler. Ağ işi: arka planda çağırın. Başarısızsa null; "auth" ise kimlik geçersizdir. */
    fun refreshHome(c: Context): Pair<HomeData?, String?> {
        val id = identity(c) ?: return null to "auth"
        val r = Api.home(id) ?: return null to "network"
        val data = HomeData.parse(r) ?: return null to r.optString("error", "server")
        cacheHome(c, r.toString(), data.name)
        return data to null
    }

    fun cachedAdmin(c: Context): AdminData? =
        p(c).getString("admin_json", null)?.let { runCatching { AdminData.parse(JSONObject(it)) }.getOrNull() }

    fun refreshAdmin(c: Context): Pair<AdminData?, String?> {
        val id = identity(c) ?: return null to "auth"
        val r = Api.authed(id, JSONObject().put("action", "adminData")) ?: return null to "network"
        val data = AdminData.parse(r) ?: return null to r.optString("error", "server")
        p(c).edit().putString("admin_json", r.toString()).putString("name", data.name).apply()
        return data to null
    }

    // --- Okunan bildirimler -----------------------------------------------------
    fun readIds(c: Context): Set<String> = p(c).getStringSet("read_ids", emptySet()) ?: emptySet()
    fun markRead(c: Context, id: String) {
        val now = readIds(c)
        if (id in now) return
        val next = (now + id).toList().takeLast(400).toSet()
        p(c).edit().putStringSet("read_ids", next).apply()
    }

    // --- Kitap sayfasının küçük yerel bilgileri (son okunan sure vb.) ------------
    fun pref(c: Context, k: String): String = p(c).getString("kp_$k", "") ?: ""
    fun setPref(c: Context, k: String, v: String) = p(c).edit().putString("kp_$k", v.take(200)).apply()

    // --- Uygulamada geçen süre (sunucuya toplu gönderilir) ------------------------
    private val lock = Any()
    private val flushLock = Any()

    fun addPendingSeconds(c: Context, s: Long) {
        if (s <= 0 || BuildConfig.ADMIN) return
        synchronized(lock) {
            val sp = p(c)
            sp.edit().putLong("pending_sec", sp.getLong("pending_sec", 0L) + s).apply()
        }
    }

    fun flushUsage(c: Context) {
        if (BuildConfig.ADMIN) return
        synchronized(flushLock) {
            val id = identity(c) ?: return
            val pending = synchronized(lock) { p(c).getLong("pending_sec", 0L) }
            if (pending <= 0) return
            val chunk = minOf(pending, 7200L)
            val r = Api.usage(id, chunk) ?: return
            if (r.optBoolean("ok")) synchronized(lock) {
                val sp = p(c)
                sp.edit().putLong("pending_sec", (sp.getLong("pending_sec", 0L) - chunk).coerceAtLeast(0L)).apply()
            }
        }
    }
}
