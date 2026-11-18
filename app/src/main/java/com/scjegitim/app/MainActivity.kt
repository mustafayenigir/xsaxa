package com.scjegitim.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {

    companion object {
        // Tema değişince Activity yeniden oluşur; kilit açık kalsın ve sayfa kaybolmasın.
        // Uygulama süreci tamamen kapanırsa bu değerler sıfırlanır ve şifre yeniden sorulur.
        private var sessionUnlocked = false
        private var lastUrl: String? = null
    }

    // Uygulamanın açacağı tek adres
    private val siteUrl = "https://scjegitim.blogspot.com"

    // Şifrenin saklandığı yer (cihazın özel alanı)
    private val prefsName = "scj_secure_prefs"
    private val keyHash = "pwd_hash"
    private val keySalt = "pwd_salt"

    private lateinit var webView: BackgroundWebView
    private lateinit var lockContainer: View
    private lateinit var lockTitle: TextView
    private lateinit var passwordInput: EditText
    private lateinit var passwordConfirm: EditText
    private lateinit var unlockButton: Button
    private lateinit var lockMessage: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var menuButton: AssistiveTouchView
    private lateinit var menuPanel: View
    private lateinit var menuScrim: View
    private lateinit var rootLayout: View
    private lateinit var blackOverlay: View

    private var unlocked = false
    private var blackMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // === EKRAN GÖRÜNTÜSÜ / EKRAN KAYDI ENGELİ (banka uygulamaları gibi) ===
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        lockContainer = findViewById(R.id.lockContainer)
        lockTitle = findViewById(R.id.lockTitle)
        passwordInput = findViewById(R.id.passwordInput)
        passwordConfirm = findViewById(R.id.passwordConfirm)
        unlockButton = findViewById(R.id.unlockButton)
        lockMessage = findViewById(R.id.lockMessage)
        settingsButton = findViewById(R.id.settingsButton)
        menuButton = findViewById(R.id.menuButton)
        menuPanel = findViewById(R.id.menuPanel)
        menuScrim = findViewById(R.id.menuScrim)
        rootLayout = findViewById(R.id.rootLayout)
        blackOverlay = findViewById(R.id.blackOverlay)

        setupWebView()
        setupBlackOverlay()
        requestNotificationPermissionIfNeeded()

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (prefs.contains(keyHash)) showEnterMode() else showCreateMode()

        unlockButton.setOnClickListener { handleUnlock() }
        settingsButton.setOnClickListener { openSettings() }
        setupFloatingButton()

        if (sessionUnlocked) openSite(lastUrl ?: siteUrl)
    }

    // ---------------- Şifre ekranı ----------------

    private fun showCreateMode() {
        lockTitle.setText(R.string.create_title)
        passwordConfirm.visibility = View.VISIBLE
        unlockButton.setText(R.string.create_button)
        lockMessage.setText(R.string.create_hint)
    }

    private fun showEnterMode() {
        lockTitle.setText(R.string.enter_title)
        passwordConfirm.visibility = View.GONE
        unlockButton.setText(R.string.enter_button)
        lockMessage.text = ""
    }

    private fun handleUnlock() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val pwd = passwordInput.text.toString()

        if (pwd.isEmpty()) {
            lockMessage.setText(R.string.err_empty)
            return
        }

        if (!prefs.contains(keyHash)) {
            // --- ŞİFRE OLUŞTURMA ---
            val confirm = passwordConfirm.text.toString()
            if (pwd.length < 4) {
                lockMessage.setText(R.string.err_short)
                return
            }
            if (pwd != confirm) {
                lockMessage.setText(R.string.err_mismatch)
                return
            }
            val salt = newSalt()
            val hash = hashPassword(pwd, salt)
            prefs.edit()
                .putString(keySalt, salt)
                .putString(keyHash, hash)
                .apply()
            openSite(siteUrl)
        } else {
            // --- ŞİFRE DOĞRULAMA ---
            val salt = prefs.getString(keySalt, "") ?: ""
            val hash = hashPassword(pwd, salt)
            if (hash == prefs.getString(keyHash, null)) {
                openSite(siteUrl)
            } else {
                lockMessage.setText(R.string.err_wrong)
                passwordInput.text?.clear()
            }
        }
    }

    private fun openSite(url: String) {
        unlocked = true
        sessionUnlocked = true
        passwordInput.text?.clear()
        passwordConfirm.text?.clear()
        lockContainer.visibility = View.GONE
        settingsButton.visibility = View.GONE
        webView.visibility = View.VISIBLE
        showFloatingButton()

        applySettings()
        startKeepAliveService()
        webView.loadUrl(url)
    }

    /** Uygulamayı yeniden şifre ekranına döndürür; ses durur. */
    private fun lockApp() {
        exitBlackMode()
        unlocked = false
        sessionUnlocked = false
        lastUrl = null
        webView.loadUrl("about:blank")
        webView.visibility = View.GONE
        hideMenuPanel()
        menuButton.visibility = View.GONE
        lockContainer.visibility = View.VISIBLE
        settingsButton.visibility = View.VISIBLE
        stopService(Intent(this, AudioKeepAliveService::class.java))
        showEnterMode()
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
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url != "about:blank") lastUrl = url
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    /** Ayarlar ekranındaki tercihleri WebView'e uygular. */
    private fun applySettings() {
        webView.settings.textZoom = AppSettings.textZoom(this)
        webView.keepBackground = AppSettings.keepAudio(this)
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

    // ---------------- Menü ve ayarlar ----------------

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
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
            openSettings()
        }
        findViewById<View>(R.id.menuItemBlack).setOnClickListener {
            hideMenuPanel()
            enterBlackMode()
        }
        findViewById<View>(R.id.menuItemLock).setOnClickListener {
            hideMenuPanel()
            lockApp()
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
        if (unlocked) showFloatingButton()

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
        if (unlocked) {
            applySettings()
            webView.onResume()
            webView.resumeTimers()
            // Ayar değiştiyse kilitleri (wake lock) yeniden değerlendir
            startKeepAliveService()
        }
    }

    override fun onPause() {
        super.onPause()
        // Bilerek webView.onPause() çağrılmıyor; böylece ekran kapanınca ses devam eder.
        if (unlocked && AppSettings.keepAudio(this)) webView.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        // Ekran kapanıp tekrar açıldığında siyah ekranda takılı kalmasın
        if (blackMode) exitBlackMode()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            menuPanel.visibility == View.VISIBLE -> hideMenuPanel()
            blackMode -> exitBlackMode()
            unlocked && webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        stopService(Intent(this, AudioKeepAliveService::class.java))
        webView.destroy()
        super.onDestroy()
    }

    // --- Şifre güvenliği: düz metin değil, tuzlu SHA-256 özeti saklanır ---
    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(pwd: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((salt + pwd).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
