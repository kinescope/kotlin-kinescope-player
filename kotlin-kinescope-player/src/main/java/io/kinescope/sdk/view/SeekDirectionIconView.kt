package io.kinescope.sdk.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser

class SeekDirectionIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var forward: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val forwardChevronPaths: List<Path> = FORWARD_CHEVRON_DATA.map(PathParser::createPathFromPathData)
    private val backChevronPaths: List<Path> = BACK_CHEVRON_DATA.map(PathParser::createPathFromPathData)
    private val chevronAlphas = FloatArray(CHEVRON_COUNT) { MIN_ALPHA }
    private var rippleAnimator: ValueAnimator? = null
    private var isRippleRequested = false

    fun startRippleAnimation() {
        isRippleRequested = true
        if (rippleAnimator?.isRunning == true) {
            return
        }
        rippleAnimator?.cancel()
        rippleAnimator = ValueAnimator.ofFloat(0f, CHEVRON_COUNT.toFloat()).apply {
            duration = RIPPLE_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                if (!isRippleRequested) {
                    return@addUpdateListener
                }
                val phase = (animator.animatedValue as Float) % CHEVRON_COUNT
                for (index in 0 until CHEVRON_COUNT) {
                    val distance = kotlin.math.abs(phase - index - 0.5f).coerceAtMost(1.5f) / 1.5f
                    chevronAlphas[index] = MIN_ALPHA + (1f - distance) * (1f - MIN_ALPHA)
                }
                invalidate()
            }
            start()
        }
    }

    fun stopRippleAnimation() {
        isRippleRequested = false
        rippleAnimator?.cancel()
        rippleAnimator = null
        for (index in chevronAlphas.indices) {
            chevronAlphas[index] = 1f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) {
            return
        }

        val scale = minOf(width / VIEWPORT_WIDTH, height / VIEWPORT_HEIGHT)
        val offsetX = (width - VIEWPORT_WIDTH * scale) / 2f
        val offsetY = (height - VIEWPORT_HEIGHT * scale) / 2f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        val chevronPaths = if (forward) forwardChevronPaths else backChevronPaths
        chevronPaths.forEachIndexed { index, path ->
            paint.alpha = (chevronAlphas[index].coerceIn(0f, 1f) * 255).toInt()
            canvas.drawPath(path, paint)
        }

        canvas.restore()
        paint.alpha = 255
    }

    override fun onDetachedFromWindow() {
        stopRippleAnimation()
        super.onDetachedFromWindow()
    }

    private companion object {
        private const val CHEVRON_COUNT = 3
        private const val VIEWPORT_WIDTH = 31f
        private const val VIEWPORT_HEIGHT = 12f
        private const val MIN_ALPHA = 0.25f
        private const val RIPPLE_CYCLE_MS = 1100L

        private val FORWARD_CHEVRON_DATA = listOf(
            "M0.5,10.132C0.5,10.93 1.39,11.407 2.055,10.964L8.252,6.832C8.846,6.436 8.846,5.564 8.252,5.168L2.055,1.036C1.39,0.593 0.5,1.07 0.5,1.869V10.132Z",
            "M19.252,5.168C19.846,5.564 19.846,6.436 19.252,6.832L13.055,10.964C12.39,11.407 11.5,10.93 11.5,10.132L11.5,1.869C11.5,1.07 12.39,0.593 13.055,1.036L19.252,5.168Z",
            "M30.252,5.168C30.846,5.564 30.846,6.436 30.252,6.832L24.055,10.964C23.39,11.407 22.5,10.93 22.5,10.132V1.869C22.5,1.07 23.39,0.593 24.055,1.036L30.252,5.168Z",
        )

        private val BACK_CHEVRON_DATA = listOf(
            "M30.5,10.132C30.5,10.93 29.61,11.407 28.945,10.964L22.748,6.832C22.154,6.436 22.154,5.564 22.748,5.168L28.945,1.036C29.61,0.593 30.5,1.07 30.5,1.869V10.132Z",
            "M11.748,5.168C11.154,5.564 11.154,6.436 11.748,6.832L17.945,10.964C18.61,11.407 19.5,10.93 19.5,10.132V1.869C19.5,1.07 18.61,0.593 17.945,1.036L11.748,5.168Z",
            "M0.748,5.168C0.154,5.564 0.154,6.436 0.748,6.832L6.945,10.964C7.61,11.407 8.5,10.93 8.5,10.132V1.869C8.5,1.07 7.61,0.593 6.945,1.036L0.748,5.168Z",
        )
    }
}
