package com.oku.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

class OkuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.applyNightMode(this)
        blockAudioCapture(this)
        // Ekran görüntüsü engeli uygulamanın TÜM pencerelerinde (Zoom toplantı ekranı dahil)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {
                a.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) { blockAudioCapture(a) }
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        ZoomSdkBridge.install(this)
    }

    companion object {
        /** Ekran kaydedicilerin uygulamanın SESİNİ yakalamasını engeller (allowAudioPlaybackCapture ile birlikte). */
        fun blockAudioCapture(c: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
