package com.kilif.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    companion object {
        // Tema değişince Activity yeniden oluşur; açık sayfa kaybolmasın.
        private var lastUrl: String? = null

        // Şu an gösterilen sitenin "ana" adresi
        private var siteBase: String? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    // Ses kaydı algılanınca içerik durdurulur (bkz. RecordingWatch)
    private lateinit var recordNotice: View
    private var recCallback: android.media.AudioManager.AudioRecordingCallback? = null
    private var recPaused = false
    private var recResumeUrl: String? = null
    private val recResume = Runnable { resumeAfterRecording() }

    @Volatile private var adBlockOn = true

    // Ana Sayfa ekranı. Logoya dokunulursa RESULT_OK; siteden geri ile gelinip
    // bir daha geri denirse (ya da hiç site kalmadıysa) RESULT_EXIT döner ve uygulama kapanır.
    private val homeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (Palettes.index(this) != appliedPalette) window.statusBarColor = paletteStatusColor()
        if (r.resultCode == HomeActivity.RESULT_EXIT) {
            finish()
            return@registerForActivityResult
        }
        val want = Sites.siteToOpen(this)
        when {
            want == null -> openHome(exitOnBack = true) // hiç site yok: Ana Sayfa'da kal
            r.resultCode == RESULT_OK || want != siteBase -> loadSite(want)
        }
    }

    private lateinit var webView: BackgroundWebView
    private lateinit var menuButton: AssistiveTouchView
    private lateinit var menuPanel: View
    private lateinit var menuScrim: View
    private lateinit var rootLayout: View
    private lateinit var blackOverlay: View

    private var blackMode = false
    private var appliedPalette = 0

    private fun paletteStatusColor(): Int {
        appliedPalette = Palettes.index(this)
        Palettes.apply(this)
        return Palettes.color(this, R.attr.cGradEnd)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Palettes.apply(this)
        appliedPalette = Palettes.index(this)
        super.onCreate(savedInstanceState)

        KilifApp.blockAudioCapture(this)

        // === EKRAN GÖRÜNTÜSÜ / EKRAN KAYDI ENGELİ (banka uygulamaları gibi) ===
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Palettes.color(this, R.attr.cGradEnd)

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        recordNotice = findViewById(R.id.recordNotice)
        menuButton = findViewById(R.id.menuButton)
        menuPanel = findViewById(R.id.menuPanel)
        menuScrim = findViewById(R.id.menuScrim)
        rootLayout = findViewById(R.id.rootLayout)
        blackOverlay = findViewById(R.id.blackOverlay)

        setupWebView()
        setupBlackOverlay()
        setupFloatingButton()
        requestNotificationPermissionIfNeeded()

        webView.visibility = View.VISIBLE
        showFloatingButton()
        applySettings()
        startKeepAliveService()
        startRecordingWatch()

        val restore = lastUrl ?: siteBase
        val want = Sites.siteToOpen(this)
        when {
            // Yeniden oluşturma (tema değişimi gibi): kaldığı sayfaya dön
            restore != null && want != null -> webView.loadUrl(restore)
            want != null -> loadSite(want)
            // İlk açılış / hiç site yok: Ana Sayfa'da site eklensin
            savedInstanceState == null -> openHome(exitOnBack = true)
        }
    }

    private fun loadSite(url: String) {
        siteBase = url
        lastUrl = null
        if (!recPaused) webView.loadUrl(url) else recResumeUrl = url
    }

    // ---------------- Ses kaydı algılama ----------------

    private fun startRecordingWatch() {
        if (recCallback != null) return
        recCallback = RecordingWatch.register(this, ui) { onRecordings(it) }
        onRecordings(RecordingWatch.current(this))
    }

    private fun onRecordings(sources: List<Int>) {
        if (sources.isNotEmpty()) {
            ui.removeCallbacks(recResume)
            if (!recPaused) {
                recPaused = true
                recResumeUrl = lastUrl ?: siteBase
                hideMenuPanel()
                // Sayfa tamamen boşaltılır: sesin kesin olarak durması için
                webView.loadUrl("about:blank")
                recordNotice.visibility = View.VISIBLE
            }
        } else if (recPaused) {
            // Kayıt bitti: kısa bir bekleyişten sonra içeriği geri getir
            ui.removeCallbacks(recResume)
            ui.postDelayed(recResume, 1500)
        }
    }

    private fun resumeAfterRecording() {
        recPaused = false
        recordNotice.visibility = View.GONE
        val url = recResumeUrl ?: siteBase
        recResumeUrl = null
        if (url != null) webView.loadUrl(url)
    }

    // ---------------- WebView ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        // Ses/videonun kullanıcı dokunmadan da başlayabilmesi
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.cacheMode = WebSettings.LOAD_DEFAULT

        // Bağlantılar uygulama içinde açılsın (dış tarayıcıya atmasın)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                // Bilinen reklam sunucularına giden istekleri boş yanıtla geçiştir
                if (adBlockOn && request != null && AdBlock.isBlocked(request.url?.host)) {
                    return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url != "about:blank") {
                    lastUrl = url
                    if (adBlockOn) view?.evaluateJavascript(AdBlock.COSMETIC_JS, null)
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        // Sitelerin oturum çerezleri saklansın (bir kez giriş yapılan site hatırlansın)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
    }

    /** Ayarlar ekranındaki tercihleri WebView'e uygular. */
    private fun applySettings() {
        webView.settings.textZoom = AppSettings.textZoom(this)
        webView.keepBackground = AppSettings.keepAudio(this)
        val ab = AppSettings.adBlock(this)
        if (ab != adBlockOn) {
            adBlockOn = ab
            val cur = webView.url
            if (!recPaused && cur != null && cur != "about:blank") webView.reload() // açık sayfaya yeni ayar uygulansın
        }
        applyWebDarkMode()
    }

    @Suppress("DEPRECATION")
    private fun applyWebDarkMode() {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(
                    webView.settings,
                    if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            }
        } catch (_: Throwable) {
            // Eski WebView sürümünde desteklenmiyorsa sessizce geç
        }
    }

    private fun startKeepAliveService() {
        val svc = Intent(this, AudioKeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    // ---------------- Ana Sayfa ----------------

    /** Yüzen düğmedeki Ana Sayfa; siteden geri gelince de açılır. */
    private fun openHome(exitOnBack: Boolean = false) {
        homeLauncher.launch(Intent(this, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_EXIT_ON_BACK, exitOnBack))
    }

    // ---------------- Yüzen düğme (AssistiveTouch) ----------------

    private fun setupFloatingButton() {
        menuButton.restore(AppSettings.fabSide(this), AppSettings.fabY(this))

        menuButton.onSingleTap = { toggleMenuPanel() }
        menuButton.onDoubleTap = {
            hideMenuPanel()
            enterBlackMode()
        }
        menuButton.onDragStart = { hideMenuPanel() }
        menuButton.onPositionChanged = { side, y -> AppSettings.setFabPosition(this, side, y) }

        // Ekran döndürülünce / boyutu değişince düğmeyi yeniden yerleştir
        rootLayout.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if ((r - l) != (oldR - oldL) || (b - t) != (oldB - oldT)) {
                menuButton.applyPosition()
                hideMenuPanel()
            }
        }

        menuScrim.setOnClickListener { hideMenuPanel() }
        findViewById<View>(R.id.menuItemSettings).setOnClickListener {
            hideMenuPanel()
            openHome()
        }
        findViewById<View>(R.id.menuItemBlack).setOnClickListener {
            hideMenuPanel()
            enterBlackMode()
        }
    }

    private fun showFloatingButton() {
        menuButton.visibility = View.VISIBLE
        menuButton.doOnLayout { menuButton.applyPosition() }
    }

    private fun toggleMenuPanel() {
        if (menuPanel.visibility == View.VISIBLE) hideMenuPanel() else showMenuPanel()
    }

    /** Düğmenin yanında (ekran kenarının iç tarafında) küçük pencereyi açar. */
    private fun showMenuPanel() {
        val density = resources.displayMetrics.density
        val gap = 8f * density
        val edge = 8f * density

        menuPanel.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val pw = menuPanel.measuredWidth.toFloat()
        val ph = menuPanel.measuredHeight.toFloat()

        val bx = menuButton.x
        val by = menuButton.y
        val bw = menuButton.width.toFloat()
        val bh = menuButton.height.toFloat()

        var px = if (menuButton.side == 1) bx - pw - gap else bx + bw + gap
        var py = by + bh / 2f - ph / 2f
        px = px.coerceIn(edge, (rootLayout.width - pw - edge).coerceAtLeast(edge))
        py = py.coerceIn(edge, (rootLayout.height - ph - edge).coerceAtLeast(edge))

        menuPanel.x = px
        menuPanel.y = py
        menuScrim.visibility = View.VISIBLE
        menuPanel.visibility = View.VISIBLE
    }

    private fun hideMenuPanel() {
        menuPanel.visibility = View.GONE
        menuScrim.visibility = View.GONE
    }

    // ---------------- Siyah ekran modu ----------------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBlackOverlay() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                exitBlackMode()
                return true
            }
        })
        blackOverlay.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            true
        }
    }

    /** Ekranı siyaha çevirir, parlaklığı en aza indirir; ses çalmaya devam eder. */
    private fun enterBlackMode() {
        if (blackMode) return
        blackMode = true
        hideMenuPanel()
        blackOverlay.visibility = View.VISIBLE
        menuButton.visibility = View.GONE

        val lp = window.attributes
        lp.screenBrightness = 0f
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())

        Toast.makeText(this, R.string.black_hint, Toast.LENGTH_LONG).show()
    }

    private fun exitBlackMode() {
        if (!blackMode) return
        blackMode = false
        blackOverlay.visibility = View.GONE
        showFloatingButton()

        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    // ---------------- Yaşam döngüsü ----------------

    override fun onResume() {
        super.onResume()
        KilifApp.blockAudioCapture(this)
        applySettings()
        webView.onResume()
        webView.resumeTimers()
        // Ayar değiştiyse kilitleri (wake lock) yeniden değerlendir
        startKeepAliveService()
    }

    override fun onPause() {
        super.onPause()
        // Bilerek webView.onPause() çağrılmıyor; böylece ekran kapanınca ses devam eder.
        if (AppSettings.keepAudio(this)) webView.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        // Ekran kapanıp tekrar açıldığında siyah ekranda takılı kalmasın
        if (blackMode) exitBlackMode()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            recPaused -> Unit
            menuPanel.visibility == View.VISIBLE -> hideMenuPanel()
            blackMode -> exitBlackMode()
            webView.canGoBack() -> webView.goBack()
            // Sitede geri gidecek yer kalmadı: uygulamayı kapatmak yerine Ana Sayfa açılır
            else -> openHome(exitOnBack = true)
        }
    }

    override fun onDestroy() {
        RecordingWatch.unregister(this, recCallback)
        ui.removeCallbacks(recResume)
        if (isFinishing) {
            stopService(Intent(this, AudioKeepAliveService::class.java))
            lastUrl = null
            siteBase = null
        }
        webView.destroy()
        super.onDestroy()
    }
}
