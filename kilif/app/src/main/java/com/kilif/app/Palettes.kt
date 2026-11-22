package com.kilif.app

import android.content.Context
import android.util.TypedValue

/** Üç renk teması. Renkler res/values(-night)/palettes.xml içindeki Palette0..2 stillerinde. */
object Palettes {
    class Palette(val styleRes: Int, val nameRes: Int, val start: Int, val end: Int)

    val all: List<Palette> = listOf(
        Palette(R.style.Palette0, R.string.palette_sunset, 0xFFFF9A3DL.toInt(), 0xFFFF3D87L.toInt()),
        Palette(R.style.Palette1, R.string.palette_ocean, 0xFF3FA9F5L.toInt(), 0xFF1B4F9CL.toInt()),
        Palette(R.style.Palette2, R.string.palette_lavender, 0xFFB06AF0L.toInt(), 0xFF5B2DB8L.toInt()),
    )

    fun index(c: Context): Int = AppSettings.palette(c).coerceIn(0, all.size - 1)

    /** Seçili temanın renklerini bu ekranın temasına uygular (setContentView'dan önce çağrılır). */
    fun apply(c: Context) {
        c.theme.applyStyle(all[index(c)].styleRes, true)
    }

    /** Temadaki bir renk özelliğinin (R.attr.cXxx) o anki değeri. */
    fun color(c: Context, attr: Int): Int {
        val tv = TypedValue()
        c.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
