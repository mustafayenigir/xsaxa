package com.kilif.app

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler

/**
 * Sistemde başka bir uygulamanın ses kaydı (ekran kaydedicinin mikrofon / sistem sesi kaydı dahil)
 * yapıp yapmadığını izler. Hangi uygulama olduğu söylenmez; yalnızca "kayıt var / yok" bilinir.
 */
object RecordingWatch {
    // 1997+ : sistemin "Hey Google" gibi sürekli dinleme kayıtları; gerçek kayıt sayılmaz
    private const val FIRST_SYSTEM_ONLY_SOURCE = 1997

    /** Etkin ses kayıtlarının kaynak kodları (MediaRecorder.AudioSource değerleri). */
    fun sources(configs: List<AudioRecordingConfiguration>?): List<Int> =
        configs.orEmpty().map { it.clientAudioSource }.filter { it < FIRST_SYSTEM_ONLY_SOURCE }

    fun current(c: Context): List<Int> {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try { sources(am.activeRecordingConfigurations) } catch (_: Throwable) { emptyList() }
    }

    fun register(c: Context, handler: Handler, onChange: (List<Int>) -> Unit): AudioManager.AudioRecordingCallback? {
        return try {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cb = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                    onChange(sources(configs))
                }
            }
            am.registerAudioRecordingCallback(cb, handler)
            cb
        } catch (_: Throwable) {
            null
        }
    }

    fun unregister(c: Context, cb: AudioManager.AudioRecordingCallback?) {
        if (cb == null) return
        try {
            (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioRecordingCallback(cb)
        } catch (_: Throwable) {
        }
    }
}
