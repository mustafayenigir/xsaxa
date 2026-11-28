package com.kitap.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

/**
 * Uygulama içi güncelleme: yeni sürüm varsa "Güncelle" düğmesi çıkar; yeni sürüm uygulamanın
 * içinde iner, Android'in kurulum ekranında "Güncelle"ye basılınca eski sürümün üzerine kurulur
 * (bilgiler silinmez).
 */
object Updater {
    private const val VERSION_URL = "https://github.com/mustafayenigir/xsaxa/releases/download/kitap-android/version.json"
    private const val PREFS = "kitap_update"
    private const val AUTO_EVERY_MS = 3 * 3600_000L
    private var pendingApk: File? = null
    private var busy = false

    private fun open(url: String): HttpURLConnection {
        var u = URL(url)
        repeat(5) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: return c
                c.disconnect()
                u = URL(u, loc)
            } else return c
        }
        return u.openConnection() as HttpURLConnection
    }

    /** Ağ işi. Yeni sürüm varsa (kod, ad, apk linki). */
    private fun latest(): Triple<Int, String, String>? = try {
        val c = open("$VERSION_URL?t=${System.currentTimeMillis() / 60000}")
        val txt = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val j = JSONObject(txt)
        val url = j.optString(if (BuildConfig.ADMIN) "yonetim" else "kitap")
        Triple(j.optInt("code"), j.optString("name"), url)
    } catch (_: Throwable) {
        null
    }

    /** manual=false: en fazla 3 saatte bir, sessizce bakar. */
    fun check(a: Activity, io: Executor, manual: Boolean) {
        if (busy) return
        val sp = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!manual && System.currentTimeMillis() - sp.getLong("last", 0L) < AUTO_EVERY_MS) return
        sp.edit().putLong("last", System.currentTimeMillis()).apply()
        if (manual) Toast.makeText(a, R.string.upd_checking, Toast.LENGTH_SHORT).show()
        io.execute {
            val v = latest()
            a.runOnUiThread {
                if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                when {
                    v == null -> if (manual) Toast.makeText(a, R.string.err_network, Toast.LENGTH_LONG).show()
                    v.first > BuildConfig.VERSION_CODE && v.third.startsWith("https://") -> offer(a, io, v.second, v.third)
                    manual -> Toast.makeText(a, a.getString(R.string.upd_none, BuildConfig.VERSION_NAME), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun offer(a: Activity, io: Executor, name: String, url: String) {
        HomePage.secure(AlertDialog.Builder(a)
            .setTitle(R.string.upd_title)
            .setMessage(a.getString(R.string.upd_text, BuildConfig.VERSION_NAME, name))
            .setPositiveButton(R.string.upd_button) { _, _ -> download(a, io, url) }
            .setNegativeButton(R.string.later, null)
            .create())
    }

    private fun download(a: Activity, io: Executor, url: String) {
        busy = true
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = true }
        val box = LinearLayout(a).apply { setPadding(Ui.dp(a, 24), Ui.dp(a, 8), Ui.dp(a, 24), 0); addView(bar, LinearLayout.LayoutParams(-1, -2)) }
        val d = HomePage.secure(AlertDialog.Builder(a).setTitle(R.string.upd_downloading).setView(box).setCancelable(false).create())
        io.execute {
            val dir = File(a.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "guncelleme.apk")
            var ok = false
            try {
                val c = open(url)
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; a.runOnUiThread { bar.isIndeterminate = false; bar.progress = pct } }
                            }
                        }
                    }
                }
                c.disconnect()
                ok = out.length() > 100_000
            } catch (_: Throwable) {
            }
            a.runOnUiThread {
                busy = false
                runCatching { d.dismiss() }
                if (!ok) { Toast.makeText(a, R.string.upd_failed, Toast.LENGTH_LONG).show(); return@runOnUiThread }
                pendingApk = out
                install(a)
            }
        }
    }

    /** İzin ekranından dönünce de çağrılır (MainActivity.onResume). */
    fun install(a: Activity) {
        val f = pendingApk ?: return
        if (!f.exists()) { pendingApk = null; return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.packageManager.canRequestPackageInstalls()) {
            HomePage.secure(AlertDialog.Builder(a)
                .setTitle(R.string.upd_perm_title)
                .setMessage(R.string.upd_perm_text)
                .setPositiveButton(R.string.upd_perm_button) { _, _ ->
                    runCatching { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName))) }
                }
                .setNegativeButton(R.string.cancel) { _, _ -> pendingApk = null }
                .create())
            return
        }
        pendingApk = null
        try {
            val uri = FileProvider.getUriForFile(a, a.packageName + ".files", f)
            a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            Toast.makeText(a, R.string.upd_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun hasPendingInstall() = pendingApk != null
}
