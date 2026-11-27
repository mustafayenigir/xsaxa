package com.oku.app

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.lang.ref.WeakReference
import us.zoom.sdk.JoinMeetingOptions
import us.zoom.sdk.JoinMeetingParams
import us.zoom.sdk.MeetingError
import us.zoom.sdk.MeetingParameter
import us.zoom.sdk.MeetingServiceListener
import us.zoom.sdk.MeetingStatus
import us.zoom.sdk.ZoomError
import us.zoom.sdk.ZoomSDK
import us.zoom.sdk.ZoomSDKInitParams
import us.zoom.sdk.ZoomSDKInitializeListener

/**
 * Zoom Meeting SDK ile toplantıya UYGULAMANIN İÇİNDEN katılma.
 * İmza (JWT) sunucudan alınır; gizli anahtar uygulamada yoktur.
 * Bir adım olmazsa sebebi ekranda gösterilir ve telefondaki Zoom uygulamasıyla açma önerilir.
 */
object ZoomSdkBridge {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = false
    @Volatile private var endedAt = 0L
    private var listening = false
    private var initializing = false
    private var current: WeakReference<Activity>? = null
    private var lastLink: String = ""
    private var lastMeeting: ZoomJoin.Meeting? = null
    private val log = ArrayList<String>()
    private var joinedOk = false
    private var reported = false

    /** Toplantıdayken (ya da yeni çıkıldıysa) kendi mikrofonumuz "kayıt" sayılmasın. */
    fun inMeeting(): Boolean = active || SystemClock.elapsedRealtime() - endedAt < 4000

    fun install(app: Application) {
        ZoomJoin.inApp = { act, link, m -> start(act, link, m); true }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                if (a.javaClass.name.startsWith("com.oku.")) current = WeakReference(a)
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    private fun note(c: Context, s: String) {
        log.add(s)
        if (log.size > 30) log.removeAt(0)
        CrashReport.step(c, s)
    }

    private fun start(act: Activity, link: String, m: ZoomJoin.Meeting) {
        log.clear(); reported = false; joinedOk = false
        lastLink = link; lastMeeting = m
        note(act, "1 başla (toplantı ${m.number}, şifre ${if (m.password.isEmpty()) "yok" else "var"})")
        val sdk = try { ZoomSDK.getInstance() } catch (e: Throwable) {
            return fail(act, "Zoom bileşeni yüklenemedi: $e")
        }
        if (sdk.isInitialized) {
            note(act, "2 Zoom zaten hazır")
            join(act, m)
            return
        }
        if (initializing) return
        initializing = true
        Toast.makeText(act, R.string.zoom_preparing, Toast.LENGTH_SHORT).show()
        val app = act.applicationContext
        Thread {
            val id = Account.identity(app)
            val r = try { if (id != null) Api.zoomSig(id, m.number) else null } catch (e: Throwable) { null }
            val sig = r?.optString("signature").orEmpty()
            main.post {
                if (id == null) { initializing = false; return@post fail(act, "Hesap bilgisi yok (yeniden giriş yapın).") }
                if (r == null) { initializing = false; return@post fail(act, "Sunucuya ulaşılamadı (internet?).") }
                if (!r.optBoolean("ok") || sig.isBlank()) {
                    initializing = false
                    return@post fail(act, "Sunucu imza vermedi: ${r.optString("error")}")
                }
                note(act, "3 imza alındı, Zoom başlatılıyor")
                val params = ZoomSDKInitParams().apply {
                    jwtToken = sig
                    domain = "zoom.us"
                    enableLog = true
                }
                try {
                    sdk.initialize(app, object : ZoomSDKInitializeListener {
                        override fun onZoomSDKInitializeResult(errorCode: Int, internalErrorCode: Int) {
                            initializing = false
                            note(act, "4 Zoom başlatma sonucu: ${zoomErrName(errorCode)} ($errorCode/$internalErrorCode)")
                            if (errorCode == ZoomError.ZOOM_ERROR_SUCCESS) join(act, m)
                            else fail(act, "Zoom başlatılamadı: ${zoomErrName(errorCode)} (kod $errorCode/$internalErrorCode)")
                        }

                        override fun onZoomAuthIdentityExpired() { note(app, "imza süresi doldu") }
                    }, params)
                } catch (e: Throwable) {
                    initializing = false
                    fail(act, "Zoom başlatma hatası: $e")
                }
            }
        }.start()
    }

    private fun join(act: Activity, m: ZoomJoin.Meeting) {
        if (act.isFinishing || act.isDestroyed) return
        val svc = ZoomSDK.getInstance().meetingService ?: return fail(act, "Zoom toplantı servisi yok.")
        if (!listening) {
            listening = true
            svc.addListener(object : MeetingServiceListener {
                override fun onMeetingStatusChanged(status: MeetingStatus?, errorCode: Int, internalErrorCode: Int) {
                    val on = status != null && status != MeetingStatus.MEETING_STATUS_IDLE &&
                        status != MeetingStatus.MEETING_STATUS_ENDED && status != MeetingStatus.MEETING_STATUS_FAILED
                    if (active && !on) endedAt = SystemClock.elapsedRealtime()
                    active = on
                    val c = current?.get() ?: act
                    note(c, "durum: $status, hata: ${meetingErrName(errorCode)} ($errorCode/$internalErrorCode)")
                    if (status == MeetingStatus.MEETING_STATUS_INMEETING) joinedOk = true
                    val bad = status == MeetingStatus.MEETING_STATUS_FAILED ||
                        (!on && !joinedOk && errorCode != MeetingError.MEETING_ERROR_SUCCESS)
                    if (bad) {
                        main.postDelayed({
                            val a = current?.get() ?: return@postDelayed
                            fail(a, "Toplantıya katılınamadı: ${meetingErrName(errorCode)} (kod $errorCode/$internalErrorCode)")
                        }, 800)
                    }
                }

                override fun onMeetingParameterNotification(p: MeetingParameter?) {}
            })
        }
        val opts = JoinMeetingOptions().apply {
            no_invite = true      // başkasını davet etme yok
            no_share = true       // öğrenci ekran paylaşamasın
            no_record = true      // toplantıyı kaydetme yok
            no_driving_mode = true
        }
        val p = JoinMeetingParams().apply {
            meetingNo = m.number
            password = m.password
            displayName = Account.name(act).ifBlank { Account.identity(act)?.no ?: "Öğrenci" }
        }
        active = true
        note(act, "5 katılma isteği gönderiliyor")
        val rc = try { svc.joinMeetingWithParams(act, p, opts) } catch (e: Throwable) {
            active = false
            return fail(act, "Katılma hatası: $e")
        }
        note(act, "6 katılma isteği sonucu: ${meetingErrName(rc)} ($rc)")
        if (rc != MeetingError.MEETING_ERROR_SUCCESS) {
            active = false
            fail(act, "Katılma reddedildi: ${meetingErrName(rc)} (kod $rc)")
        }
    }

    /** Sebebi ekranda gösterir; kullanıcı isterse telefondaki Zoom uygulamasıyla açar. */
    private fun fail(act: Activity, reason: String) {
        note(act, "SONUÇ: $reason")
        if (reported || act.isFinishing || act.isDestroyed) return
        reported = true
        val text = reason + "\n\nAdımlar:\n" + log.joinToString("\n")
        try {
            AlertDialog.Builder(act)
                .setTitle("Zoom uygulama içinde açılamadı")
                .setMessage(text)
                .setPositiveButton("Kopyala") { _, _ ->
                    val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("zoom", text))
                    Toast.makeText(act, "Kopyalandı", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Zoom uygulamasında aç") { _, _ -> ZoomJoin.openZoomApp(act, lastLink, lastMeeting) }
                .setNegativeButton("Kapat", null)
                .show()
        } catch (_: Throwable) {
            Toast.makeText(act, reason, Toast.LENGTH_LONG).show()
        }
    }

    private fun constName(cls: Class<*>, v: Int): String = try {
        cls.fields.firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.getInt(null) == v }?.name ?: "?"
    } catch (_: Throwable) { "?" }

    private fun zoomErrName(v: Int) = constName(ZoomError::class.java, v)
    private fun meetingErrName(v: Int) = constName(MeetingError::class.java, v)
}
