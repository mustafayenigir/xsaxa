package com.oku.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Kodla kurulan ekranlar için küçük yardımcılar. */
object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density
    fun color(c: Context, id: Int): Int = ContextCompat.getColor(c, id)

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun rounded(c: Context, fill: Int, radiusDp: Int = 18, strokeColor: Int? = null, strokeDp: Int = 1) =
        GradientDrawable().apply {
            cornerRadius = dpf(c, radiusDp.toFloat())
            setColor(fill)
            if (strokeColor != null) setStroke(dp(c, strokeDp), strokeColor)
        }

    fun circle(fill: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }

    fun card(c: Context): GradientDrawable =
        rounded(c, color(c, R.color.surface), 20, color(c, R.color.outline))

    fun text(c: Context, s: CharSequence, sp: Float = 15f, color: Int = color(c, R.color.on_background), bold: Boolean = false) =
        TextView(c).apply {
            text = s
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun muted(c: Context, s: CharSequence, sp: Float = 13f) = text(c, s, sp, color(c, R.color.on_background_muted))

    fun section(c: Context, s: CharSequence) = text(c, s.toString().uppercase(Format.TR), 13f, color(c, R.color.accent_text), true).apply {
        letterSpacing = 0.06f
        setPadding(0, dp(c, 22), 0, dp(c, 8))
    }

    fun vbox(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    fun hbox(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun matchWrap(topDp: Int = 0, c: Context? = null) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { if (c != null) topMargin = dp(c, topDp) }

    fun weight1() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    /** Küçük yuvarlak etiket ("Bugün 15 dk" gibi). */
    fun chip(c: Context, s: String, fill: Int = withAlpha(color(c, R.color.primary), 30), fg: Int = color(c, R.color.accent_text)) =
        text(c, s, 12f, fg, true).apply {
            background = rounded(c, fill, 12)
            setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4))
        }

    fun gone(v: View, hide: Boolean) { v.visibility = if (hide) View.GONE else View.VISIBLE }
}
