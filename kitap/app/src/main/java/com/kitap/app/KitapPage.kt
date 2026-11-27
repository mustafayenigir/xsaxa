package com.kitap.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.Executor

/**
 * "Kitap" sekmesi: Kur'an-ı Kerim meali (Excel'deki "Kuran" sayfası). Ekran uygulamanın içindeki
 * kitap.html'dir; sunucuya istekleri bu köprü kimlikle birlikte iletir.
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
class KitapPage(private val a: Activity, private val io: Executor, private val onAuthError: () -> Unit) {
    val web = WebView(a)
    private var loaded = false
    private var dark = false

    init {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setSupportZoom(false)
        s.allowContentAccess = false
        web.isVerticalScrollBarEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith("file:///android_asset/")

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loaded = true
                applyTheme()
            }
        }
    }

    fun load() {
        if (web.url == null) web.loadUrl("file:///android_asset/kitap.html")
    }

    fun setTextZoom(z: Int) { web.settings.textZoom = z }

    fun setDark(d: Boolean) {
        dark = d
        applyTheme()
    }

    private fun applyTheme() {
        if (loaded) web.evaluateJavascript("window.kitapTema && window.kitapTema(${if (dark) "true" else "false"})", null)
    }

    fun setVisible(v: Boolean) {
        if (loaded) web.evaluateJavascript("window.kitapGorunur && window.kitapGorunur(${if (v) "true" else "false"})", null)
    }

    /** Geri tuşu: okuyucu içinde geri gidilebildiyse true. */
    fun back(done: (Boolean) -> Unit) {
        if (!loaded) return done(false)
        web.evaluateJavascript("window.kitapGeri ? window.kitapGeri() : 'false'") { v -> done(v?.contains("true") == true) }
    }

    inner class Bridge {
        @JavascriptInterface
        fun call(id: String, json: String) {
            io.execute {
                val id2 = Account.identity(a)
                val body = runCatching { JSONObject(json) }.getOrNull()
                val r = if (id2 != null && body != null) Api.authedRaw(id2, body) else null
                if (r != null && r.contains("\"error\":\"auth\"")) a.runOnUiThread { onAuthError() }
                a.runOnUiThread {
                    if (!a.isDestroyed) web.evaluateJavascript("window.__kitapCevap(${JSONObject.quote(id)}, ${JSONObject.quote(r ?: "")})", null)
                }
            }
        }

        @JavascriptInterface
        fun copy(text: String) {
            a.runOnUiThread {
                val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Kitap", text))
            }
        }

        @JavascriptInterface
        fun getPref(k: String): String = Account.pref(a, k)

        @JavascriptInterface
        fun setPref(k: String, v: String) = Account.setPref(a, k, v)
    }
}
