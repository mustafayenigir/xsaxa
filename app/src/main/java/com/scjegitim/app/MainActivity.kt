package com.scjegitim.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {

    // Uygulamanın açacağı tek adres
    private val siteUrl = "https://scjegitim.blogspot.com"

    // Şifrenin saklandığı yer (cihazın özel alanı)
    private val prefsName = "scj_secure_prefs"
    private val keyHash = "pwd_hash"
    private val keySalt = "pwd_salt"

    private lateinit var webView: WebView
    private lateinit var lockContainer: View
    private lateinit var lockTitle: TextView
    private lateinit var passwordInput: EditText
    private lateinit var passwordConfirm: EditText
    private lateinit var unlockButton: Button
    private lateinit var lockMessage: TextView

    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // === EKRAN GÖRÜNTÜSÜ / EKRAN KAYDI ENGELİ (banka uygulamaları gibi) ===
        // Tüm uygulama boyunca geçerlidir. Çoklu görev ekranında da içerik gizlenir.
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

        setupWebView()
        requestNotificationPermissionIfNeeded()

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (prefs.contains(keyHash)) showEnterMode() else showCreateMode()

        unlockButton.setOnClickListener { handleUnlock() }
    }

    /** İlk kez: şifre oluşturma ekranı */
    private fun showCreateMode() {
        lockTitle.setText(R.string.create_title)
        passwordConfirm.visibility = View.VISIBLE
        unlockButton.setText(R.string.create_button)
        lockMessage.setText(R.string.create_hint)
    }

    /** Sonraki açılışlar: şifre girme ekranı */
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
            openSite()
        } else {
            // --- ŞİFRE DOĞRULAMA ---
            val salt = prefs.getString(keySalt, "") ?: ""
            val hash = hashPassword(pwd, salt)
            if (hash == prefs.getString(keyHash, null)) {
                openSite()
            } else {
                lockMessage.setText(R.string.err_wrong)
                passwordInput.text?.clear()
            }
        }
    }

    private fun openSite() {
        unlocked = true
        passwordInput.text?.clear()
        passwordConfirm.text?.clear()
        lockContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE

        // Ekran kapanınca sitenin/ses akışının kapanmaması için ön plan servisi
        val svc = Intent(this, AudioKeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }

        webView.loadUrl(siteUrl)
    }

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
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
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

    // --- Arka planda çalmaya devam: ekran kapanınca WebView'i DURAKLATMIYORUZ ---
    override fun onPause() {
        super.onPause()
        // Bilerek webView.onPause() çağrılmıyor; böylece ses devam eder.
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) webView.onResume()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (unlocked && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
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
