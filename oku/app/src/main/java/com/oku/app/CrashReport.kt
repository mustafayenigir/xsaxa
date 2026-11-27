package com.oku.app

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File

/**
 * Çökme kaydı: uygulama kapanırsa sebebi bir sonraki açılışta ekranda gösterilir
 * (kopyalayıp bana gönderebilirsiniz). Java hataları + sistemin "süreç sonlandı" kaydı (yerel çökmeler dahil).
 */
object CrashReport {
    private fun file(c: Context) = File(c.filesDir, "crash.txt")
    private fun marker(c: Context) = File(c.filesDir, "zoom_step.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(app).writeText("Java çökmesi (${t.name}), adım: ${step(app)}\n" + android.util.Log.getStackTraceString(e).take(6000))
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }

    /** Zoom adımlarını not alır: çökme hangi adımda oldu bilinsin. */
    fun step(c: Context, s: String? = null): String {
        return try {
            if (s != null) { marker(c).writeText(s); s } else if (marker(c).exists()) marker(c).readText() else ""
        } catch (_: Throwable) { "" }
    }

    private fun systemExit(c: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        return try {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = am.getHistoricalProcessExitReasons(c.packageName, 0, 3).firstOrNull() ?: return ""
            val bad = last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                last.reason == ApplicationExitInfo.REASON_CRASH ||
                last.reason == ApplicationExitInfo.REASON_ANR ||
                last.reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
                last.reason == ApplicationExitInfo.REASON_SIGNALED ||
                last.reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
            if (!bad) return ""
            val trace = try { last.traceInputStream?.bufferedReader()?.readText()?.take(3500).orEmpty() } catch (_: Throwable) { "" }
            "Sistem kaydı: sebep=${last.reason}, açıklama=${last.description}, durum=${last.status}\n$trace"
        } catch (_: Throwable) { "" }
    }

    /** Açılışta: önceki çalıştırmada çökme olduysa göster. */
    fun showIfAny(a: Activity) {
        val c = a.applicationContext
        var text = ""
        val f = file(c)
        if (f.exists()) {
            text = try { f.readText() } catch (_: Throwable) { "" }
            f.delete()
        }
        if (text.isBlank()) {
            val sys = systemExit(c)
            val st = step(c)
            if (sys.isNotBlank()) text = "Son adım: $st\n$sys"
        }
        step(c, "")
        runCatching { marker(c).delete() }
        if (text.isBlank()) return
        val msg = text.take(5000)
        AlertDialog.Builder(a)
            .setTitle("Önceki kapanmanın sebebi")
            .setMessage(msg)
            .setPositiveButton("Kopyala") { _, _ ->
                val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", msg))
                Toast.makeText(a, "Kopyalandı", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Kapat", null)
            .show()
    }
}
