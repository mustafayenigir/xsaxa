package com.scjegitim.app

import android.content.Context
import android.util.TypedValue

/**
 * Kişilik (Enneagram'dan esinlenen) renk temaları. Eğlence amaçlıdır; kişilik testi değildir.
 * Renkler res/values/palettes.xml içindeki Palette0..9 stillerinde.
 */
object Palettes {
    class Palette(
        val styleRes: Int,
        val name: String,
        val type: String,
        val desc: String,
        val start: Int,
        val end: Int
    )

    val all: List<Palette> = listOf(
        Palette(R.style.Palette0, "Meyvem", "Klasik", "Neşeli, sıcak ve enerjik. Varsayılan tema.", 0xFFFF9A3DL.toInt(), 0xFFFF3D87L.toInt()),
        Palette(R.style.Palette1, "Kusursuz", "Tip 1 · İyileştirici", "Düzenli, ilkeli, kaliteye önem veren biri gibi: berrak okyanus mavisi.", 0xFF3FA9F5L.toInt(), 0xFF1B4F9CL.toInt()),
        Palette(R.style.Palette2, "Candan", "Tip 2 · Yardımsever", "Sıcak, cömert, insanlara değer veren: gül pembesi.", 0xFFFF8FB1L.toInt(), 0xFFE8457CL.toInt()),
        Palette(R.style.Palette3, "Parlak", "Tip 3 · Başarı Odaklı", "Hedef odaklı, enerjik, ilham veren: altın ve turuncu.", 0xFFF0A000L.toInt(), 0xFFD9480FL.toInt()),
        Palette(R.style.Palette4, "Özgün", "Tip 4 · Bireyci", "Derin, yaratıcı, kendine has: lavanta ve mor.", 0xFFB06AF0L.toInt(), 0xFF5B2DB8L.toInt()),
        Palette(R.style.Palette5, "Bilge", "Tip 5 · Araştırmacı", "Meraklı, analitik, derinlemesine düşünen: zümrüt ve petrol.", 0xFF1FA37AL.toInt(), 0xFF0B5D6BL.toInt()),
        Palette(R.style.Palette6, "Güvenilir", "Tip 6 · Sadık", "Sadık, sorumluluk sahibi, güven veren: lacivert.", 0xFF4E6BDBL.toInt(), 0xFF1E2A78L.toInt()),
        Palette(R.style.Palette7, "Neşeli", "Tip 7 · Maceracı", "Hevesli, hareketli, hep yeni fikirli: yeşil ve turkuaz.", 0xFF3DAE3AL.toInt(), 0xFF0E8F85L.toInt()),
        Palette(R.style.Palette8, "Güçlü", "Tip 8 · Lider", "Kararlı, cesur, koruyucu: kırmızı ve bordo.", 0xFFFF4B3EL.toInt(), 0xFF8E0F2BL.toInt()),
        Palette(R.style.Palette9, "Huzurlu", "Tip 9 · Barışçı", "Sakin, uyumlu, denge arayan: adaçayı ve gök mavisi.", 0xFF4FB99AL.toInt(), 0xFF3A86C8L.toInt()),
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
