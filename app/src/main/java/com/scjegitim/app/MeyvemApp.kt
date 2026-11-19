package com.scjegitim.app

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build

class MeyvemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Kaydedilmiş gece/gündüz tercihini uygulama açılır açılmaz uygula
        AppSettings.applyNightMode(this)
        blockAudioCapture()
    }

    /**
     * Ekran kaydedici / MediaProjection gibi araçların uygulamanın SESİNİ yakalamasını engeller.
     * (Manifest'teki allowAudioPlaybackCapture="false" ile birlikte çalışır.)
     * Not: telefonun hoparlöründen çıkan sesin mikrofonla kaydedilmesini engelleyemez.
     */
    private fun blockAudioCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            } catch (_: Throwable) {
            }
        }
    }
}
