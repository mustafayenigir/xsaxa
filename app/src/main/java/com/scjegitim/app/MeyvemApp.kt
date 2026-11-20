package com.scjegitim.app

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build

class MeyvemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Kaydedilmiş gece/gündüz tercihini uygulama açılır açılmaz uygula
        AppSettings.applyNightMode(this)
        blockAudioCapture(this)
    }

    companion object {
        /**
         * Ekran kaydedici / MediaProjection gibi araçların uygulamanın SESİNİ yakalamasını engeller.
         * (Manifest'teki allowAudioPlaybackCapture="false" ile birlikte çalışır.)
         * Not: hoparlörden çıkan sesin mikrofonla kaydedilmesini engelleyemez.
         */
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
