package com.oku.app

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build

class OkuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.applyNightMode(this)
        blockAudioCapture(this)
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
