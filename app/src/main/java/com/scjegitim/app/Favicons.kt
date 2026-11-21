package com.scjegitim.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sitelerin küçük logolarını (favicon) indirir ve cihazda saklar.
 * Ağ/disk işi yapar: ana iş parçacığında ÇAĞRILMAMALIDIR.
 */
object Favicons {
    private val mem = HashMap<String, Bitmap>()

    fun load(c: Context, siteUrl: String): Bitmap? {
        val host = Uri.parse(siteUrl).host?.lowercase() ?: return null
        synchronized(mem) { mem[host]?.let { return it } }

        val dir = File(c.cacheDir, "favicons").apply { mkdirs() }
        val file = File(dir, host.replace(Regex("[^a-z0-9.-]"), "_") + ".png")
        if (file.exists() && file.length() > 0) {
            BitmapFactory.decodeFile(file.path)?.let { bmp ->
                synchronized(mem) { mem[host] = bmp }
                return bmp
            }
        }

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("https://www.google.com/s2/favicons?domain=" + Uri.encode(host) + "&sz=128")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
            }
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            file.writeBytes(bytes)
            synchronized(mem) { mem[host] = bmp }
            bmp
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }
}
