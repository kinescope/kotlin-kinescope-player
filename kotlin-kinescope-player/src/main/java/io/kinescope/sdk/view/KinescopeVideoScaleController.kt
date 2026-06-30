package io.kinescope.sdk.view

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.hypot
import kotlin.math.roundToInt
import java.util.Locale

internal class KinescopeVideoScaleController(
    private val transformTarget: View,
    private val scaleBadge: TextView,
) {
    var scale: Float = 1f
        private set

    var onScaleChanged: ((Float) -> Unit)? = null

    private var translateX = 0f
    private var translateY = 0f
    private var resetAnimator: ValueAnimator? = null
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isPanning = false
    private var panConsumedMovement = false
    private var badgeSuppressed = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var blockScaleUntilAllPointersUp = false

    private val scaleGestureDetector = ScaleGestureDetector(
        transformTarget.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (blockScaleUntilAllPointersUp) {
                    return false
                }
                cancelResetAnimation()
                isPanning = false
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (blockScaleUntilAllPointersUp) {
                    return true
                }
                applyScaleFactor(detector.scaleFactor)
                return true
            }
        },
    ).apply {
        isQuickScaleEnabled = false
        isStylusScaleEnabled = false
    }

    fun isEnabled(): Boolean = transformTarget.isEnabled

    fun setEnabled(enabled: Boolean) {
        transformTarget.isEnabled = enabled
    }

    fun setBadgeSuppressed(suppressed: Boolean) {
        if (badgeSuppressed == suppressed) {
            return
        }
        badgeSuppressed = suppressed
        updateBadge()
    }

    fun shouldConsumeGestures(): Boolean =
        scaleGestureDetector.isInProgress || (isPanning && panConsumedMovement)

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!transformTarget.isEnabled) {
            return false
        }

        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelResetAnimation()
                blockScaleUntilAllPointersUp = false
                activePointerId = event.getPointerId(0)
                if (scale > SCALE_BADGE_THRESHOLD && event.pointerCount == 1) {
                    beginPan(event, pointerIndex = 0)
                } else {
                    isPanning = false
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isPanning = false
                blockScaleUntilAllPointersUp = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                isPanning = false
                if (event.pointerCount > 1) {
                    blockScaleUntilAllPointersUp = true
                }
                if (event.pointerCount == 2 && scale > SCALE_BADGE_THRESHOLD) {
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    beginPan(event, pointerIndex = remainingIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (
                    isPanning &&
                    event.pointerCount == 1 &&
                    !scaleGestureDetector.isInProgress &&
                    !blockScaleUntilAllPointersUp
                ) {
                    val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    val dx = x - lastPanX
                    val dy = y - lastPanY
                    if (hypot(dx.toDouble(), dy.toDouble()) > 0.5) {
                        panConsumedMovement = true
                    }
                    translateX += dx
                    translateY += dy
                    lastPanX = x
                    lastPanY = y
                    clampTranslation()
                    applyTransformImmediate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                blockScaleUntilAllPointersUp = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }

        return scaleGestureDetector.isInProgress || (isPanning && panConsumedMovement)
    }

    fun reset(animated: Boolean = true) {
        cancelResetAnimation()
        if (!animated) {
            scale = 1f
            translateX = 0f
            translateY = 0f
            applyTransformImmediate()
            return
        }

        val startScale = scale
        val startTranslateX = translateX
        val startTranslateY = translateY
        if (startScale <= SCALE_BADGE_THRESHOLD &&
            hypot(startTranslateX.toDouble(), startTranslateY.toDouble()) < 0.5
        ) {
            scale = 1f
            translateX = 0f
            translateY = 0f
            applyTransformImmediate()
            return
        }

        resetAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RESET_ANIMATION_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                scale = startScale + (1f - startScale) * progress
                translateX = startTranslateX * (1f - progress)
                translateY = startTranslateY * (1f - progress)
                applyTransformImmediate()
            }
            start()
        }
    }

    fun multiplyScale(factor: Float) {
        cancelResetAnimation()
        applyScaleFactor(factor)
    }

    fun percentLabel(): String = "${(scale * 100f).roundToInt()}%"

    private fun beginPan(event: MotionEvent, pointerIndex: Int) {
        isPanning = true
        panConsumedMovement = false
        activePointerId = event.getPointerId(pointerIndex)
        lastPanX = event.getX(pointerIndex)
        lastPanY = event.getY(pointerIndex)
    }

    private fun applyScaleFactor(scaleFactor: Float) {
        val previousScale = scale
        scale = (scale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (scale == previousScale) {
            return
        }
        if (scale <= SCALE_BADGE_THRESHOLD) {
            translateX = 0f
            translateY = 0f
        }
        clampTranslation()
        applyTransformImmediate()
    }

    private fun clampTranslation() {
        if (scale <= MIN_SCALE) {
            translateX = 0f
            translateY = 0f
            return
        }
        val maxX = (transformTarget.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (transformTarget.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        translateX = translateX.coerceIn(-maxX, maxX)
        translateY = translateY.coerceIn(-maxY, maxY)
    }

    private fun applyTransformImmediate() {
        val width = transformTarget.width
        val height = transformTarget.height
        if (width > 0 && height > 0) {
            transformTarget.pivotX = width / 2f
            transformTarget.pivotY = height / 2f
        }
        transformTarget.scaleX = scale
        transformTarget.scaleY = scale
        transformTarget.translationX = translateX
        transformTarget.translationY = translateY
        updateBadge()
        onScaleChanged?.invoke(scale)
    }

    private fun updateBadge() {
        if (scale <= SCALE_BADGE_THRESHOLD || badgeSuppressed) {
            scaleBadge.isVisible = false
            return
        }
        scaleBadge.isVisible = true
        scaleBadge.text = formatScaleMultiplier(scale)
    }

    private fun cancelResetAnimation() {
        resetAnimator?.cancel()
        resetAnimator = null
    }

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 5f
        private const val SCALE_BADGE_THRESHOLD = 1.005f
        private const val RESET_ANIMATION_DURATION_MS = 250L

        fun formatScaleMultiplier(value: Float): String {
            val rounded = (value * 10f).roundToInt() / 10f
            return if (kotlin.math.abs(rounded - rounded.toLong()) < 0.05f) {
                "${rounded.toLong()}x"
            } else {
                String.format(Locale.US, "%.1fx", rounded)
            }
        }
    }
}
