package com.kitap.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Ekran kapandığında / uygulama arka plana alındığında WebView'in "görünmez"
 * olduğunu fark edip sesi ve sayfayı durdurmasını engeller.
 * Sayfa kendini hâlâ görünür sanır, bu yüzden ses/video çalmaya devam eder.
 */
class BackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {

    var keepBackground: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepBackground && visibility != View.VISIBLE) {
            // Gizlenme bilgisini sayfaya iletme: sayfa "görünür" kalsın
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}
