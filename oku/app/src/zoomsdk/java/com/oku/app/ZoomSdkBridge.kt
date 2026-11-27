package com.oku.app

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
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
 * Herhangi bir adım olmazsa telefondaki Zoom uygulamasına geçilir.
 */
object ZoomSdkBridge {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = false
    @Volatile private var endedAt = 0L
    private var listening = false
    private var initializing = false

    /** Toplantıdayken (ya da yeni çıkıldıysa) kendi mikrofonumuz "kayıt" sayılmasın. */
    fun inMeeting(): Boolean = active || SystemClock.elapsedRealtime() - endedAt < 4000

    fun install(app: Application) {
        ZoomJoin.inApp = { act, link, m -> start(act, link, m); true }
    }

    private fun start(act: Activity, link: String, m: ZoomJoin.Meeting) {
        val sdk = ZoomSDK.getInstance()
        if (sdk.isInitialized) {
            join(act, link, m)
            return
        }
        if (initializing) return
        initializing = true
        Toast.makeText(act, R.string.zoom_preparing, Toast.LENGTH_SHORT).show()
        val app = act.applicationContext
        Thread {
            val id = Account.identity(app)
            val r = if (id != null) Api.zoomSig(id, m.number) else null
            val sig = r?.optString("signature").orEmpty()
            main.post {
                if (r == null || !r.optBoolean("ok") || sig.isBlank()) {
                    initializing = false
                    fallback(act, link, m)
                    return@post
                }
                val params = ZoomSDKInitParams().apply {
                    jwtToken = sig
                    domain = "zoom.us"
                    enableLog = false
                }
                try {
                    sdk.initialize(app, object : ZoomSDKInitializeListener {
                        override fun onZoomSDKInitializeResult(errorCode: Int, internalErrorCode: Int) {
                            initializing = false
                            if (errorCode == ZoomError.ZOOM_ERROR_SUCCESS) join(act, link, m) else fallback(act, link, m)
                        }

                        override fun onZoomAuthIdentityExpired() {}
                    }, params)
                } catch (_: Throwable) {
                    initializing = false
                    fallback(act, link, m)
                }
            }
        }.start()
    }

    private fun join(act: Activity, link: String, m: ZoomJoin.Meeting) {
        if (act.isFinishing || act.isDestroyed) return
        val svc = ZoomSDK.getInstance().meetingService ?: return fallback(act, link, m)
        if (!listening) {
            listening = true
            svc.addListener(object : MeetingServiceListener {
                override fun onMeetingStatusChanged(status: MeetingStatus?, errorCode: Int, internalErrorCode: Int) {
                    val on = status != null && status != MeetingStatus.MEETING_STATUS_IDLE &&
                        status != MeetingStatus.MEETING_STATUS_ENDED && status != MeetingStatus.MEETING_STATUS_FAILED
                    if (active && !on) endedAt = SystemClock.elapsedRealtime()
                    active = on
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
        val rc = try { svc.joinMeetingWithParams(act, p, opts) } catch (_: Throwable) { -1 }
        if (rc != MeetingError.MEETING_ERROR_SUCCESS) {
            active = false
            fallback(act, link, m)
        }
    }

    private fun fallback(act: Activity, link: String, m: ZoomJoin.Meeting) {
        if (act.isFinishing || act.isDestroyed) return
        Toast.makeText(act, R.string.zoom_inapp_failed, Toast.LENGTH_SHORT).show()
        ZoomJoin.openZoomApp(act, link, m)
    }
}
