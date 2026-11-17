package com.scjegitim.app

import android.app.Application

class MeyvemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Kaydedilmiş gece/gündüz tercihini uygulama açılır açılmaz uygula
        AppSettings.applyNightMode(this)
    }
}
