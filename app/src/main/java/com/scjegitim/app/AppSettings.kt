package com.scjegitim.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Kullanıcının Ayarlar ekranından değiştirdiği tercihler. */
object AppSettings {
    private const val PREFS = "meyvem_settings"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val ZOOM_MIN = 50
    const val ZOOM_MAX = 200
    const val ZOOM_STEP = 10
    const val ZOOM_DEFAULT = 100

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Ekran kapanınca / uygulama arka plana alınınca sesi sürdür
    fun keepAudio(c: Context): Boolean = prefs(c).getBoolean("keep_audio", true)
    fun setKeepAudio(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("keep_audio", v).apply()
    }

    // Tema: sistem / gündüz / gece
    fun themeMode(c: Context): Int = prefs(c).getInt("theme_mode", THEME_SYSTEM)
    fun setThemeMode(c: Context, v: Int) {
        prefs(c).edit().putInt("theme_mode", v).apply()
    }

    // Site yazı boyutu (yüzde)
    fun textZoom(c: Context): Int = prefs(c).getInt("text_zoom", ZOOM_DEFAULT)
    fun setTextZoom(c: Context, v: Int) {
        prefs(c).edit().putInt("text_zoom", v.coerceIn(ZOOM_MIN, ZOOM_MAX)).apply()
    }

    fun applyNightMode(c: Context) {
        val mode = when (themeMode(c)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
