package com.kitap.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Apps Script sunucusuyla konuşur. Ağ işlemi yapar: ana iş parçacığında ÇAĞRILMAMALI.
 * Yanıt { ok: true, ... } ya da { ok: false, error }; ağ hatasında null.
 */
object Api {
    const val URL =
        "https://script.google.com/macros/s/AKfycbwcMDIUkU1SGwVILRlAUpuKTExvJzwZ-xKz_oWLhZt9fSfJlTugYgkZgsBCdHDDTpIoVg/exec"

    /** Hangi uygulama: Üye Yönetim "y", Kitap "k" (sunucu kişiyi buna göre "Yöneticiler"/"Kişiler"de arar). */
    val APP: String = if (BuildConfig.ADMIN) "y" else "k"

    fun post(body: JSONObject): JSONObject? = postRaw(body)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun postRaw(body: JSONObject): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 40_000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                instanceFollowRedirects = true
            }
            conn.outputStream.use { it.write(body.put("app", APP).toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun call(action: String, build: JSONObject.() -> Unit = {}): JSONObject? =
        post(JSONObject().put("action", action).apply(build))

    fun JSONObject.id(a: Account.Identity): JSONObject {
        put("code", a.code); put("uuid", a.uuid); put("pwd", a.pwd)
        return this
    }

    fun check(code: String) = call("check") { put("code", code) }
    fun register(code: String, pwd: String) = call("register") { put("code", code); put("pwd", pwd) }
    fun relogin(code: String, pwd: String, uuid: String?) = call("relogin") { put("code", code); put("pwd", pwd); put("uuid", uuid ?: "") }
    fun login(a: Account.Identity) = call("login") { id(a) }
    fun usage(a: Account.Identity, seconds: Long) = call("usage") { id(a); put("seconds", seconds) }
    fun home(a: Account.Identity) = call("home") { id(a) }

    /** Kimlik eklenmiş genel çağrı (Kitap sayfası ve yönetim ekranları için). */
    fun authed(a: Account.Identity, body: JSONObject): JSONObject? = post(body.id(a))
    fun authedRaw(a: Account.Identity, body: JSONObject): String? = postRaw(body.id(a))
}
