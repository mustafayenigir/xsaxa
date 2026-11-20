package com.scjegitim.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Apps Script sunucusuyla konuşur. Tüm çağrılar ağ işlemi yaptığı için
 * ana iş parçacığında (UI) ÇAĞRILMAMALIDIR.
 *
 * Her çağrı { ok: true, ... } veya { ok: false, error: "invalid|busy|auth|bad_request|server" }
 * döndürür. Ağ hatası / bozuk yanıt olursa null döner.
 */
object Api {
    const val URL =
        "https://script.google.com/macros/s/AKfycbyUwCt2kyUA4npnPIaqJztXCNsq6B0anGvRWaW5UwfHKBhHbgGadagfocj-KH2jeSYt/exec"

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 15000

    fun post(body: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                // text/plain: tarayıcı tarafındaki CORS ön kontrolünü gerektirmez; Apps Script bunu okur.
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                // Apps Script yanıtı 302 ile googleusercontent.com adresine yönlendirir;
                // HttpURLConnection bunu otomatik (GET olarak) izler.
                instanceFollowRedirects = true
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
            JSONObject(text)
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun call(action: String, build: JSONObject.() -> Unit): JSONObject? =
        post(JSONObject().put("action", action).apply(build))

    private fun JSONObject.identity(a: Account.Identity) {
        put("no", a.no); put("uuid", a.uuid); put("token", a.token)
    }

    fun check(no: String) = call("check") { put("no", no) }

    fun register(no: String, pwdHash: String) = call("register") {
        put("no", no); put("pwdHash", pwdHash)
    }

    fun login(id: Account.Identity) = call("login") { identity(id) }

    fun usage(id: Account.Identity, seconds: Long) = call("usage") {
        identity(id); put("seconds", seconds)
    }

    fun links(id: Account.Identity) = call("links") { identity(id) }
}
