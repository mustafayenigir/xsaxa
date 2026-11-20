package com.scjegitim.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        // Tema değişince Activity yeniden oluşur; kilit açık kalsın ve sayfa kaybolmasın.
        // Uygulama süreci tamamen kapanırsa bu değerler sıfırlanır ve şifre yeniden sorulur.
        private var sessionUnlocked = false
        private var lastUrl: String? = null

        // Şu an gösterilen sitenin "ana" adresi (Ayarlar'dan değişirse onResume'da fark edilir)
        private var siteBase: String? = null

        // Uygulamada geçen süre ölçümü (Activity yeniden oluşsa da kaldığı yerden sürer)
        private var usageMark = 0L
        private var lastFlush = 0L

        // Ağ işleri ana iş parçacığında yapılmasın
        private val io: ExecutorService = Executors.newSingleThreadExecutor()

        private const val TICK_MS = 60_000L
        private const val FLUSH_EVERY_MS = 10 * 60_000L
        private const val MAX_TICK_SEC = 90L

        private val NO_REGEX = Regex("^[A-Z0-9-]{6,24}$")
        private val GOOGLE_SESSION = Regex("(^|;\\s*)(SID|__Secure-1PSID)=")
        private const val GOOGLE_SIGNIN_URL =
            "https://accounts.google.com/ServiceLogin?hl=tr&continue=https%3A%2F%2Fmyaccount.google.com%2F"
    }

    private enum class Step { NO, PASSWORD, GOOGLE, SITE, ENTER }

    private var step = Step.NO
    private var busy = false
    private val ui = Handler(Looper.getMainLooper())
    private var googleLoaded = false
    private val googlePoll = object : Runnable {
        override fun run() {
            updateGoogleStatus()
            if (step == Step.GOOGLE) ui.postDelayed(this, 1500)
        }
    }

    // İlk ayarlar ekranı (site seçimi). "Devam Et" ile dönülürse site açılır.
    private val firstSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK && step == Step.SITE) {
            Account.setSiteDone(this)
            openSite(null, logLogin = false)
        }
    }

    private val usageTick = object : Runnable {
        override fun run() {
            tickUsage()
            ui.postDelayed(this, TICK_MS)
        }
    }

    // Şifrenin saklandığı yer (cihazın özel alanı)
    private val prefsName = "scj_secure_prefs"
    private val keyHash = "pwd_hash"
    private val keySalt = "pwd_salt"

    private lateinit var webView: BackgroundWebView
    private lateinit var lockContainer: View
    private lateinit var lockTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var noInput: EditText
    private lateinit var googleContainer: View
    private lateinit var googleWebView: WebView
    private lateinit var googleStatus: TextView
    private lateinit var googleContinue: Button
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
        infoText = findViewById(R.id.infoText)
        noInput = findViewById(R.id.noInput)
        googleContainer = findViewById(R.id.googleContainer)
        googleWebView = findViewById(R.id.googleWebView)
        googleStatus = findViewById(R.id.googleStatus)
        googleContinue = findViewById(R.id.googleContinue)
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
        setupGoogleWebView()
        googleContinue.setOnClickListener { handleGoogleContinue() }
        setupBlackOverlay()
        requestNotificationPermissionIfNeeded()

        showStep(currentStep())

        unlockButton.setOnClickListener { handleAction() }
        settingsButton.setOnClickListener { openSettings() }
        setupFloatingButton()

        if (sessionUnlocked) openSite(lastUrl ?: siteBase, logLogin = false)
    }

    // ---------------- Kayıt / şifre ekranı ----------------

    /** Kayıtlı duruma göre hangi adımda olunacağını bulur (uygulama kapansa da kaldığı yerden sürer). */
    private fun currentStep(): Step = when {
        Account.identity(this) != null -> when {
            !Account.googleDone(this) -> Step.GOOGLE
            !Account.siteDone(this) -> Step.SITE
            else -> Step.ENTER
        }
        Account.pendingNo(this) != null -> Step.PASSWORD
        else -> Step.NO
    }

    private fun showStep(s: Step) {
        step = s
        busy = false
        unlockButton.isEnabled = true
        lockMessage.text = ""

        noInput.visibility = if (s == Step.NO) View.VISIBLE else View.GONE
        passwordInput.visibility = if (s == Step.PASSWORD || s == Step.ENTER) View.VISIBLE else View.GONE
        passwordConfirm.visibility = if (s == Step.PASSWORD) View.VISIBLE else View.GONE

        // Google adımında kendi ekranı (WebView) görünür; diğer adımlarda şifre/numara ekranı
        val google = s == Step.GOOGLE
        googleContainer.visibility = if (google) View.VISIBLE else View.GONE
        lockContainer.visibility = if (google) View.GONE else View.VISIBLE
        settingsButton.visibility = if (google) View.GONE else View.VISIBLE
        ui.removeCallbacks(googlePoll)
        if (google) {
            if (!googleLoaded) {
                googleLoaded = true
                googleWebView.loadUrl(GOOGLE_SIGNIN_URL)
            }
            ui.post(googlePoll)
        }

        when (s) {
            Step.NO -> {
                lockTitle.setText(R.string.no_title)
                infoText.setText(R.string.info_no)
                unlockButton.setText(R.string.no_button)
            }
            Step.PASSWORD -> {
                lockTitle.setText(R.string.create_title)
                infoText.setText(R.string.info_password)
                unlockButton.setText(R.string.create_button_save)
            }
            Step.GOOGLE -> {
                // Kendi ekranında gösterilir (googleContainer)
            }
            Step.SITE -> {
                lockTitle.setText(R.string.site_step_title)
                infoText.setText(R.string.info_site)
                unlockButton.setText(R.string.site_step_button)
            }
            Step.ENTER -> {
                lockTitle.setText(R.string.enter_title)
                infoText.text = ""
                unlockButton.setText(R.string.enter_button)
            }
        }
        infoText.visibility = if (infoText.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun handleAction() {
        if (busy) return
        when (step) {
            Step.NO -> handleNo()
            Step.PASSWORD -> handleCreatePassword()
            Step.GOOGLE -> Unit
            Step.SITE -> openFirstSettings()
            Step.ENTER -> handleEnter()
        }
    }

    private fun setBusy(b: Boolean) {
        busy = b
        unlockButton.isEnabled = !b
        if (b) lockMessage.setText(R.string.checking)
    }

    private fun message(resId: Int) { lockMessage.setText(resId) }

    private fun errorFor(code: String?): Int = when (code) {
        "invalid" -> R.string.err_no_invalid
        "busy" -> R.string.err_busy
        "auth" -> R.string.err_auth
        else -> R.string.err_server
    }

    /** Ağ işini arka planda yapar, sonucu ekran hâlâ canlıysa ana iş parçacığında teslim eder. */
    private fun <T> background(work: () -> T, done: (T) -> Unit) {
        io.execute {
            val result = work()
            runOnUiThread { if (!isFinishing && !isDestroyed) done(result) }
        }
    }

    // 1) Giriş numarası (yalnızca bir kere)
    private fun handleNo() {
        val no = noInput.text.toString().trim().replace(" ", "").uppercase()
        if (!NO_REGEX.matches(no)) {
            message(R.string.err_no_format)
            return
        }
        setBusy(true)
        background({ Api.check(no) }) { r ->
            setBusy(false)
            when {
                r == null -> message(R.string.err_network)
                r.optBoolean("ok") -> {
                    Account.setPendingNo(this, no)
                    showStep(Step.PASSWORD)
                }
                else -> message(errorFor(r.optString("error")))
            }
        }
    }

    // 2) Şifre oluştur (enter, tekrar, kaydet) → sunucuda UUID üretilir ve satır yazılır
    private fun handleCreatePassword() {
        val no = Account.pendingNo(this)
        if (no == null) {
            showStep(Step.NO)
            return
        }
        val pwd = passwordInput.text.toString()
        val confirm = passwordConfirm.text.toString()
        when {
            pwd.isEmpty() -> return message(R.string.err_empty)
            pwd.length < 4 -> return message(R.string.err_short)
            pwd != confirm -> return message(R.string.err_mismatch)
        }
        val salt = newSalt()
        val hash = hashPassword(pwd, salt)
        setBusy(true)
        background({ Api.register(no, hash) }) { r ->
            setBusy(false)
            val uuid = r?.optString("uuid").orEmpty()
            when {
                r == null -> message(R.string.err_network)
                r.optBoolean("ok") && uuid.isNotBlank() -> {
                    // Yerel şifre özeti yalnızca sunucu kaydı başarılı olunca saklanır
                    getSharedPreferences(prefsName, MODE_PRIVATE).edit()
                        .putString(keySalt, salt).putString(keyHash, hash).apply()
                    Account.saveRegistration(this, no, uuid, hash)
                    passwordInput.text?.clear()
                    passwordConfirm.text?.clear()
                    showStep(Step.GOOGLE)
                }
                r.optString("error") == "invalid" -> {
                    // Numara bu arada listeden çıkarılmış
                    Account.setPendingNo(this, null)
                    showStep(Step.NO)
                    message(R.string.err_no_invalid)
                }
                else -> message(errorFor(r.optString("error")))
            }
        }
    }

    // 3) Google hesabıyla oturum açma (adres yazılmaz; oturum uygulamadaki tüm sitelerde geçerli olur)
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGoogleWebView() {
        val s = googleWebView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        // Google, gömülü WebView'de giriş sayfasını "wv" işaretiyle engelleyebilir; işareti kaldır.
        s.userAgentString = s.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(googleWebView, true)
        googleWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateGoogleStatus()
            }
        }
    }

    private fun googleSignedIn(): Boolean {
        val cm = CookieManager.getInstance()
        val cookies = (cm.getCookie("https://accounts.google.com") ?: "") + ";" +
            (cm.getCookie("https://www.google.com") ?: "")
        return GOOGLE_SESSION.containsMatchIn(cookies)
    }

    private fun updateGoogleStatus() {
        val ok = googleSignedIn()
        googleStatus.setText(if (ok) R.string.google_status_ok else R.string.google_status_wait)
        googleContinue.isEnabled = ok
    }

    private fun handleGoogleContinue() {
        if (!googleSignedIn()) {
            updateGoogleStatus()
            return
        }
        CookieManager.getInstance().flush()
        Account.setGoogleDone(this)
        showStep(Step.SITE)
        openFirstSettings()
    }

    // 3b) İlk ayarlar: site seçilip "Devam Et" denir
    private fun openFirstSettings() {
        firstSettings.launch(
            Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FIRST_RUN, true)
        )
    }

    // 4) Sonraki açılışlar: yalnızca şifre (yerelde doğrulanır, ağ gerekmez)
    private fun handleEnter() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val pwd = passwordInput.text.toString()
        if (pwd.isEmpty()) {
            message(R.string.err_empty)
            return
        }
        val salt = prefs.getString(keySalt, "") ?: ""
        if (hashPassword(pwd, salt) == prefs.getString(keyHash, null)) {
            openSite(null, logLogin = true)
        } else {
            message(R.string.err_wrong)
            passwordInput.text?.clear()
        }
    }

    /**
     * Kilidi açar ve siteyi gösterir.
     * @param url null ise adres seçili / listedeki ilk / varsayılan siteden belirlenir.
     * @param logLogin true ise sunucuya "giriş yapıldı" bilgisi gönderilir (yoklama tablosuna tarih eklenir).
     */
    private fun openSite(url: String?, logLogin: Boolean) {
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
        beginUsage()

        val id = Account.identity(this)
        if (logLogin && id != null) io.execute { Api.login(id) }

        if (url != null) {
            // Yeniden oluşturma (tema değişimi gibi): kaldığı sayfaya dön
            if (siteBase == null) siteBase = url
            webView.loadUrl(url)
            return
        }

        val known = Account.siteToOpen(this)
        if (known == null) {
            // Liste hiç inmemiş: önce listeyi al, sonra ilk siteyi aç
            if (id != null) {
                background({ Account.refreshLinks(applicationContext) }) { showSiteOrNotice() }
            } else {
                showSiteOrNotice()
            }
        } else {
            loadSite(known)
            if (id != null) {
                // Listeyi arka planda tazele; seçili site listeden kaldırıldıysa ilkine geç
                background({ Account.refreshLinks(applicationContext) }) { list -> reconcileSite(list) }
            }
        }
    }

    private fun showSiteOrNotice() {
        val url = Account.siteToOpen(this)
        if (url != null) {
            loadSite(url)
        } else {
            webView.loadUrl("about:blank")
            Toast.makeText(this, R.string.no_site, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSite(url: String) {
        siteBase = url
        lastUrl = null
        webView.loadUrl(url)
    }

    private fun reconcileSite(list: List<SiteLink>?) {
        if (list == null || list.isEmpty() || !unlocked) return
        val selected = Account.selectedUrl(this)
        if (selected != null && list.none { it.url == selected }) Account.setSelectedUrl(this, null)
        val want = Account.siteToOpen(this) ?: return
        if (want != siteBase) loadSite(want)
    }

    /** Uygulamayı yeniden kilit ekranına döndürür; ses durur. */
    private fun lockApp() {
        exitBlackMode()
        endUsage()
        unlocked = false
        sessionUnlocked = false
        lastUrl = null
        siteBase = null
        webView.loadUrl("about:blank")
        webView.visibility = View.GONE
        hideMenuPanel()
        menuButton.visibility = View.GONE
        lockContainer.visibility = View.VISIBLE
        settingsButton.visibility = View.VISIBLE
        stopService(Intent(this, AudioKeepAliveService::class.java))
        showStep(currentStep())
    }

    // ---------------- Uygulamada geçen süre ----------------

    private fun beginUsage() {
        if (usageMark == 0L) usageMark = SystemClock.elapsedRealtime()
        if (lastFlush == 0L) lastFlush = SystemClock.elapsedRealtime()
        ui.removeCallbacks(usageTick)
        ui.postDelayed(usageTick, TICK_MS)
        flushUsageAsync() // önceki oturumdan kalan süre varsa gönder
    }

    /** Son ölçümden bu yana geçen süreyi (en çok 90 sn) yerel sayaca ekler. */
    private fun tickUsage() {
        if (usageMark == 0L) return
        val now = SystemClock.elapsedRealtime()
        val sec = ((now - usageMark) / 1000).coerceIn(0L, MAX_TICK_SEC)
        usageMark = now
        Account.addPendingSeconds(applicationContext, sec)
        if (now - lastFlush >= FLUSH_EVERY_MS) flushUsageAsync()
    }

    private fun endUsage() {
        ui.removeCallbacks(usageTick)
        tickUsage()
        usageMark = 0L
        flushUsageAsync()
    }

    private fun flushUsageAsync() {
        lastFlush = SystemClock.elapsedRealtime()
        val app = applicationContext
        io.execute { Account.flushUsage(app) }
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
        // Google oturumu (ve sitelerin çerezleri) tüm sitelerde geçerli olsun
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
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
            // Ayarlar'dan farklı bir site seçildiyse onu aç
            val want = Account.siteToOpen(this)
            if (want != null && want != siteBase) loadSite(want)
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
        if (unlocked) {
            tickUsage()
            flushUsageAsync()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            menuPanel.visibility == View.VISIBLE -> hideMenuPanel()
            blackMode -> exitBlackMode()
            step == Step.GOOGLE && !unlocked && googleWebView.canGoBack() -> googleWebView.goBack()
            unlocked && webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        ui.removeCallbacks(usageTick)
        ui.removeCallbacks(googlePoll)
        googleWebView.destroy()
        if (unlocked) tickUsage()
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
