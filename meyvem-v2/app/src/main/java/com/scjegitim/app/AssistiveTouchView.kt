package com.scjegitim.app

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

/**
 * iPhone'daki "AssistiveTouch" benzeri yüzen düğme:
 *  - Yarı saydam durur, ekranda sürüklenebilir, bırakınca en yakın kenara yapışır.
 *  - Tek dokunuş: onSingleTap, çift dokunuş: onDoubleTap.
 */
class AssistiveTouchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var onSingleTap: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onPositionChanged: ((side: Int, yFraction: Float) -> Unit)? = null

    /** 0 = sol kenar, 1 = sağ kenar */
    var side: Int = 1
        private set

    /** Dikey konum: 0 = en üst, 1 = en alt */
    var yFraction: Float = 0.7f
        private set

    private val density = resources.displayMetrics.density
    private val margin = 6f * density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false

    private val fadeRunnable = Runnable {
        animate().alpha(IDLE_ALPHA).setDuration(300).start()
    }

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap?.invoke()
            return true
        }
    })

    init {
        alpha = IDLE_ALPHA
    }

    /** Kaydedilmiş konumu al (görünüm yerleştirilince applyPosition ile uygulanır). */
    fun restore(side: Int, yFraction: Float) {
        this.side = if (side == 0) 0 else 1
        this.yFraction = yFraction.coerceIn(0f, 1f)
    }

    /** Kayıtlı konumu ekran boyutuna göre yerleştirir. */
    fun applyPosition() {
        val p = parent as? View ?: return
        if (p.width == 0 || width == 0) return
        val maxY = (p.height - height).coerceAtLeast(0)
        y = yFraction * maxY
        x = targetX(p)
    }

    private fun targetX(p: View): Float =
        if (side == 0) margin else (p.width - width - margin)

    private fun fadeToIdle() {
        removeCallbacks(fadeRunnable)
        postDelayed(fadeRunnable, 1500)
    }

    private fun snapToEdge(p: View) {
        side = if (x + width / 2f < p.width / 2f) 0 else 1
        val maxY = (p.height - height).coerceAtLeast(1)
        yFraction = (y / maxY).coerceIn(0f, 1f)
        animate().x(targetX(p)).setDuration(180).start()
        onPositionChanged?.invoke(side, yFraction)
        fadeToIdle()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val p = parent as? View ?: return super.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(fadeRunnable)
                animate().cancel()
                alpha = ACTIVE_ALPHA
                downRawX = ev.rawX
                downRawY = ev.rawY
                startX = x
                startY = y
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                    dragging = true
                    onDragStart?.invoke()
                    // Dokunma algılayıcıya "iptal" gönder: sürükleme dokunuş sayılmasın
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    detector.onTouchEvent(cancel)
                    cancel.recycle()
                }
                if (dragging) {
                    x = (startX + dx).coerceIn(0f, (p.width - width).toFloat())
                    y = (startY + dy).coerceIn(0f, (p.height - height).toFloat())
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    snapToEdge(p)
                    return true
                }
                fadeToIdle()
            }
        }

        detector.onTouchEvent(ev)
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        private const val IDLE_ALPHA = 0.55f
        private const val ACTIVE_ALPHA = 0.95f
    }
}
