package com.kitap.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File

/**
 * Hata raporu: uygulama çökerse kişiye pencere GÖSTERİLMEZ. Rapor bir sonraki açılışta sessizce
 * sunucuya gönderilir ve Üye Yönetim'deki "Hata raporları" bölümünde yöneticilere görünür.
 * Kişinin uygulamayı kendisi kapatması rapor sayılmaz; yalnızca gerçek çökmeler gönderilir.
 */
object CrashReport {
    private const val PREFS = "kitap_crash"
    private fun file(c: Context) = File(c.filesDir, "crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(app).writeText("Java çökmesi (${t.name})\n" + android.util.Log.getStackTraceString(e).take(3800))
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }

    /** Sistemin kaydındaki gerçek çökmeler (yerel çökme, donma); son gönderilenden yeni olanlar. */
    private fun systemCrash(c: Context): Pair<String, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = am.getHistoricalProcessExitReasons(c.packageName, 0, 5).firstOrNull() ?: return null
            val real = last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                last.reason == ApplicationExitInfo.REASON_CRASH ||
                last.reason == ApplicationExitInfo.REASON_ANR
            val seen = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_ts", 0L)
            if (!real || last.timestamp <= seen) return null
            val kind = when (last.reason) {
                ApplicationExitInfo.REASON_ANR -> "Uygulama yanıt vermedi (donma)"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Yerel çökme"
                else -> "Çökme"
            }
            val trace = try { last.traceInputStream?.bufferedReader()?.readText()?.take(3000).orEmpty() } catch (_: Throwable) { "" }
            ("$kind: ${last.description.orEmpty()}\n$trace").trim() to last.timestamp
        } catch (_: Throwable) {
            null
        }
    }

    /** Bekleyen raporu sunucuya gönderir. Ağ işi: arka planda çağırın. */
    fun sendPending(c: Context) {
        val app = c.applicationContext
        val f = file(app)
        var text = if (f.exists()) runCatching { f.readText() }.getOrDefault("") else ""
        var ts = 0L
        if (text.isBlank()) {
            val sys = systemCrash(app) ?: return
            text = sys.first
            ts = sys.second
        }
        val body = JSONObject().put("action", "crash").put("report", text.take(4000))
            .put("version", BuildConfig.VERSION_NAME)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        Account.identity(app)?.let { body.put("code", it.code).put("uuid", it.uuid).put("pwd", it.pwd) }
            ?: body.put("code", Account.code(app))
        val r = Api.post(body)
        if (r?.optBoolean("ok") == true || r?.optString("error") == "bad_request") {
            runCatching { f.delete() }
            // sistem kaydındaki çökmeyi de (Java çökmesiyle aynı olay) bir daha gönderme
            val newest = if (ts > 0) ts else System.currentTimeMillis()
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_ts", newest).apply()
        }
    }
}
