package com.oku.app

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
import android.view.Gravity
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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        // Tema (gece/gündüz) değişince Activity yeniden oluşur; kilit açık kalsın, sayfa kaybolmasın.
        private var sessionUnlocked = false
        private var savedPage = Page.HOME
        private var savedSite: SiteLink? = null
        private var savedUrl: String? = null

        private var usageMark = 0L
        private var lastFlush = 0L
        private val io: ExecutorService = Executors.newFixedThreadPool(2)

        private const val TICK_MS = 60_000L
        private const val FLUSH_EVERY_MS = 10 * 60_000L
        private const val MAX_TICK_SEC = 90L
        private const val REFRESH_MS = 5 * 60_000L

        private val NO_REGEX = Regex("^[A-Z0-9-]{3,24}$")
        private val GOOGLE_SESSION = Regex("(^|;\\s*)(SID|__Secure-1PSID)=")
        private const val GOOGLE_SIGNIN_URL =
            "https://accounts.google.com/ServiceLogin?hl=tr&continue=https%3A%2F%2Fmyaccount.google.com%2F"
    }

    private enum class Step { NO, PASSWORD, GOOGLE, ENTER }
    private enum class Page { HOME, SITE, CALENDAR, SETTINGS }

    private val ui = Handler(Looper.getMainLooper())
    private var step = Step.NO
    private var busy = false
    private var unlocked = false
    private var page = Page.HOME
    private var site: SiteLink? = null
    private var blackMode = false
    private var landscape = false
    private var home: HomeData = HomeData.EMPTY
    private var loading = false
    @Volatile private var adBlockOn = true

    // --- görünümler
    private lateinit var root: FrameLayout
    private lateinit var shell: View
    private lateinit var topBar: View
    private lateinit var topTitle: TextView
    private lateinit var backButton: ImageButton
    private lateinit var bellBadge: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var homePage: ScrollView
    private lateinit var homeContent: LinearLayout
    private lateinit var webView: BackgroundWebView
    private lateinit var calendarPage: FrameLayout
    private lateinit var settingsPage: ScrollView
    private lateinit var settingsContent: LinearLayout
    private lateinit var noticePanel: LinearLayout
    private lateinit var noticeScrim: View
    private lateinit var noticeList: LinearLayout
    private lateinit var menuButton: AssistiveTouchView
    private lateinit var menuPanel: View
    private lateinit var menuScrim: View
    private lateinit var menuNotices: TextView
    private lateinit var blackOverlay: View
    private lateinit var recordNotice: View

    private lateinit var lockContainer: View
    private lateinit var lockTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var noInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordConfirm: EditText
    private lateinit var unlockButton: Button
    private lateinit var lockMessage: TextView
    private lateinit var googleContainer: View
    private lateinit var googleWebView: WebView
    private lateinit var googleStatus: TextView
    private lateinit var googleContinue: Button

    private lateinit var calendar: CalendarView
    private lateinit var homeView: HomePage
    private lateinit var settingsView: SettingsPage
    private lateinit var notices: Notices

    // --- ses kaydı algılama (öğrenci)
    private var recCallback: android.media.AudioManager.AudioRecordingCallback? = null
    private var recPaused = false
    private var recResumeUrl: String? = null
    private val recResume = Runnable { resumeAfterRecording() }

    private var googleLoaded = false
    private val googlePoll = object : Runnable {
        override fun run() {
            updateGoogleStatus()
            if (step == Step.GOOGLE && !unlocked) ui.postDelayed(this, 1500)
        }
    }
    private val usageTick = object : Runnable {
        override fun run() {
            tickUsage()
            ui.postDelayed(this, TICK_MS)
        }
    }
    private val autoRefresh = object : Runnable {
        override fun run() {
            refresh(silent = true)
            ui.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OkuApp.blockAudioCapture(this)
        // === EKRAN GÖRÜNTÜSÜ / EKRAN KAYDI ENGELİ ===
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)
        bind()

        calendar = CalendarView(this) { showEvent(it) }
        calendarPage.addView(calendar)
        homeView = HomePage(this, io, { r -> runOnUiThread { if (!isDestroyed) r.run() } }, { openSite(it) }, { showEvent(it) }, { joinZoom(it) })
        settingsView = SettingsPage(this, { applySettings() }, { lockApp() })
        notices = Notices(this, { joinZoom(it) }) { updateBadge() }

        setupLock()
        setupShell()
        setupWebView()
        setupFloatingButton()
        setupBlackOverlay()
        if (!BuildConfig.PARENT) requestNotificationPermissionIfNeeded()

        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        showStep(currentStep())
        if (sessionUnlocked) unlock(logLogin = false, restore = true)
    }

    private fun bind() {
        root = findViewById(R.id.rootLayout)
        shell = findViewById(R.id.shell)
        topBar = findViewById(R.id.topBar)
        topTitle = findViewById(R.id.topTitle)
        backButton = findViewById(R.id.backButton)
        bellBadge = findViewById(R.id.bellBadge)
        bottomNav = findViewById(R.id.bottomNav)
        homePage = findViewById(R.id.homePage)
        homeContent = findViewById(R.id.homeContent)
        webView = findViewById(R.id.webView)
        calendarPage = findViewById(R.id.calendarPage)
        settingsPage = findViewById(R.id.settingsPage)
        settingsContent = findViewById(R.id.settingsContent)
        noticePanel = findViewById(R.id.noticePanel)
        noticeScrim = findViewById(R.id.noticeScrim)
        noticeList = findViewById(R.id.noticeList)
        menuButton = findViewById(R.id.menuButton)
        menuPanel = findViewById(R.id.menuPanel)
        menuScrim = findViewById(R.id.menuScrim)
        menuNotices = findViewById(R.id.menuNotices)
        blackOverlay = findViewById(R.id.blackOverlay)
        recordNotice = findViewById(R.id.recordNotice)

        lockContainer = findViewById(R.id.lockContainer)
        lockTitle = findViewById(R.id.lockTitle)
        infoText = findViewById(R.id.infoText)
        noInput = findViewById(R.id.noInput)
        passwordInput = findViewById(R.id.passwordInput)
        passwordConfirm = findViewById(R.id.passwordConfirm)
        unlockButton = findViewById(R.id.unlockButton)
        lockMessage = findViewById(R.id.lockMessage)
        googleContainer = findViewById(R.id.googleContainer)
        googleWebView = findViewById(R.id.googleWebView)
        googleStatus = findViewById(R.id.googleStatus)
        googleContinue = findViewById(R.id.googleContinue)
    }

    // ================================================================ kayıt / şifre

    private fun currentStep(): Step = when {
        Account.identity(this) != null -> if (!Account.googleDone(this)) Step.GOOGLE else Step.ENTER
        Account.pendingNo(this) != null -> Step.PASSWORD
        else -> Step.NO
    }

    private fun setupLock() {
        unlockButton.setOnClickListener { handleAction() }
        googleContinue.setOnClickListener { handleGoogleContinue() }
        passwordInput.setOnEditorActionListener { _, _, _ -> handleAction(); true }
        if (!BuildConfig.PARENT) setupGoogleWebView()
    }

    private fun showStep(s: Step) {
        step = s
        busy = false
        unlockButton.isEnabled = true
        lockMessage.text = ""
        noInput.visibility = if (s == Step.NO) View.VISIBLE else View.GONE
        passwordInput.visibility = if (s == Step.PASSWORD || s == Step.ENTER) View.VISIBLE else View.GONE
        passwordConfirm.visibility = if (s == Step.PASSWORD) View.VISIBLE else View.GONE

        val google = s == Step.GOOGLE && !unlocked
        googleContainer.visibility = if (google) View.VISIBLE else View.GONE
        lockContainer.visibility = if (!google && !unlocked) View.VISIBLE else View.GONE
        ui.removeCallbacks(googlePoll)
        if (google) {
            if (!googleLoaded) {
                googleLoaded = true
                googleWebView.loadUrl(GOOGLE_SIGNIN_URL)
            }
            ui.post(googlePoll)
        }
        when (s) {
            Step.NO -> { lockTitle.setText(R.string.no_title); infoText.setText(R.string.info_no); unlockButton.setText(R.string.no_button) }
            Step.PASSWORD -> { lockTitle.setText(R.string.create_title); infoText.setText(R.string.info_password); unlockButton.setText(R.string.create_button) }
            Step.GOOGLE -> Unit
            Step.ENTER -> {
                val n = Account.name(this).trim().split(" ").firstOrNull().orEmpty()
                lockTitle.text = if (n.isNotEmpty()) getString(R.string.enter_title_name, n) else getString(R.string.enter_title)
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
            Step.ENTER -> handleEnter()
        }
    }

    private fun setBusy(b: Boolean) {
        busy = b
        unlockButton.isEnabled = !b
        if (b) lockMessage.setText(R.string.checking)
    }

    private fun errorFor(code: String?): Int = when (code) {
        "invalid" -> R.string.err_no_invalid
        "busy" -> R.string.err_busy
        "auth" -> R.string.err_auth
        else -> R.string.err_server
    }

    private fun <T> background(work: () -> T, done: (T) -> Unit) {
        io.execute {
            val r = work()
            runOnUiThread { if (!isFinishing && !isDestroyed) done(r) }
        }
    }

    // 1) Numara (yalnızca ilk kurulumda)
    private fun handleNo() {
        val no = noInput.text.toString().trim().replace(" ", "").uppercase(Format.TR)
        if (!NO_REGEX.matches(no)) return lockMessage.setText(R.string.err_no_format)
        setBusy(true)
        background({ Api.check(no) }) { r ->
            setBusy(false)
            when {
                r == null -> lockMessage.setText(R.string.err_network)
                r.optBoolean("ok") -> {
                    Account.setPendingNo(this, no)
                    showStep(Step.PASSWORD)
                    val n = r.optString("name")
                    if (n.isNotBlank()) infoText.text = getString(R.string.info_password_name, n)
                }
                else -> lockMessage.setText(errorFor(r.optString("error")))
            }
        }
    }

    // 2) Şifre oluştur → sunucuya kayıt
    private fun handleCreatePassword() {
        val no = Account.pendingNo(this) ?: return showStep(Step.NO)
        val pwd = passwordInput.text.toString()
        val confirm = passwordConfirm.text.toString()
        when {
            pwd.isEmpty() -> return lockMessage.setText(R.string.err_empty)
            pwd.length < 4 -> return lockMessage.setText(R.string.err_short)
            pwd != confirm -> return lockMessage.setText(R.string.err_mismatch)
        }
        val salt = Account.newSalt()
        val hash = Account.hash(pwd, salt)
        setBusy(true)
        background({ Api.register(no, hash) }) { r ->
            setBusy(false)
            val uuid = r?.optString("uuid").orEmpty()
            when {
                r == null -> lockMessage.setText(R.string.err_network)
                r.optBoolean("ok") && uuid.isNotBlank() -> {
                    Account.saveRegistration(this, no, uuid, salt, hash, r.optString("name"))
                    passwordInput.text?.clear()
                    passwordConfirm.text?.clear()
                    if (Account.googleDone(this)) unlock(logLogin = false) else showStep(Step.GOOGLE)
                }
                r.optString("error") == "invalid" -> {
                    Account.setPendingNo(this, null)
                    showStep(Step.NO)
                    lockMessage.setText(R.string.err_no_invalid)
                }
                else -> lockMessage.setText(errorFor(r.optString("error")))
            }
        }
    }

    // 3) (Öğrenci) Google hesabıyla bir kez oturum aç: oturum uygulamadaki tüm sitelerde geçerli olur
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGoogleWebView() {
        val s = googleWebView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
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
        val cookies = (cm.getCookie("https://accounts.google.com") ?: "") + ";" + (cm.getCookie("https://www.google.com") ?: "")
        return GOOGLE_SESSION.containsMatchIn(cookies)
    }

    private fun updateGoogleStatus() {
        val ok = googleSignedIn()
        googleStatus.setText(if (ok) R.string.google_status_ok else R.string.google_status_wait)
        googleContinue.isEnabled = ok
    }

    private fun handleGoogleContinue() {
        if (!googleSignedIn()) return updateGoogleStatus()
        CookieManager.getInstance().flush()
        Account.setGoogleDone(this)
        unlock(logLogin = false)
    }

    // 4) Sonraki açılışlar: yalnızca şifre (yerelde doğrulanır)
    private fun handleEnter() {
        val pwd = passwordInput.text.toString()
        if (pwd.isEmpty()) return lockMessage.setText(R.string.err_empty)
        if (Account.checkPassword(this, pwd)) {
            unlock(logLogin = true)
        } else {
            lockMessage.setText(R.string.err_wrong)
            passwordInput.text?.clear()
        }
    }

    // ================================================================ kilit aç / kapa

    private fun unlock(logLogin: Boolean, restore: Boolean = false) {
        unlocked = true
        sessionUnlocked = true
        passwordInput.text?.clear()
        lockContainer.visibility = View.GONE
        googleContainer.visibility = View.GONE
        ui.removeCallbacks(googlePoll)
        hideKeyboard()

        home = Account.cachedHome(this) ?: HomeData.EMPTY
        renderAll()
        applyMode()

        if (!BuildConfig.PARENT) {
            applySettings()
            startKeepAliveService()
            beginUsage()
            startRecordingWatch()
        }
        val id = Account.identity(this)
        if (logLogin && id != null) io.execute { Api.login(id) }

        if (restore) {
            when (savedPage) {
                Page.SITE -> savedSite?.let { openSite(it, savedUrl) } ?: showPage(Page.HOME)
                else -> showPage(savedPage)
            }
        } else showPage(Page.HOME)
        refresh(silent = false)
        ui.removeCallbacks(autoRefresh)
        ui.postDelayed(autoRefresh, REFRESH_MS)
    }

    private fun lockApp() {
        exitBlackMode()
        hideNotices()
        hideMenuPanel()
        if (!BuildConfig.PARENT) {
            endUsage()
            stopRecordingWatch()
            stopService(Intent(this, AudioKeepAliveService::class.java))
        }
        ui.removeCallbacks(autoRefresh)
        unlocked = false
        sessionUnlocked = false
        savedPage = Page.HOME
        savedSite = null
        savedUrl = null
        site = null
        webView.loadUrl("about:blank")
        shell.visibility = View.GONE
        menuButton.visibility = View.GONE
        showStep(currentStep())
    }

    // ================================================================ veri

    private fun refresh(silent: Boolean) {
        if (!unlocked || loading) return
        loading = true
        if (!silent && home === HomeData.EMPTY) renderHome()
        val app = applicationContext
        background({ Account.refreshHome(app) }) { (data, err) ->
            loading = false
            if (data != null) {
                home = data
                renderAll()
            } else {
                renderHome()
                if (!silent) Toast.makeText(this, if (err == "network") R.string.err_network_cached else errorFor(err), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderAll() {
        renderHome()
        calendar.setEvents(home.events)
        settingsView.render(settingsContent)
        if (noticePanel.visibility == View.VISIBLE) notices.render(noticeList, home)
        updateBadge()
    }

    private fun renderHome() = homeView.render(homeContent, home, loading)

    private fun updateBadge() {
        val n = notices.unread(home)
        bellBadge.text = if (n > 99) "99+" else n.toString()
        bellBadge.visibility = if (n > 0) View.VISIBLE else View.GONE
        menuNotices.text = if (n > 0) getString(R.string.notices_count, n) else getString(R.string.notices)
    }

    // ================================================================ sayfalar

    private fun setupShell() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showPage(Page.HOME, fromNav = true)
                R.id.nav_calendar -> showPage(Page.CALENDAR, fromNav = true)
                R.id.nav_settings -> showPage(Page.SETTINGS, fromNav = true)
            }
            true
        }
        bottomNav.setOnItemReselectedListener { item ->
            // Sitedeyken Ana Sayfa'ya tekrar dokunmak site listesine döndürür
            if (item.itemId == R.id.nav_home && page == Page.SITE) showPage(Page.HOME, fromNav = true)
        }
        backButton.setOnClickListener { onBackPressedCompat() }
        findViewById<View>(R.id.bellButton).setOnClickListener { toggleNotices() }
        noticeScrim.setOnClickListener { hideNotices() }
        findViewById<View>(R.id.noticeAllRead).setOnClickListener {
            notices.markAllRead(home)
            notices.render(noticeList, home)
        }
        findViewById<View>(R.id.moreButton).setOnClickListener { showMoreMenu(it) }
    }

    private fun showPage(p: Page, fromNav: Boolean = false) {
        if (p != Page.SITE) site = null
        page = p
        savedPage = p
        homePage.visibility = if (p == Page.HOME) View.VISIBLE else View.GONE
        webView.visibility = if (p == Page.SITE) View.VISIBLE else View.GONE
        calendarPage.visibility = if (p == Page.CALENDAR) View.VISIBLE else View.GONE
        settingsPage.visibility = if (p == Page.SETTINGS) View.VISIBLE else View.GONE
        topTitle.text = when (p) {
            Page.HOME -> getString(R.string.app_name)
            Page.SITE -> site?.name ?: getString(R.string.app_name)
            Page.CALENDAR -> getString(R.string.nav_calendar)
            Page.SETTINGS -> getString(R.string.nav_settings)
        }
        backButton.visibility = if (p == Page.SITE) View.VISIBLE else View.GONE
        if (!fromNav) {
            val id = when (p) {
                Page.HOME, Page.SITE -> R.id.nav_home
                Page.CALENDAR -> R.id.nav_calendar
                Page.SETTINGS -> R.id.nav_settings
            }
            if (bottomNav.selectedItemId != id) bottomNav.menu.findItem(id).isChecked = true
        }
        if (p != Page.SITE && webView.url != null && webView.url != "about:blank") {
            // Siteden çıkınca ses dursun
            webView.loadUrl("about:blank")
            savedUrl = null
        }
        if (p == Page.SETTINGS) settingsView.render(settingsContent)
        if (p == Page.HOME) homePage.scrollTo(0, 0)
    }

    private fun openSite(link: SiteLink, url: String? = null) {
        if (BuildConfig.PARENT) return
        showPage(Page.SITE)
        site = link
        savedSite = link
        topTitle.text = link.name
        if (recPaused) recResumeUrl = url ?: link.url else webView.loadUrl(url ?: link.url)
    }

    private fun showEvent(ev: Event) {
        val box = Ui.vbox(this).apply { setPadding(Ui.dp(context, 24), Ui.dp(context, 6), Ui.dp(context, 24), 0) }
        box.addView(Ui.text(this, Format.dayLong(ev.date) + if (ev.start != null) "  ·  " + ev.timeText() else "", 15f, Ui.color(this, R.color.accent_text), true))
        if (ev.desc.isNotEmpty()) box.addView(Ui.text(this, ev.desc, 15f), Ui.matchWrap(10, this))
        val b = AlertDialog.Builder(this).setTitle(ev.title).setView(box).setNegativeButton(R.string.close, null)
        if (ev.zoom.isNotEmpty()) b.setPositiveButton(R.string.zoom_join) { _, _ -> joinZoom(ev.zoom) }
        else box.addView(Ui.muted(this, getString(R.string.event_no_zoom), 13f), Ui.matchWrap(10, this))
        if (page != Page.CALENDAR) b.setNeutralButton(R.string.event_open_day) { _, _ ->
            showPage(Page.CALENDAR)
            calendar.showDay(ev.date)
        }
        HomePage.secure(b.create())
    }

    private fun joinZoom(link: String) {
        hideNotices()
        ZoomJoin.join(this, link)
    }

    private fun showMoreMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menuInflater.inflate(R.menu.more, pm.menu)
        pm.menu.findItem(R.id.more_black).isVisible = !BuildConfig.PARENT
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.more_refresh -> refresh(silent = false)
                R.id.more_black -> enterBlackMode()
                R.id.more_lock -> lockApp()
            }
            true
        }
        pm.show()
    }

    // ================================================================ bildirim paneli

    private fun toggleNotices() {
        if (noticePanel.visibility == View.VISIBLE) hideNotices() else showNotices()
    }

    private fun showNotices() {
        hideMenuPanel()
        notices.render(noticeList, home)
        val lp = noticePanel.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = if (landscape) Ui.dp(this, 12) else topBar.height + Ui.dp(this, 4)
        lp.width = minOf(Ui.dp(this, 360), root.width - Ui.dp(this, 24))
        noticePanel.layoutParams = lp
        // Liste uzarsa ekranın %60'ını geçmesin
        val sc = findViewById<ScrollView>(R.id.noticeScroll)
        sc.layoutParams = sc.layoutParams.apply { height = LinearLayout.LayoutParams.WRAP_CONTENT }
        noticePanel.visibility = View.VISIBLE
        noticeScrim.visibility = View.VISIBLE
        noticePanel.doOnLayout {
            val max = (root.height * 0.6f).toInt()
            if (sc.height > max) sc.layoutParams = sc.layoutParams.apply { height = max }
        }
        noticePanel.alpha = 0f
        noticePanel.translationY = -Ui.dpf(this, 12f)
        noticePanel.animate().alpha(1f).translationY(0f).setDuration(160).start()
    }

    private fun hideNotices() {
        noticePanel.visibility = View.GONE
        noticeScrim.visibility = View.GONE
    }

    // ================================================================ dikey / yatay

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyMode()
    }

    /** Dikeyde alt menü + üst çubuk; yatayda yalnızca havada duran düğme (AssistiveTouch). */
    private fun applyMode() {
        if (!unlocked) return
        shell.visibility = View.VISIBLE
        topBar.visibility = if (landscape) View.GONE else View.VISIBLE
        bottomNav.visibility = if (landscape) View.GONE else View.VISIBLE
        hideMenuPanel()
        hideNotices()
        if (landscape && !blackMode) {
            menuButton.visibility = View.VISIBLE
            menuButton.doOnLayout { menuButton.applyPosition() }
        } else {
            menuButton.visibility = View.GONE
        }
    }

    private fun setupFloatingButton() {
        menuButton.restore(AppSettings.fabSide(this), AppSettings.fabY(this))
        menuButton.onSingleTap = { if (menuPanel.visibility == View.VISIBLE) hideMenuPanel() else showMenuPanel() }
        menuButton.onDoubleTap = { if (!BuildConfig.PARENT) enterBlackMode() else showNotices() }
        menuButton.onDragStart = { hideMenuPanel() }
        menuButton.onPositionChanged = { side, y -> AppSettings.setFabPosition(this, side, y) }
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if ((r - l) != (or - ol) || (b - t) != (ob - ot)) {
                if (menuButton.visibility == View.VISIBLE) menuButton.applyPosition()
                hideMenuPanel()
            }
        }
        menuScrim.setOnClickListener { hideMenuPanel() }
        findViewById<View>(R.id.menuHome).setOnClickListener { hideMenuPanel(); showPage(Page.HOME) }
        findViewById<View>(R.id.menuCalendar).setOnClickListener { hideMenuPanel(); showPage(Page.CALENDAR) }
        findViewById<View>(R.id.menuSettings).setOnClickListener { hideMenuPanel(); showPage(Page.SETTINGS) }
        menuNotices.setOnClickListener { hideMenuPanel(); showNotices() }
        findViewById<View>(R.id.menuBlack).apply {
            visibility = if (BuildConfig.PARENT) View.GONE else View.VISIBLE
            setOnClickListener { hideMenuPanel(); enterBlackMode() }
        }
        findViewById<View>(R.id.menuLock).setOnClickListener { hideMenuPanel(); lockApp() }
    }

    private fun showMenuPanel() {
        hideNotices()
        val gap = Ui.dpf(this, 8f)
        menuPanel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val pw = menuPanel.measuredWidth.toFloat()
        val ph = menuPanel.measuredHeight.toFloat()
        var px = if (menuButton.side == 1) menuButton.x - pw - gap else menuButton.x + menuButton.width + gap
        var py = menuButton.y + menuButton.height / 2f - ph / 2f
        px = px.coerceIn(gap, (root.width - pw - gap).coerceAtLeast(gap))
        py = py.coerceIn(gap, (root.height - ph - gap).coerceAtLeast(gap))
        menuPanel.x = px
        menuPanel.y = py
        menuScrim.visibility = View.VISIBLE
        menuPanel.visibility = View.VISIBLE
    }

    private fun hideMenuPanel() {
        menuPanel.visibility = View.GONE
        menuScrim.visibility = View.GONE
    }

    // ================================================================ site (WebView) — öğrenci

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (adBlockOn && request != null && AdBlock.isBlocked(request.url?.host)) {
                    return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                // Sitedeki Zoom bağlantıları da doğrudan toplantıya götürsün
                if (ZoomJoin.parse(u.toString()) != null) {
                    joinZoom(u.toString())
                    return true
                }
                val scheme = u.scheme?.lowercase()
                return scheme != "https" && scheme != "http"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url != "about:blank") {
                    savedUrl = url
                    if (adBlockOn) view?.evaluateJavascript(AdBlock.COSMETIC_JS, null)
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
    }

    private fun applySettings() {
        if (BuildConfig.PARENT) return
        webView.settings.textZoom = AppSettings.textZoom(this)
        webView.keepBackground = AppSettings.keepAudio(this)
        val ab = AppSettings.adBlock(this)
        if (ab != adBlockOn) {
            adBlockOn = ab
            if (page == Page.SITE && !recPaused) webView.reload()
        }
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
            }
        } catch (_: Throwable) {
        }
        if (unlocked) startKeepAliveService()
    }

    private fun startKeepAliveService() {
        if (BuildConfig.PARENT) return
        val svc = Intent(this, AudioKeepAliveService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        } catch (_: Throwable) {
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
    }

    // ================================================================ ses kaydı algılama — öğrenci

    private fun startRecordingWatch() {
        if (recCallback != null) return
        recCallback = RecordingWatch.register(this, ui) { onRecordings(it) }
        onRecordings(RecordingWatch.current(this))
    }

    private fun stopRecordingWatch() {
        RecordingWatch.unregister(this, recCallback)
        recCallback = null
        ui.removeCallbacks(recResume)
        if (recPaused) resumeAfterRecording(reload = false)
    }

    private fun onRecordings(sources: List<Int>) {
        if (!unlocked) return
        if (sources.isNotEmpty()) {
            ui.removeCallbacks(recResume)
            if (!recPaused) {
                recPaused = true
                recResumeUrl = if (page == Page.SITE) savedUrl ?: site?.url else null
                hideMenuPanel()
                hideNotices()
                webView.loadUrl("about:blank")
                recordNotice.visibility = View.VISIBLE
            }
        } else if (recPaused) {
            ui.removeCallbacks(recResume)
            ui.postDelayed(recResume, 1500)
        }
    }

    private fun resumeAfterRecording(reload: Boolean = true) {
        recPaused = false
        recordNotice.visibility = View.GONE
        val url = recResumeUrl
        recResumeUrl = null
        if (reload && unlocked && page == Page.SITE && url != null) webView.loadUrl(url)
    }

    // ================================================================ süre ölçümü — öğrenci

    private fun beginUsage() {
        if (BuildConfig.PARENT) return
        if (usageMark == 0L) usageMark = SystemClock.elapsedRealtime()
        if (lastFlush == 0L) lastFlush = SystemClock.elapsedRealtime()
        ui.removeCallbacks(usageTick)
        ui.postDelayed(usageTick, TICK_MS)
        flushUsageAsync()
    }

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

    // ================================================================ siyah ekran — öğrenci

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBlackOverlay() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { exitBlackMode(); return true }
        })
        blackOverlay.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); true }
    }

    private fun enterBlackMode() {
        if (blackMode || BuildConfig.PARENT) return
        blackMode = true
        hideMenuPanel()
        hideNotices()
        blackOverlay.visibility = View.VISIBLE
        menuButton.visibility = View.GONE
        val lp = window.attributes
        lp.screenBrightness = 0f
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        Toast.makeText(this, R.string.black_hint, Toast.LENGTH_LONG).show()
    }

    private fun exitBlackMode() {
        if (!blackMode) return
        blackMode = false
        blackOverlay.visibility = View.GONE
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        applyMode()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    // ================================================================ yaşam döngüsü

    override fun onResume() {
        super.onResume()
        OkuApp.blockAudioCapture(this)
        if (unlocked) {
            applySettings()
            webView.onResume()
            webView.resumeTimers()
            ui.removeCallbacks(autoRefresh)
            ui.postDelayed(autoRefresh, REFRESH_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(autoRefresh)
        // Bilerek webView.onPause() çağrılmıyor: ekran kapanınca ses devam eder
        if (unlocked && !BuildConfig.PARENT && AppSettings.keepAudio(this)) webView.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        if (blackMode) exitBlackMode()
        if (unlocked && !BuildConfig.PARENT) {
            tickUsage()
            flushUsageAsync()
        }
    }

    private fun onBackPressedCompat() {
        @Suppress("DEPRECATION")
        onBackPressed()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            recPaused -> Unit
            noticePanel.visibility == View.VISIBLE -> hideNotices()
            menuPanel.visibility == View.VISIBLE -> hideMenuPanel()
            blackMode -> exitBlackMode()
            !unlocked && step == Step.GOOGLE && googleWebView.canGoBack() -> googleWebView.goBack()
            unlocked && page == Page.SITE && webView.canGoBack() -> webView.goBack()
            unlocked && page != Page.HOME -> showPage(Page.HOME)
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        RecordingWatch.unregister(this, recCallback)
        if (unlocked && !BuildConfig.PARENT) tickUsage()
        if (isFinishing) {
            if (!BuildConfig.PARENT) stopService(Intent(this, AudioKeepAliveService::class.java))
            sessionUnlocked = false
        }
        googleWebView.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
