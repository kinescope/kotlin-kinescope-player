package io.kinescope.sdk.view

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.graphics.PathParser
import io.kinescope.sdk.R

class KinescopePlayPauseMorphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class State {
        PLAY,
        PAUSE,
        REPLAY,
    }

    private val morphIconView = MorphIconView(context)
    private val replayView = ImageView(context).apply {
        setImageResource(R.drawable.ic_controls_rewind)
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
        visibility = INVISIBLE
    }

    private var currentState = State.PLAY
    private var morphAnimator: ValueAnimator? = null
    private var replayAnimator: ValueAnimator? = null

    init {
        addView(morphIconView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(replayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setIconColor(@ColorInt color: Int) {
        morphIconView.iconColor = color
    }

    fun setState(state: State, animated: Boolean) {
        morphAnimator?.cancel()
        replayAnimator?.cancel()

        if (!animated || !isAttachedToWindow) {
            applyStateInstantly(state)
            currentState = state
            return
        }

        if (state == currentState && !morphAnimatorRunning()) {
            return
        }

        when (state) {
            State.REPLAY -> animateToReplay()
            State.PLAY, State.PAUSE -> {
                if (currentState == State.REPLAY) {
                    animateFromReplay(state)
                } else {
                    animateMorph(state)
                }
            }
        }
        currentState = state
    }

    private fun morphAnimatorRunning(): Boolean {
        return morphAnimator?.isRunning == true
    }

    private fun applyStateInstantly(state: State) {
        when (state) {
            State.PLAY -> {
                morphIconView.morphProgress = 0f
                morphIconView.alpha = 1f
                replayView.alpha = 0f
                replayView.visibility = INVISIBLE
            }

            State.PAUSE -> {
                morphIconView.morphProgress = 1f
                morphIconView.alpha = 1f
                replayView.alpha = 0f
                replayView.visibility = INVISIBLE
            }

            State.REPLAY -> {
                morphIconView.alpha = 0f
                replayView.alpha = 1f
                replayView.visibility = VISIBLE
            }
        }
        morphIconView.invalidate()
    }

    private fun animateMorph(state: State) {
        val end = if (state == State.PAUSE) 1f else 0f
        val start = morphIconView.morphProgress
        morphIconView.alpha = 1f
        replayView.visibility = INVISIBLE

        morphIconView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        morphAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = MORPH_DURATION_MS
            interpolator = MORPH_INTERPOLATOR
            addUpdateListener {
                morphIconView.morphProgress = it.animatedValue as Float
                morphIconView.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    morphIconView.setLayerType(View.LAYER_TYPE_NONE, null)
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    morphIconView.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    private fun animateToReplay() {
        replayView.visibility = VISIBLE
        morphAnimator = ValueAnimator.ofFloat(morphIconView.alpha, 0f).apply {
            duration = MORPH_DURATION_MS / 2
            interpolator = MORPH_INTERPOLATOR
            addUpdateListener { morphIconView.alpha = it.animatedValue as Float }
            start()
        }
        replayAnimator = ValueAnimator.ofFloat(replayView.alpha, 1f).apply {
            duration = MORPH_DURATION_MS / 2
            interpolator = MORPH_INTERPOLATOR
            addUpdateListener { replayView.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun animateFromReplay(state: State) {
        morphIconView.morphProgress = if (state == State.PAUSE) 1f else 0f
        replayAnimator = ValueAnimator.ofFloat(replayView.alpha, 0f).apply {
            duration = MORPH_DURATION_MS / 2
            interpolator = MORPH_INTERPOLATOR
            addUpdateListener {
                replayView.alpha = it.animatedValue as Float
                if (it.animatedFraction >= 1f) {
                    replayView.visibility = INVISIBLE
                }
            }
            start()
        }
        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MORPH_DURATION_MS / 2
            interpolator = MORPH_INTERPOLATOR
            addUpdateListener { morphIconView.alpha = it.animatedValue as Float }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        morphAnimator?.cancel()
        replayAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private class MorphIconView(context: Context) : View(context) {

        var morphProgress = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
            }

        @ColorInt
        var iconColor: Int = Color.WHITE
            set(value) {
                field = value
                paint.color = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val playPath: Path = PathParser.createPathFromPathData(
            "M21.462,12.722C23.6,13.983 24.669,14.613 25.013,15.42C25.312,16.122 25.296,16.92 24.968,17.611C24.591,18.402 23.497,18.988 21.309,20.159L12.947,24.637C10.876,25.746 9.841,26.3 8.995,26.192C8.257,26.098 7.592,25.7 7.161,25.094C6.667,24.399 6.667,23.225 6.667,20.875V11.468C6.667,9.021 6.667,7.797 7.18,7.094C7.628,6.481 8.316,6.088 9.071,6.014C9.938,5.928 10.992,6.55 13.1,7.793L21.462,12.722Z",
        )

        private val pausePath: Path = PathParser.createPathFromPathData(
            "M13.333,8.667C13.333,7.194 12.139,6 10.667,6C9.194,6 8,7.194 8,8.667V23.333C8,24.806 9.194,26 10.667,26C12.139,26 13.333,24.806 13.333,23.333L13.333,8.667ZM24,8.667C24,7.194 22.806,6 21.333,6C19.861,6 18.667,7.194 18.667,8.667L18.667,23.333C18.667,24.806 19.861,26 21.333,26C22.806,26 24,24.806 24,23.333V8.667Z",
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width == 0 || height == 0) {
                return
            }

            val scale = minOf(width, height) / VIEWPORT_SIZE
            val offsetX = (width - VIEWPORT_SIZE * scale) / 2f
            val offsetY = (height - VIEWPORT_SIZE * scale) / 2f

            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)

            drawMorphFrame(canvas, morphProgress)

            canvas.restore()
        }

        private fun drawMorphFrame(canvas: Canvas, progress: Float) {
            val t = progress.coerceIn(0f, 1f)

            if (t <= 0f) {
                canvas.drawPath(playPath, paint)
                return
            }
            if (t >= 1f) {
                canvas.drawPath(pausePath, paint)
                return
            }

            val playAlpha = 1f - smoothStep(t, PLAY_FADE_START, PLAY_FADE_END)
            val pauseAlpha = smoothStep(t, PAUSE_FADE_START, PAUSE_FADE_END)

            if (playAlpha > 0.004f) {
                val playScaleX = 1f - t * PLAY_SQUEEZE_X
                val playScaleY = 1f - t * PLAY_SQUEEZE_Y
                paint.alpha = (playAlpha * 255).toInt().coerceIn(1, 255)
                canvas.save()
                canvas.scale(playScaleX, playScaleY, PLAY_PIVOT_X, PLAY_PIVOT_Y)
                canvas.drawPath(playPath, paint)
                canvas.restore()
            }

            if (pauseAlpha > 0.004f) {
                val pauseScaleX = PAUSE_MIN_SCALE + pauseAlpha * (1f - PAUSE_MIN_SCALE)
                val pauseScaleY = PAUSE_MIN_SCALE + pauseAlpha * (1f - PAUSE_MIN_SCALE)
                paint.alpha = (pauseAlpha * 255).toInt().coerceIn(1, 255)
                canvas.save()
                canvas.scale(pauseScaleX, pauseScaleY, CENTER_X, CENTER_Y)
                canvas.drawPath(pausePath, paint)
                canvas.restore()
            }

            paint.alpha = 255
        }

        private fun smoothStep(value: Float, edge0: Float, edge1: Float): Float {
            val range = edge1 - edge0
            if (range <= 0f) {
                return if (value >= edge1) 1f else 0f
            }
            val x = ((value - edge0) / range).coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }
    }

    companion object {
        private const val VIEWPORT_SIZE = 32f
        private const val PLAY_PIVOT_X = 6.667f
        private const val PLAY_PIVOT_Y = 16f
        private const val CENTER_X = 16f
        private const val CENTER_Y = 16f
        private const val PLAY_FADE_START = 0f
        private const val PLAY_FADE_END = 0.72f
        private const val PAUSE_FADE_START = 0.28f
        private const val PAUSE_FADE_END = 1f
        private const val PLAY_SQUEEZE_X = 0.18f
        private const val PLAY_SQUEEZE_Y = 0.06f
        private const val PAUSE_MIN_SCALE = 0.9f
        private const val MORPH_DURATION_MS = 420L
        private val MORPH_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}
