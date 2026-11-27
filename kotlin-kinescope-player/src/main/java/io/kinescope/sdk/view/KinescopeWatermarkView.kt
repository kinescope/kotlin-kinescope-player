package io.kinescope.sdk.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Draws the server watermark (`sdk.watermark`) over the video picture, like the
 * web player: `random` jumps to a random place, `stripes` repeats in diagonal rows;
 * `display_timeout` makes it blink. Lives in the video content frame, so it covers
 * only the picture and follows pinch-to-zoom. Never takes touches.
 */
@SuppressLint("ViewConstructor")
internal class KinescopeWatermarkView(
    context: Context,
    private val random: Random = Random.Default,
) : View(context) {

    var watermark: KinescopeWatermarkStyle? = null
        set(value) {
            if (field == value) return
            field = value
            textShown = true
            visibility = if (value != null) VISIBLE else GONE
            moveRandomly()
            schedule()
            invalidate()
        }

    val text: String?
        get() = watermark?.text

    /** `true` while the text is on screen (false between blinks or with no watermark). */
    internal val isTextShown: Boolean
        get() = watermark != null && textShown

    /** Top-left of the random-mode text as a fraction of the free space. */
    internal var position: Pair<Float, Float> = 0.5f to 0.5f
        private set

    private var textShown = true

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(dp(1.5f), 0f, 0f, SHADOW_COLOR)
    }

    private val tick = Runnable { onTick() }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        schedule()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun schedule() {
        removeCallbacks(tick)
        val wm = watermark ?: return
        if (!isAttachedToWindow) return
        val delay = when {
            wm.blinks -> if (textShown) wm.visibleMs else wm.hiddenMs
            wm.mode == KinescopeWatermarkStyle.Mode.RANDOM -> MOVE_INTERVAL_MS
            else -> return
        }
        postDelayed(tick, delay)
    }

    private fun onTick() {
        val wm = watermark ?: return
        if (wm.blinks) {
            textShown = !textShown
            if (textShown) moveRandomly()
        } else {
            moveRandomly()
        }
        invalidate()
        schedule()
    }

    private fun moveRandomly() {
        position = random.nextFloat() to random.nextFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val wm = watermark ?: return
        if (!textShown || width <= 0 || height <= 0) return
        paint.color = wm.color
        updateTextSize(wm)
        when (wm.mode) {
            KinescopeWatermarkStyle.Mode.RANDOM -> drawRandom(canvas, wm.text)
            KinescopeWatermarkStyle.Mode.STRIPES -> drawStripes(canvas, wm.text)
        }
    }

    private fun updateTextSize(wm: KinescopeWatermarkStyle) {
        paint.textSize = REFERENCE_TEXT_SIZE
        val referenceWidth = paint.measureText(wm.text).coerceAtLeast(1f)
        val target = width * wm.scale / referenceWidth * REFERENCE_TEXT_SIZE
        val min = sp(MIN_TEXT_SP)
        val max = (height * MAX_TEXT_HEIGHT_FRACTION).coerceAtLeast(min)
        paint.textSize = target.coerceIn(min, max)
    }

    private fun drawRandom(canvas: Canvas, text: String) {
        val textWidth = paint.measureText(text)
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val margin = dp(EDGE_MARGIN_DP)
        val freeX = (width - textWidth - 2 * margin).coerceAtLeast(0f)
        val freeY = (height - textHeight - 2 * margin).coerceAtLeast(0f)
        val (fx, fy) = position
        canvas.drawText(text, margin + freeX * fx, margin + freeY * fy - metrics.ascent, paint)
    }

    private fun drawStripes(canvas: Canvas, text: String) {
        val textWidth = paint.measureText(text)
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        if (textWidth <= 0f || textHeight <= 0f) return
        val stepX = textWidth + textHeight * 2f
        val stepY = textHeight * STRIPE_ROW_SPACING
        val cx = width / 2f
        val cy = height / 2f
        val radius = hypot(width.toFloat(), height.toFloat()) / 2f
        // Bounded: a degenerate font (zero metrics) must not turn into millions of draw calls.
        val rows = ceil(radius / stepY).toInt().coerceAtMost(MAX_STRIPE_STEPS)
        val cols = (ceil(radius / stepX).toInt() + 1).coerceAtMost(MAX_STRIPE_STEPS)
        canvas.save()
        canvas.rotate(STRIPE_ANGLE, cx, cy)
        for (row in -rows..rows) {
            val shift = if (row % 2 == 0) 0f else stepX / 2f
            val y = cy + row * stepY
            for (col in -cols..cols) {
                canvas.drawText(text, cx + col * stepX + shift - textWidth / 2f, y, paint)
            }
        }
        canvas.restore()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    internal companion object {
        /** Random mode without blinking: how often the text jumps. */
        const val MOVE_INTERVAL_MS = 6_000L
        private const val SHADOW_COLOR = 0x59000000
        private const val REFERENCE_TEXT_SIZE = 100f
        private const val MIN_TEXT_SP = 10f
        private const val MAX_TEXT_HEIGHT_FRACTION = 0.15f
        private const val EDGE_MARGIN_DP = 8f
        private const val STRIPE_ANGLE = -30f
        private const val STRIPE_ROW_SPACING = 4f
        private const val MAX_STRIPE_STEPS = 50
    }
}
