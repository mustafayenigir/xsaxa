package com.oku.app

import android.app.Application

/** Zoom Meeting SDK eklenmemiş derleme: Zoom, telefondaki Zoom uygulamasıyla açılır. */
object ZoomSdkBridge {
    fun install(app: Application) {}
    fun inMeeting(): Boolean = false
}
