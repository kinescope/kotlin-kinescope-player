package io.kinescope.sdk.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import io.kinescope.sdk.R

/**
 * Center play/pause control with Figma path morph (play <-> pause) and light tap zoom.
 * Replay uses a static rewind icon.
 *
 * Uses the same [R.drawable.ic_play_pause_morph] asset as Compose; pause state = animation end.
 */
class KinescopePlayPauseMorphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageButton(context, attrs) {

    private var showingReplay = false
  /** false = play (morph start), true = pause (morph end) — matches Compose `atEnd`. */
    private var atEnd = false
    private var morphAnimating = false

    private val morphGlyphInset: Int
        get() = resources.getDimensionPixelSize(R.dimen.kinescope_play_pause_glyph_inset)

    private val rewindGlyphInset: Int
        get() = resources.getDimensionPixelSize(R.dimen.kinescope_play_pause_rewind_glyph_inset)

    init {
        background = null
        scaleType = ScaleType.FIT_CENTER
        applyGlyphInset(replay = false)
        showMorphFrame(atEnd = false)
    }

    fun showReplay() {
        showingReplay = true
        morphAnimating = false
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        setImageResource(R.drawable.ic_controls_rewind)
        applyGlyphInset(replay = true)
    }

    fun isShowingReplay(): Boolean = showingReplay

    fun setPlaying(isPlaying: Boolean, animated: Boolean = true) {
        val leavingReplay = showingReplay
        if (showingReplay) {
            showingReplay = false
        }
        if (!leavingReplay && morphAnimating && isPlaying == atEnd) {
            return
        }
        if (!animated) {
            if (!leavingReplay && isPlaying == atEnd) {
                return
            }
            atEnd = isPlaying
            morphAnimating = false
            showMorphFrame(atEnd)
            return
        }
        if (!leavingReplay && isPlaying == atEnd) {
            return
        }
        atEnd = isPlaying
        morphAnimating = true
        val morphRes = if (isPlaying) {
            R.drawable.ic_play_pause_morph
        } else {
            R.drawable.ic_pause_play_morph
        }
        startMorph(morphRes)
    }

    fun applyIconTint(color: Int) {
        imageTintList = ColorStateList.valueOf(color)
    }

    private fun applyGlyphInset(replay: Boolean) {
        val inset = if (replay) rewindGlyphInset else morphGlyphInset
        setPadding(inset, inset, inset, inset)
    }

    private fun showMorphFrame(atEnd: Boolean) {
        morphAnimating = false
        applyGlyphInset(replay = false)
        val glyphRes = if (atEnd) {
            R.drawable.ic_center_pause_glyph
        } else {
            R.drawable.ic_center_play_glyph
        }
        setImageResource(glyphRes)
    }

    private fun startMorph(@DrawableRes morphRes: Int) {
        val drawable = AnimatedVectorDrawableCompat.create(context, morphRes) ?: return
        setImageDrawable(drawable)
        applyGlyphInset(replay = false)
        val animatable = drawable as? Animatable ?: return
        animatable.start()
        postDelayed({
            morphAnimating = false
        }, MORPH_DURATION_MS)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                animate()
                    .scaleX(PRESS_SCALE)
                    .scaleY(PRESS_SCALE)
                    .setDuration(PRESS_ANIM_MS)
                    .setInterpolator(PRESS_INTERPOLATOR)
                    .start()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().cancel()
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(PRESS_ANIM_MS)
                    .setInterpolator(PRESS_INTERPOLATOR)
                    .start()
            }
        }
        return super.onTouchEvent(event)
    }

    private companion object {
        private const val PRESS_SCALE = 1.08f
        private const val PRESS_ANIM_MS = 90L
        private const val MORPH_DURATION_MS = 90L
        private val PRESS_INTERPOLATOR = DecelerateInterpolator()
    }
}
