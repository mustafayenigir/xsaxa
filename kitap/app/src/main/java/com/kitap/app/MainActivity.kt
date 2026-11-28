package com.kitap.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        // Tema / yazı boyutu değişince Activity yeniden oluşur; kilit açık kalsın, sayfa kaybolmasın.
        private var sessionUnlocked = false
        private var savedPage: Page? = null
        private var savedSite: SiteLink? = null
        private var savedUrl: String? = null
        private var savedEvent: Event? = null
        private var savedPerson: String? = null

        private var usageMark = 0L
        private var lastFlush = 0L
        private val io: ExecutorService = Executors.newFixedThreadPool(3)

        private const val TICK_MS = 60_000L
        private const val FLUSH_EVERY_MS = 10 * 60_000L
        private const val MAX_TICK_SEC = 90L
        private const val REFRESH_MS = 5 * 60_000L
    }

    private enum class Step { CODE, NEW_PASSWORD, OLD_PASSWORD, ENTER }
    enum class Page { KITAP, HOME, SITE, CALENDAR, SETTINGS, EVENT, PEOPLE, PERSON, EDU, NOTICES, ERRORS }

    private val admin = BuildConfig.ADMIN
    private val firstPage get() = if (admin) Page.PEOPLE else Page.HOME

    private val ui = Handler(Looper.getMainLooper())
    private var step = Step.CODE
    private var busy = false
    private var unlocked = false
    private var page = Page.HOME
    private var site: SiteLink? = null
    private var eventReturn = Page.HOME
    private var blackMode = false
    private var landscape = false
    private var home: HomeData = HomeData.EMPTY
    private var loading = false
    private var bioShownForStep = false
    @Volatile private var adBlockOn = true

    // --- görünümler
    private lateinit var root: FrameLayout
    private lateinit var shell: View
    private lateinit var topBar: View
    private lateinit var topTitle: TextView
    private lateinit var backButton: ImageButton
    private lateinit var bellBox: View
    private lateinit var bellBadge: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var kitapHost: FrameLayout
    private lateinit var homePage: ScrollView
    private lateinit var homeContent: LinearLayout
    private lateinit var webView: BackgroundWebView
    private lateinit var calendarPage: FrameLayout
    private lateinit var settingsPage: ScrollView
    private lateinit var settingsContent: LinearLayout
    private lateinit var extraPage: FrameLayout
    private lateinit var noticePanel: LinearLayout
    private lateinit var noticeScrim: View
    private lateinit var noticeList: LinearLayout
    private lateinit var menuButton: AssistiveTouchView
    private lateinit var menuPanel: LinearLayout
    private lateinit var menuScrim: View
    private lateinit var blackOverlay: View
    private lateinit var recordNotice: View

    private lateinit var lockContainer: View
    private lateinit var lockTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var codeInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordConfirm: EditText
    private lateinit var unlockButton: Button
    private lateinit var bioButton: MaterialButton
    private lateinit var altButton: TextView
    private lateinit var lockMessage: TextView

    private lateinit var calendar: CalendarView
    private lateinit var homeView: HomePage
    private lateinit var settingsView: SettingsPage
    private lateinit var notices: Notices
    private lateinit var eventView: EventPage
    private var kitap: KitapPage? = null
    private lateinit var adminView: AdminPages

    // --- ses kaydı algılama
    private var recCallback: android.media.AudioManager.AudioRecordingCallback? = null
    private var recPaused = false
    private var recResumeUrl: String? = null
    private val recResume = Runnable { resumeAfterRecording() }

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

    // ================================================================ yazı boyutu (tüm uygulama)

    override fun attachBaseContext(newBase: Context) {
        val pct = AppSettings.fontPercent(newBase)
        if (pct == AppSettings.FONT_DEFAULT) return super.attachBaseContext(newBase)
        val cfg = Configuration(newBase.resources.configuration)
        cfg.fontScale = Resources.getSystem().configuration.fontScale * pct / 100f
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    private fun webTextZoom(): Int = (Resources.getSystem().configuration.fontScale * AppSettings.fontPercent(this)).toInt().coerceIn(50, 300)

    private fun isNight() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ================================================================ açılış

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KitapApp.blockAudioCapture(this)
        // === EKRAN GÖRÜNTÜSÜ / EKRAN KAYDI ENGELİ ===
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)
        bind()
        // Önceki çökme varsa kişiye gösterme; sessizce yöneticilere gönder
        io.execute { CrashReport.sendPending(applicationContext) }

        calendar = CalendarView(this) { openEvent(it) }
        calendarPage.addView(calendar)
        if (!admin) calendarPage.addView(com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(this).apply {
            id = R.id.planFab
            setText(R.string.plan_add)
            setIconResource(R.drawable.ic_add)
            setOnClickListener { openPlan(null) }
        }, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM or android.view.Gravity.END).apply {
            setMargins(0, 0, Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16))
        })
        homeView = HomePage(this, { openSite(it) }, { openEvent(it) }, { openPlan(it) })
        settingsView = SettingsPage(this, { applySettings() }, { recreate() }, { lockApp() }, { bioAvailable() },
            { on, done -> toggleBiometric(on, done) }) { Updater.check(this, io, manual = true) }
        notices = Notices(this, { hideNotices(); openEvent(it) }) { updateBadge() }
        eventView = EventPage(this, io)
        adminView = AdminPages(this, io, { refresh(silent = true, force = true) }, { showPerson(it) }, { showPage(Page.ERRORS) }) { sessionRejected() }

        setupLock()
        setupShell()
        setupWebView()
        setupFloatingButton()
        setupBlackOverlay()
        if (!admin) requestNotificationPermissionIfNeeded()

        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        showStep(currentStep())
        if (sessionUnlocked && Account.identity(this) != null) unlock(logLogin = false, restore = true)
    }

    private fun bind() {
        root = findViewById(R.id.rootLayout)
        shell = findViewById(R.id.shell)
        topBar = findViewById(R.id.topBar)
        topTitle = findViewById(R.id.topTitle)
        backButton = findViewById(R.id.backButton)
        bellBox = findViewById(R.id.bellBox)
        bellBadge = findViewById(R.id.bellBadge)
        bottomNav = findViewById(R.id.bottomNav)
        kitapHost = findViewById(R.id.kitapPage)
        homePage = findViewById(R.id.homePage)
        homeContent = findViewById(R.id.homeContent)
        webView = findViewById(R.id.webView)
        calendarPage = findViewById(R.id.calendarPage)
        settingsPage = findViewById(R.id.settingsPage)
        settingsContent = findViewById(R.id.settingsContent)
        extraPage = findViewById(R.id.extraPage)
        noticePanel = findViewById(R.id.noticePanel)
        noticeScrim = findViewById(R.id.noticeScrim)
        noticeList = findViewById(R.id.noticeList)
        menuButton = findViewById(R.id.menuButton)
        menuPanel = findViewById(R.id.menuPanel)
        menuScrim = findViewById(R.id.menuScrim)
        blackOverlay = findViewById(R.id.blackOverlay)
        recordNotice = findViewById(R.id.recordNotice)

        lockContainer = findViewById(R.id.lockContainer)
        lockTitle = findViewById(R.id.lockTitle)
        infoText = findViewById(R.id.infoText)
        codeInput = findViewById(R.id.codeInput)
        passwordInput = findViewById(R.id.passwordInput)
        passwordConfirm = findViewById(R.id.passwordConfirm)
        unlockButton = findViewById(R.id.unlockButton)
        bioButton = findViewById(R.id.bioButton)
        altButton = findViewById(R.id.altButton)
        lockMessage = findViewById(R.id.lockMessage)
    }

    // ================================================================ giriş adımları

    private fun currentStep(): Step = when {
        Account.identity(this) != null -> Step.ENTER
        Account.pendingCode(this) != null -> if (Account.pendingHasPassword(this)) Step.OLD_PASSWORD else Step.NEW_PASSWORD
        else -> Step.CODE
    }

    private fun setupLock() {
        unlockButton.setOnClickListener { handleAction() }
        passwordInput.setOnEditorActionListener { _, _, _ -> if (step != Step.NEW_PASSWORD) handleAction() else passwordConfirm.requestFocus(); true }
        passwordConfirm.setOnEditorActionListener { _, _, _ -> handleAction(); true }
        codeInput.setOnEditorActionListener { _, _, _ -> handleAction(); true }
        bioButton.setOnClickListener { askFingerprintToEnter() }
        altButton.setOnClickListener {
            when (step) {
                Step.ENTER, Step.OLD_PASSWORD -> HomePage.secure(AlertDialog.Builder(this)
                    .setTitle(R.string.forgot_title)
                    .setMessage(R.string.forgot_text)
                    .setPositiveButton(R.string.ok, null)
                    .setNeutralButton(R.string.other_code) { _, _ -> Reminders.cancelAll(this); Account.reset(this); AppSettings.setBiometric(this, false); showStep(Step.CODE) }
                    .create())
                Step.NEW_PASSWORD -> { Account.reset(this); showStep(Step.CODE) }
                Step.CODE -> Unit
            }
        }
    }

    private fun showStep(s: Step, message: String? = null) {
        step = s
        busy = false
        bioShownForStep = false
        unlockButton.isEnabled = true
        lockMessage.text = message ?: ""
        codeInput.visibility = if (s == Step.CODE) View.VISIBLE else View.GONE
        passwordInput.visibility = if (s != Step.CODE) View.VISIBLE else View.GONE
        passwordConfirm.visibility = if (s == Step.NEW_PASSWORD) View.VISIBLE else View.GONE
        passwordInput.text?.clear()
        passwordConfirm.text?.clear()
        lockContainer.visibility = if (!unlocked) View.VISIBLE else View.GONE
        val name = Account.name(this).ifBlank { Account.pendingName(this) }.trim().split(" ").firstOrNull().orEmpty()
        when (s) {
            Step.CODE -> {
                lockTitle.setText(R.string.code_title); infoText.setText(R.string.info_code); unlockButton.setText(R.string.code_button)
            }
            Step.NEW_PASSWORD -> {
                lockTitle.setText(R.string.create_title)
                infoText.text = if (name.isNotEmpty()) getString(R.string.info_password_name, name) else getString(R.string.info_password)
                unlockButton.setText(R.string.create_button)
            }
            Step.OLD_PASSWORD -> {
                lockTitle.text = if (name.isNotEmpty()) getString(R.string.enter_title_name, name) else getString(R.string.enter_title)
                infoText.setText(R.string.info_old_password)
                unlockButton.setText(R.string.enter_button)
            }
            Step.ENTER -> {
                lockTitle.text = if (name.isNotEmpty()) getString(R.string.enter_title_name, name) else getString(R.string.enter_title)
                infoText.text = ""
                unlockButton.setText(R.string.enter_button)
            }
        }
        altButton.visibility = if (s == Step.CODE) View.GONE else View.VISIBLE
        altButton.setText(if (s == Step.NEW_PASSWORD) R.string.other_code else R.string.forgot_link)
        infoText.visibility = if (infoText.text.isEmpty()) View.GONE else View.VISIBLE
        val bio = s == Step.ENTER && AppSettings.biometric(this) && bioAvailable()
        bioButton.visibility = if (bio) View.VISIBLE else View.GONE
        if (bio && !unlocked) ui.postDelayed({ if (step == Step.ENTER && !unlocked && !bioShownForStep && hasWindowFocus()) askFingerprintToEnter() }, 350)
    }

    private fun handleAction() {
        if (busy) return
        when (step) {
            Step.CODE -> handleCode()
            Step.NEW_PASSWORD -> handleCreatePassword()
            Step.OLD_PASSWORD -> handleOldPassword()
            Step.ENTER -> handleEnter()
        }
    }

    private fun setBusy(b: Boolean) {
        busy = b
        unlockButton.isEnabled = !b
        if (b) lockMessage.setText(R.string.checking)
    }

    private fun errorFor(code: String?): Int = when (code) {
        "invalid" -> R.string.err_code_invalid
        "passive" -> R.string.err_passive
        "busy" -> R.string.err_busy
        "auth" -> R.string.err_auth
        "wrong" -> R.string.err_wrong
        "bad_password" -> R.string.err_short
        else -> R.string.err_server
    }

    private fun <T> background(work: () -> T, done: (T) -> Unit) {
        io.execute {
            val r = work()
            runOnUiThread { if (!isFinishing && !isDestroyed) done(r) }
        }
    }

    // 1) Yetki kodu: Excel'de var mı?
    private fun handleCode() {
        val code = codeInput.text.toString().trim().replace(Regex("\\s+"), " ")
        if (code.length < 2) return lockMessage.setText(R.string.err_code_format)
        setBusy(true)
        background({ Api.check(code) }) { r ->
            setBusy(false)
            when {
                r == null -> lockMessage.setText(R.string.err_network)
                r.optBoolean("ok") -> {
                    val has = r.optBoolean("hasPassword")
                    Account.setPending(this, code, has, r.optString("name"))
                    showStep(if (has) Step.OLD_PASSWORD else Step.NEW_PASSWORD)
                    hideKeyboard()
                }
                else -> lockMessage.setText(errorFor(r.optString("error")))
            }
        }
    }

    // 2a) İlk kez: şifre belirle (Excel'e yazılır)
    private fun handleCreatePassword() {
        val code = Account.pendingCode(this) ?: return showStep(Step.CODE)
        val pwd = passwordInput.text.toString().trim()
        val confirm = passwordConfirm.text.toString().trim()
        when {
            pwd.isEmpty() -> return lockMessage.setText(R.string.err_empty)
            pwd.length < 4 -> return lockMessage.setText(R.string.err_short)
            pwd != confirm -> return lockMessage.setText(R.string.err_mismatch)
        }
        setBusy(true)
        background({ Api.register(code, pwd) }) { r ->
            setBusy(false)
            val uuid = r?.optString("uuid").orEmpty()
            when {
                r == null -> lockMessage.setText(R.string.err_network)
                r.optBoolean("ok") && uuid.isNotBlank() -> {
                    Account.save(this, code, uuid, pwd, r.optString("name"))
                    unlock(logLogin = false)
                    offerBiometric()
                }
                r.optString("error") == "has_password" -> {
                    Account.setPending(this, code, true, Account.pendingName(this))
                    showStep(Step.OLD_PASSWORD, getString(R.string.err_has_password))
                }
                r.optString("error") == "invalid" -> { Account.reset(this); showStep(Step.CODE, getString(R.string.err_code_invalid)) }
                else -> lockMessage.setText(errorFor(r.optString("error")))
            }
        }
    }

    // 2b) Şifresi olan kişi (yeni telefon / yeniden kurulum / şifre değişti)
    private fun handleOldPassword() {
        val code = Account.pendingCode(this) ?: return showStep(Step.CODE)
        val pwd = passwordInput.text.toString().trim()
        if (pwd.isEmpty()) return lockMessage.setText(R.string.err_empty)
        setBusy(true)
        background({ Api.relogin(code, pwd, Account.lastUuid(this)) }) { r ->
            setBusy(false)
            val uuid = r?.optString("uuid").orEmpty()
            when {
                r == null -> lockMessage.setText(R.string.err_network)
                r.optBoolean("ok") && uuid.isNotBlank() -> {
                    Account.save(this, code, uuid, pwd, r.optString("name"))
                    unlock(logLogin = true)
                    offerBiometric()
                }
                r.optString("error") == "no_password" -> { Account.setPending(this, code, false, Account.pendingName(this)); showStep(Step.NEW_PASSWORD) }
                r.optString("error") == "invalid" -> { Account.reset(this); showStep(Step.CODE, getString(R.string.err_code_invalid)) }
                else -> { lockMessage.setText(errorFor(r.optString("error"))); passwordInput.text?.clear() }
            }
        }
    }

    // 3) Sonraki açılışlar: şifre (cihazda doğrulanır) ya da parmak izi
    private fun handleEnter() {
        val pwd = passwordInput.text.toString().trim()
        if (pwd.isEmpty()) return lockMessage.setText(R.string.err_empty)
        if (Account.checkPassword(this, pwd)) {
            unlock(logLogin = true)
        } else {
            // Yönetim şifreyi değiştirmiş olabilir: sunucuya sor
            val code = Account.identity(this)?.code ?: return
            setBusy(true)
            background({ Api.relogin(code, pwd, Account.lastUuid(this)) }) { r ->
                setBusy(false)
                val uuid = r?.optString("uuid").orEmpty()
                if (r != null && r.optBoolean("ok") && uuid.isNotBlank()) {
                    Account.save(this, code, uuid, pwd, r.optString("name"))
                    unlock(logLogin = false)
                } else {
                    lockMessage.setText(if (r == null) R.string.err_wrong_offline else errorFor(r.optString("error")))
                    passwordInput.text?.clear()
                }
            }
        }
    }

    // ================================================================ parmak izi

    private fun bioAvailable(): Boolean = try {
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Throwable) { false }

    private fun fingerprint(title: Int, ok: () -> Unit, fail: () -> Unit = {}) {
        try {
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { ok() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { fail() }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(title))
                .setSubtitle(getString(R.string.app_name))
                .setNegativeButtonText(getString(R.string.bio_use_password))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build())
        } catch (_: Throwable) {
            fail()
        }
    }

    private fun askFingerprintToEnter() {
        if (unlocked || step != Step.ENTER) return
        bioShownForStep = true
        fingerprint(R.string.bio_title_enter, { if (!unlocked && Account.identity(this) != null) unlock(logLogin = true) })
    }

    private fun toggleBiometric(on: Boolean, done: (Boolean) -> Unit) {
        if (!on) { AppSettings.setBiometric(this, false); done(true); return }
        fingerprint(R.string.bio_title_enable, { AppSettings.setBiometric(this, true); done(true) }, { done(false) })
    }

    private fun offerBiometric() {
        if (AppSettings.biometricAsked(this) || !bioAvailable()) return
        AppSettings.setBiometricAsked(this)
        HomePage.secure(AlertDialog.Builder(this)
            .setTitle(R.string.bio_offer_title)
            .setMessage(R.string.bio_offer_text)
            .setPositiveButton(R.string.yes) { _, _ -> toggleBiometric(true) { ok -> if (ok) Toast.makeText(this, R.string.bio_enabled, Toast.LENGTH_SHORT).show() } }
            .setNegativeButton(R.string.later, null)
            .create())
    }

    // ================================================================ kilit aç / kapa

    private fun unlock(logLogin: Boolean, restore: Boolean = false) {
        unlocked = true
        sessionUnlocked = true
        passwordInput.text?.clear()
        lockContainer.visibility = View.GONE
        hideKeyboard()

        if (admin) {
            Account.cachedAdmin(this)?.let { adminView.data = it }
        } else {
            home = Account.cachedHome(this) ?: HomeData.EMPTY
            ensureKitap()
        }
        renderAll()

        if (!admin) {
            applySettings()
            startKeepAliveService()
            beginUsage()
            startRecordingWatch()
        }
        val id = Account.identity(this)
        if (logLogin && id != null) io.execute {
            val r = Api.login(id)
            if (r?.optString("error") == "auth") runOnUiThread { sessionRejected() }
        }

        val sp = savedPage
        if (restore && sp != null) {
            when (sp) {
                Page.SITE -> savedSite?.let { openSite(it, savedUrl) } ?: showPage(firstPage)
                Page.EVENT -> savedEvent?.let { openEvent(it) } ?: showPage(firstPage)
                Page.PERSON -> savedPerson?.let { showPerson(it) } ?: showPage(firstPage)
                else -> showPage(sp)
            }
        } else showPage(firstPage)
        refresh(silent = false)
        ui.removeCallbacks(autoRefresh)
        ui.postDelayed(autoRefresh, REFRESH_MS)
        if (!admin) Reminders.schedule(this, home.plans)
        ui.postDelayed({ if (unlocked) Updater.check(this, io, manual = false) }, 2500)
    }

    private fun lockApp(message: String? = null) {
        exitBlackMode()
        hideNotices()
        hideMenuPanel()
        if (!admin) {
            endUsage()
            stopRecordingWatch()
            stopService(Intent(this, AudioKeepAliveService::class.java))
            kitap?.setVisible(false)
        }
        ui.removeCallbacks(autoRefresh)
        unlocked = false
        sessionUnlocked = false
        savedPage = null
        savedSite = null
        savedUrl = null
        site = null
        webView.loadUrl("about:blank")
        setFullscreen(false)
        shell.visibility = View.GONE
        menuButton.visibility = View.GONE
        showStep(currentStep(), message)
    }

    /** Sunucu kimliği kabul etmedi: şifre değişmiş, kişi pasif yapılmış ya da başka telefonda giriş yapılmış. */
    private fun sessionRejected() {
        if (!unlocked && step != Step.ENTER) return
        Account.invalidate(this)
        lockApp(getString(R.string.err_session))
    }

    // ================================================================ veri

    private fun refresh(silent: Boolean, force: Boolean = false) {
        if (!unlocked || (loading && !force)) return
        loading = true
        val app = applicationContext
        if (admin) {
            background({ Account.refreshAdmin(app) }) { (data, err) ->
                loading = false
                if (data != null) {
                    adminView.data = data
                    renderAll()
                } else if (err == "auth") sessionRejected()
                else if (!silent) Toast.makeText(this, if (err == "network") R.string.err_network_cached else errorFor(err), Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!silent && home === HomeData.EMPTY) renderHome()
        background({ Account.refreshHome(app) }) { (data, err) ->
            loading = false
            if (data != null) {
                home = data
                renderAll()
                Reminders.schedule(this, data.plans)
            } else {
                renderHome()
                if (err == "auth") sessionRejected()
                else if (!silent) Toast.makeText(this, if (err == "network") R.string.err_network_cached else errorFor(err), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderAll() {
        if (admin) {
            when (page) {
                Page.PEOPLE -> adminView.renderPeople(extraPage)
                Page.EDU -> adminView.renderEvents(extraPage)
                Page.NOTICES -> adminView.renderNotices(extraPage)
                else -> Unit
            }
            if (page == Page.SETTINGS) settingsView.render(settingsContent)
            return
        }
        applyAccess()
        renderHome()
        calendar.setEvents(home.calendarItems())
        if (page == Page.SETTINGS) settingsView.render(settingsContent)
        if (noticePanel.visibility == View.VISIBLE) notices.render(noticeList, home)
        updateBadge()
    }

    private fun renderHome() = homeView.render(homeContent, home, loading)

    private var menuAccess: Boolean? = null

    /** "Göremez" yapılan kişide Kitap (Kur'an meali) sekmesi ve linkler hiç görünmez. */
    private fun applyAccess() {
        if (admin) return
        bottomNav.menu.findItem(R.id.nav_kitap)?.isVisible = home.access
        bottomNav.menu.findItem(R.id.nav_calendar)?.isVisible = home.access
        // Göremez: bildirimler ve kişisel notlar da tamamen gizli
        bellBox.visibility = if (home.access) View.VISIBLE else View.GONE
        calendarPage.findViewById<View>(R.id.planFab)?.visibility = if (home.access) View.VISIBLE else View.GONE
        if (!home.access) { hideNotices(); Reminders.cancelAll(this) }
        if (menuAccess != home.access) { menuAccess = home.access; buildMenu() }
        if (!home.access && page == Page.KITAP) showPage(Page.HOME)
        if (!home.access && (page == Page.SITE || page == Page.CALENDAR || page == Page.EVENT)) showPage(Page.HOME)
    }

    private fun openPlan(p: Plan?) {
        if (admin || !home.access) return
        val date = if (page == Page.CALENDAR) calendar.focusDate else java.time.LocalDate.now()
        PlanDialog.show(this, io, p, if (p == null && date.isBefore(java.time.LocalDate.now())) java.time.LocalDate.now() else date, { plans ->
            home = home.withPlans(plans)
            renderAll()
            Reminders.schedule(this, plans)
        }) { sessionRejected() }
    }

    private fun updateBadge() {
        if (admin) return
        val n = notices.unread(home)
        bellBadge.text = if (n > 99) "99+" else n.toString()
        bellBadge.visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    // ================================================================ sayfalar

    private fun ensureKitap() {
        if (admin || kitap != null) return
        val k = KitapPage(this, io) { sessionRejected() }
        kitap = k
        k.setTextZoom(webTextZoom())
        k.setDark(isNight())
        kitapHost.addView(k.web, FrameLayout.LayoutParams(-1, -1))
        k.load()
    }

    private fun setupShell() {
        bottomNav.setOnItemSelectedListener { item ->
            navTarget(item.itemId)?.let { showPage(it, fromNav = true) }
            true
        }
        bottomNav.setOnItemReselectedListener { item ->
            val t = navTarget(item.itemId)
            if (t != null && t != page) showPage(t, fromNav = true)
        }
        backButton.setOnClickListener { onBackPressedCompat() }
        bellBox.visibility = if (admin) View.GONE else View.VISIBLE
        findViewById<View>(R.id.bellButton).setOnClickListener { toggleNotices() }
        noticeScrim.setOnClickListener { hideNotices() }
        findViewById<View>(R.id.noticeAllRead).setOnClickListener {
            notices.markAllRead(home)
            notices.render(noticeList, home)
        }
        findViewById<View>(R.id.moreButton).setOnClickListener { showMoreMenu(it) }
    }

    private fun navTarget(id: Int): Page? = when (id) {
        R.id.nav_kitap -> Page.KITAP
        R.id.nav_home -> Page.HOME
        R.id.nav_calendar -> Page.CALENDAR
        R.id.nav_settings -> Page.SETTINGS
        R.id.nav_people -> Page.PEOPLE
        R.id.nav_edu -> Page.EDU
        R.id.nav_notices -> Page.NOTICES
        else -> null
    }

    private fun navIdFor(p: Page): Int = when (p) {
        Page.KITAP -> R.id.nav_kitap
        Page.HOME, Page.SITE -> R.id.nav_home
        Page.EVENT -> if (eventReturn == Page.CALENDAR) R.id.nav_calendar else R.id.nav_home
        Page.CALENDAR -> R.id.nav_calendar
        Page.SETTINGS -> R.id.nav_settings
        Page.PEOPLE, Page.PERSON, Page.ERRORS -> R.id.nav_people
        Page.EDU -> R.id.nav_edu
        Page.NOTICES -> R.id.nav_notices
    }

    private fun titleFor(p: Page): String = when (p) {
        Page.KITAP -> getString(R.string.nav_kitap)
        Page.HOME -> getString(R.string.app_name)
        Page.SITE -> site?.name ?: getString(R.string.app_name)
        Page.CALENDAR -> getString(R.string.nav_calendar)
        Page.SETTINGS -> getString(R.string.nav_settings)
        Page.EVENT -> getString(R.string.edu_title)
        Page.PEOPLE -> getString(R.string.nav_people)
        Page.PERSON -> getString(R.string.adm_person_title)
        Page.EDU -> getString(R.string.nav_edu)
        Page.NOTICES -> getString(R.string.notices)
        Page.ERRORS -> getString(R.string.adm_crashes)
    }

    private fun showPage(p: Page, fromNav: Boolean = false) {
        if (p != Page.SITE) site = null
        val wasKitap = page == Page.KITAP
        page = p
        savedPage = p
        if (p != Page.EVENT) eventView.clear()
        kitapHost.visibility = if (p == Page.KITAP) View.VISIBLE else View.GONE
        homePage.visibility = if (p == Page.HOME) View.VISIBLE else View.GONE
        webView.visibility = if (p == Page.SITE) View.VISIBLE else View.GONE
        calendarPage.visibility = if (p == Page.CALENDAR) View.VISIBLE else View.GONE
        settingsPage.visibility = if (p == Page.SETTINGS) View.VISIBLE else View.GONE
        val extra = p == Page.EVENT || p == Page.PEOPLE || p == Page.PERSON || p == Page.EDU || p == Page.NOTICES || p == Page.ERRORS
        extraPage.visibility = if (extra) View.VISIBLE else View.GONE
        if (wasKitap != (p == Page.KITAP)) kitap?.setVisible(p == Page.KITAP)
        topTitle.text = titleFor(p)
        backButton.visibility = if (p == Page.EVENT || p == Page.PERSON || p == Page.ERRORS) View.VISIBLE else View.GONE
        if (!fromNav) {
            val id = navIdFor(p)
            bottomNav.menu.findItem(id)?.let { if (bottomNav.selectedItemId != id) it.isChecked = true }
        }
        if (p != Page.SITE && webView.url != null && webView.url != "about:blank") {
            webView.loadUrl("about:blank") // içerikten çıkınca ses dursun
            savedUrl = null
        }
        when (p) {
            Page.SETTINGS -> settingsView.render(settingsContent)
            Page.HOME -> homePage.scrollTo(0, 0)
            Page.PEOPLE -> adminView.renderPeople(extraPage)
            Page.EDU -> adminView.renderEvents(extraPage)
            Page.NOTICES -> adminView.renderNotices(extraPage)
            Page.ERRORS -> adminView.renderCrashes(extraPage)
            else -> Unit
        }
        applyMode()
    }

    private fun showPerson(code: String) {
        savedPerson = code
        showPage(Page.PERSON)
        adminView.renderPerson(extraPage, code)
    }

    /** İçerik (site) tam ekran açılır: üst/alt menü gizli, yalnızca havada duran düğme. */
    private fun openSite(link: SiteLink, url: String? = null) {
        if (admin) return
        site = link
        showPage(Page.SITE)
        site = link
        savedSite = link
        if (recPaused) recResumeUrl = url ?: link.url else webView.loadUrl(url ?: link.url)
    }

    private fun openEvent(ev: Event) {
        if (!home.access) return
        ev.plan?.let { return openPlan(it) }
        if (page != Page.EVENT) eventReturn = if (page == Page.CALENDAR) Page.CALENDAR else Page.HOME
        savedEvent = ev
        showPage(Page.EVENT)
        eventView.render(extraPage, ev, home.access)
    }

    private fun showMoreMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, R.string.refresh)
        if (!admin) pm.menu.add(0, 2, 1, R.string.menu_black)
        if (admin) pm.menu.add(0, 4, 1, getString(R.string.adm_crashes) + if (adminView.data.crashCount > 0) " (${adminView.data.crashCount})" else "")
        pm.menu.add(0, 3, 2, R.string.menu_lock)
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> refresh(silent = false)
                2 -> enterBlackMode()
                3 -> lockApp()
                4 -> showPage(Page.ERRORS)
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
        if (admin) return showPage(Page.NOTICES)
        if (!home.access) return
        hideMenuPanel()
        notices.render(noticeList, home)
        val lp = noticePanel.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = if (topBar.visibility == View.VISIBLE) topBar.height + Ui.dp(this, 4) else Ui.dp(this, 12)
        lp.width = minOf(Ui.dp(this, 360), root.width - Ui.dp(this, 24))
        noticePanel.layoutParams = lp
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

    // ================================================================ dikey / yatay / tam ekran

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyMode()
    }

    /**
     * Dikeyde: üst çubuk (sağda zil ve dikey üç nokta) + alt menü.
     * Yatayda ve içerik (site) açıkken: menüler gizli, yalnızca havada duran düğme.
     */
    private fun applyMode() {
        if (!unlocked) return
        shell.visibility = View.VISIBLE
        val full = page == Page.SITE
        val bars = !landscape && !full
        topBar.visibility = if (bars) View.VISIBLE else View.GONE
        bottomNav.visibility = if (bars) View.VISIBLE else View.GONE
        setFullscreen(full && !blackMode)
        hideMenuPanel()
        if (!bars && !blackMode) {
            menuButton.visibility = View.VISIBLE
            menuButton.doOnLayout { menuButton.applyPosition() }
        } else {
            menuButton.visibility = View.GONE
        }
    }

    private fun setFullscreen(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else if (!blackMode) {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupFloatingButton() {
        menuButton.restore(AppSettings.fabSide(this), AppSettings.fabY(this))
        menuButton.onSingleTap = { if (menuPanel.visibility == View.VISIBLE) hideMenuPanel() else showMenuPanel() }
        menuButton.onDoubleTap = { if (!admin) enterBlackMode() }
        menuButton.onDragStart = { hideMenuPanel() }
        menuButton.onPositionChanged = { side, y -> AppSettings.setFabPosition(this, side, y) }
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if ((r - l) != (or - ol) || (b - t) != (ob - ot)) {
                if (menuButton.visibility == View.VISIBLE) menuButton.applyPosition()
                hideMenuPanel()
            }
        }
        menuScrim.setOnClickListener { hideMenuPanel() }
        buildMenu()
    }

    /** Havada duran düğmenin menüsü. */
    private fun buildMenu() {
        menuPanel.removeAllViews()
        fun item(icon: Int, text: String, click: () -> Unit) {
            val v = layoutInflater.inflate(R.layout.panel_item, menuPanel, false) as TextView
            v.text = text
            v.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            v.setOnClickListener { hideMenuPanel(); click() }
            menuPanel.addView(v)
        }
        if (admin) {
            item(R.drawable.ic_people_light, getString(R.string.nav_people)) { showPage(Page.PEOPLE) }
            item(R.drawable.ic_calendar_light, getString(R.string.nav_edu)) { showPage(Page.EDU) }
            item(R.drawable.ic_bell, getString(R.string.notices)) { showPage(Page.NOTICES) }
            item(R.drawable.ic_settings_light, getString(R.string.nav_settings)) { showPage(Page.SETTINGS) }
            item(R.drawable.ic_bell, getString(R.string.adm_crashes)) { showPage(Page.ERRORS) }
        } else {
            if (home.access) item(R.drawable.ic_book_light, getString(R.string.nav_kitap)) { showPage(Page.KITAP) }
            item(R.drawable.ic_home_light, getString(R.string.nav_home)) { showPage(Page.HOME) }
            if (home.access) item(R.drawable.ic_calendar_light, getString(R.string.nav_calendar)) { showPage(Page.CALENDAR) }
            item(R.drawable.ic_settings_light, getString(R.string.nav_settings)) { showPage(Page.SETTINGS) }
            if (home.access) item(R.drawable.ic_bell, getString(R.string.notices)) { showNotices() }
            item(R.drawable.ic_power, getString(R.string.menu_black)) { enterBlackMode() }
        }
        item(R.drawable.ic_lock, getString(R.string.menu_lock)) { lockApp() }
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

    // ================================================================ içerik (WebView)

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
                // İçerikteki Zoom bağlantıları telefondaki Zoom uygulamasında açılsın
                if (ZoomOpen.parse(u.toString()) != null) {
                    ZoomOpen.open(this@MainActivity, u.toString())
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
        if (admin) return
        webView.settings.textZoom = webTextZoom()
        kitap?.setTextZoom(webTextZoom())
        kitap?.setDark(isNight())
        webView.keepBackground = AppSettings.keepAudio(this)
        val ab = AppSettings.adBlock(this)
        if (ab != adBlockOn) {
            adBlockOn = ab
            if (page == Page.SITE && !recPaused) webView.reload()
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isNight())
            }
        } catch (_: Throwable) {
        }
        if (unlocked) startKeepAliveService()
    }

    private fun startKeepAliveService() {
        if (admin) return
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

    // ================================================================ ses kaydı algılama

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

    // ================================================================ süre ölçümü

    private fun beginUsage() {
        if (admin) return
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

    // ================================================================ siyah ekran

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBlackOverlay() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { exitBlackMode(); return true }
        })
        blackOverlay.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); true }
    }

    private fun enterBlackMode() {
        if (blackMode || admin) return
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

    // ================================================================ geri tuşu

    private fun onBackPressedCompat() {
        @Suppress("DEPRECATION")
        onBackPressed()
    }

    private fun confirmExit() {
        HomePage.secure(AlertDialog.Builder(this)
            .setMessage(R.string.exit_q)
            .setPositiveButton(R.string.exit_yes) { _, _ -> finish() }
            .setNegativeButton(R.string.exit_no, null)
            .create())
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            recPaused -> Unit
            noticePanel.visibility == View.VISIBLE -> hideNotices()
            menuPanel.visibility == View.VISIBLE -> hideMenuPanel()
            blackMode -> exitBlackMode()
            !unlocked -> confirmExit()
            page == Page.SITE -> if (webView.canGoBack()) webView.goBack() else showPage(Page.HOME)
            page == Page.KITAP -> kitap?.back { handled -> if (!handled) showPage(Page.HOME) } ?: showPage(Page.HOME)
            page == Page.EVENT -> showPage(eventReturn)
            page == Page.PERSON -> showPage(Page.PEOPLE)
            page != firstPage -> showPage(firstPage)
            else -> confirmExit()
        }
    }

    // ================================================================ yaşam döngüsü

    override fun onResume() {
        super.onResume()
        KitapApp.blockAudioCapture(this)
        if (Updater.hasPendingInstall()) Updater.install(this)
        if (unlocked) {
            applySettings()
            webView.onResume()
            webView.resumeTimers()
            if (page == Page.KITAP) kitap?.setVisible(true)
            ui.removeCallbacks(autoRefresh)
            ui.postDelayed(autoRefresh, REFRESH_MS)
            applyMode()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !unlocked && step == Step.ENTER && !bioShownForStep && bioButton.visibility == View.VISIBLE) askFingerprintToEnter()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(autoRefresh)
        if (unlocked && !admin && AppSettings.keepAudio(this)) webView.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        if (blackMode) exitBlackMode()
        if (unlocked && !admin) {
            kitap?.setVisible(false)
            tickUsage()
            flushUsageAsync()
        }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        RecordingWatch.unregister(this, recCallback)
        if (unlocked && !admin) tickUsage()
        if (isFinishing) {
            if (!admin) stopService(Intent(this, AudioKeepAliveService::class.java))
            sessionUnlocked = false
            savedPage = null
        }
        kitap?.web?.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
