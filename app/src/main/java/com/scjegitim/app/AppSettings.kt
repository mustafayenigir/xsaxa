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

    // Yüzen düğmenin konumu: 0=sol 1=sağ kenar, dikey oran 0..1
    fun fabSide(c: Context): Int = prefs(c).getInt("fab_side", 1)
    fun fabY(c: Context): Float = prefs(c).getFloat("fab_y", 0.7f)
    fun setFabPosition(c: Context, side: Int, yFraction: Float) {
        prefs(c).edit().putInt("fab_side", side).putFloat("fab_y", yFraction).apply()
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
