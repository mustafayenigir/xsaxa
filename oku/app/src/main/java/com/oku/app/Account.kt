package com.oku.app

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Bu cihazdaki kimlik ve önbellek. Şifrenin kendisi saklanmaz; yalnızca tuzlu özeti (token) tutulur
 * ve sunucuya da yalnızca o gider.
 */
object Account {
    private const val PREFS = "oku_account"
    data class Identity(val no: String, val uuid: String, val token: String)

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Kayıt adımları -------------------------------------------------------
    fun pendingNo(c: Context): String? = p(c).getString("pending_no", null)
    fun setPendingNo(c: Context, no: String?) =
        p(c).edit().apply { if (no == null) remove("pending_no") else putString("pending_no", no) }.apply()

    fun googleDone(c: Context) = BuildConfig.PARENT || p(c).getBoolean("google_done", false)
    fun setGoogleDone(c: Context) = p(c).edit().putBoolean("google_done", true).apply()

    fun identity(c: Context): Identity? {
        val sp = p(c)
        val no = sp.getString("no", null) ?: return null
        val uuid = sp.getString("uuid", null) ?: return null
        val token = sp.getString("token", null) ?: return null
        return Identity(no, uuid, token)
    }

    fun name(c: Context): String = p(c).getString("name", "") ?: ""

    fun saveRegistration(c: Context, no: String, uuid: String, salt: String, hash: String, name: String) {
        p(c).edit()
            .putString("no", no).putString("uuid", uuid).putString("token", hash)
            .putString("pwd_salt", salt).putString("name", name)
            .putBoolean("google_done", false)
            .remove("pending_no").remove("home_json")
            .apply()
    }

    /** Sonraki açılışlarda şifre yerelde (ağ olmadan) doğrulanır. */
    fun checkPassword(c: Context, pwd: String): Boolean {
        val sp = p(c)
        val salt = sp.getString("pwd_salt", null) ?: return false
        return hash(pwd, salt) == sp.getString("token", null)
    }

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    fun hash(pwd: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pwd).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // --- Ana sayfa verisi (son başarılı yanıt saklanır; çevrimdışı da açılır) ---
    fun cachedHome(c: Context): HomeData? =
        p(c).getString("home_json", null)?.let { runCatching { HomeData.parse(JSONObject(it)) }.getOrNull() }

    /** Sunucudan tazeler. Ağ işi: arka planda çağırın. Başarısızsa null; "auth" ise kimlik geçersizdir. */
    fun refreshHome(c: Context): Pair<HomeData?, String?> {
        val id = identity(c) ?: return null to "auth"
        val r = Api.home(id) ?: return null to "network"
        val data = HomeData.parse(r) ?: return null to r.optString("error", "server")
        p(c).edit().putString("home_json", r.toString()).putString("name", data.name).apply()
        return data to null
    }

    // --- Okunan bildirimler -----------------------------------------------------
    fun readIds(c: Context): Set<String> = p(c).getStringSet("read_ids", emptySet()) ?: emptySet()
    fun markRead(c: Context, id: String) {
        val now = readIds(c)
        if (id in now) return
        // Çok büyümesin: en fazla 400 kimlik
        val next = (now + id).toList().takeLast(400).toSet()
        p(c).edit().putStringSet("read_ids", next).apply()
    }

    // --- Uygulamada geçen süre (öğrenci; sunucuya toplu gönderilir) -------------
    private val lock = Any()
    private val flushLock = Any()

    fun addPendingSeconds(c: Context, s: Long) {
        if (s <= 0 || BuildConfig.PARENT) return
        synchronized(lock) {
            val sp = p(c)
            sp.edit().putLong("pending_sec", sp.getLong("pending_sec", 0L) + s).apply()
        }
    }

    fun flushUsage(c: Context) {
        if (BuildConfig.PARENT) return
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
