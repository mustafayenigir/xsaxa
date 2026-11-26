package com.oku.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Apps Script sunucusuyla konuşur. Ağ işlemi yapar: ana iş parçacığında ÇAĞRILMAMALI.
 * Yanıt { ok: true, ... } ya da { ok: false, error }; ağ hatasında null.
 */
object Api {
    const val URL =
        "https://script.google.com/macros/s/AKfycbyENmWtJ3heRJcg5kJPV5Vvj9c1F9YzpuSQE4g3_O54Dul9TAQLqAmXIl7eImXfZUZhGw/exec"

    /** Bu uygulamanın rolü: Okut (veli) için "p", Oku (öğrenci) için "s". */
    val ROLE: String = if (BuildConfig.PARENT) "p" else "s"

    fun post(body: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                instanceFollowRedirects = true
            }
            conn.outputStream.use { it.write(body.put("role", ROLE).toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
            JSONObject(text)
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun call(action: String, build: JSONObject.() -> Unit = {}): JSONObject? =
        post(JSONObject().put("action", action).apply(build))

    private fun JSONObject.id(a: Account.Identity) {
        put("no", a.no); put("uuid", a.uuid); put("token", a.token)
    }

    fun check(no: String) = call("check") { put("no", no) }
    fun register(no: String, pwdHash: String) = call("register") { put("no", no); put("pwdHash", pwdHash) }
    fun login(a: Account.Identity) = call("login") { id(a) }
    fun usage(a: Account.Identity, seconds: Long) = call("usage") { id(a); put("seconds", seconds) }
    fun home(a: Account.Identity) = call("home") { id(a) }
    fun zoomSig(a: Account.Identity, meetingNumber: String) = call("zoomSig") { id(a); put("meetingNumber", meetingNumber) }
}
