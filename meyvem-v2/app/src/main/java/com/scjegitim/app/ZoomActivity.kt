package com.scjegitim.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * DENEME (V2): Zoom toplantısına, Zoom uygulaması kurulu olmadan, Zoom'un web sürümüyle
 * uygulamanın içinden katılma. Ekran görüntüsü engeli burada da geçerli.
 */
class ZoomActivity : AppCompatActivity() {

    companion object {
        /** Sabit toplantı. "/j/" yerine "/wc/join/" doğrudan tarayıcı sürümünü açar (uygulamaya yönlendirmez). */
        const val JOIN_URL = "https://us05web.zoom.us/wc/join/88170373640?pwd=NGLhQOdez5wVLIw4pgJygAVlDCxy2k.1"

        /** Toplantı açıkken ana ekrandaki "ses kaydı algılandı" koruması kendi mikrofonumuzu kayıt sanmasın. */
        @Volatile var active = false
        @Volatile private var endedAt = 0L

        /** Toplantı açık mı (ya da mikrofon yeni kapandı mı)? */
        fun inMeeting() = active || android.os.SystemClock.elapsedRealtime() - endedAt < 3000

        // Zoom web sürümü telefon tarayıcısında "uygulamayı indir" sayfasına yönlendirebiliyor;
        // masaüstü Chrome gibi görünerek doğrudan web istemcisini istiyoruz.
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private fun isZoom(host: String?) = host != null && (host == "zoom.us" || host.endsWith(".zoom.us"))
    }

    private lateinit var web: WebView
    private var pending: PermissionRequest? = null

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val req = pending ?: return@registerForActivityResult
        pending = null
        answer(req, showHint = true)
    }

    /** Zoom'un istediği mikrofon/kamera izinlerinden telefonun verdiklerini onaylar. */
    private fun answer(req: PermissionRequest, showHint: Boolean) {
        val ok = req.resources.filter {
            when (it) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> has(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> has(Manifest.permission.CAMERA)
                else -> false
            }
        }
        if (ok.isEmpty()) req.deny() else req.grant(ok.toTypedArray())
        if (showHint && ok.size < req.resources.size) {
            Toast.makeText(this, R.string.zoom_perm_denied, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        Palettes.apply(this)
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        MeyvemApp.blockAudioCapture(this)
        active = true

        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val s = request.url.scheme?.lowercase()
                // zoomus://, intent:// gibi "uygulamada aç" yönlendirmeleri engellenir; sayfa içinde kalınır
                return s != "https" && s != "http"
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (!isZoom(request.origin?.host)) {
                    request.deny()
                    return
                }
                val need = mutableListOf<String>()
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources && !has(Manifest.permission.RECORD_AUDIO)) {
                    need += Manifest.permission.RECORD_AUDIO
                }
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources && !has(Manifest.permission.CAMERA)) {
                    need += Manifest.permission.CAMERA
                }
                if (need.isEmpty()) {
                    answer(request, showHint = false)
                } else {
                    pending?.deny()
                    pending = request
                    permLauncher.launch(need.toTypedArray())
                }
            }
        }
        if (savedInstanceState == null) web.loadUrl(JOIN_URL) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val d = AlertDialog.Builder(this)
            .setMessage(R.string.zoom_leave_confirm)
            .setPositiveButton(R.string.zoom_leave) { _, _ -> finish() }
            .setNegativeButton(R.string.zoom_stay, null)
            .create()
        d.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        d.show()
    }

    override fun onDestroy() {
        active = false
        endedAt = android.os.SystemClock.elapsedRealtime()
        pending?.deny()
        web.loadUrl("about:blank")
        web.destroy()
        super.onDestroy()
    }
}
