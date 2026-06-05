package io.kinescope.sdk.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import io.kinescope.sdk.R

class KinesopeSeekView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)

    private val scrubOverlay: View
    private val scrubTopBar: LinearLayout
    private val moveBackButton: ImageView
    private val moveForwardButton: ImageView

    private val seekFeedbackBack: FrameLayout
    private val seekFeedbackForward: FrameLayout
    private val seekFeedbackBackHemisphere: View
    private val seekFeedbackForwardHemisphere: View
    private val seekFeedbackBackHemisphereClip: FrameLayout
    private val seekFeedbackForwardHemisphereClip: FrameLayout
    private val seekFeedbackBackIcon: ImageView
    private val seekFeedbackForwardIcon: ImageView
    private val seekFeedbackBackSeconds: TextView
    private val seekFeedbackForwardSeconds: TextView
    private val seekFeedbackBackContent: View
    private val seekFeedbackForwardContent: View

    private var backShiftAnimator: ObjectAnimator? = null
    private var forwardShiftAnimator: ObjectAnimator? = null
    private var feedbackIconAnimator: ObjectAnimator? = null

    private var hideSeekFeedbackRunnable: Runnable? = null
    private var visibleEdgeForward: Boolean? = null
    private var isFullscreenMode = false

    init {
        inflate(context, R.layout.view_kinescope_seek_view, this)
        scrubOverlay = findViewById(R.id.scrub_overlay)
        scrubTopBar = findViewById(R.id.scrub_top_bar)
        moveBackButton = findViewById(R.id.btn_move_back)
        moveForwardButton = findViewById(R.id.btn_move_forward)

        seekFeedbackBack = findViewById(R.id.seek_feedback_back)
        seekFeedbackForward = findViewById(R.id.seek_feedback_forward)
        seekFeedbackBackHemisphere = findViewById(R.id.seek_feedback_back_hemisphere)
        seekFeedbackForwardHemisphere = findViewById(R.id.seek_feedback_forward_hemisphere)
        seekFeedbackBackHemisphereClip = findViewById(R.id.seek_feedback_back_hemisphere_clip)
        seekFeedbackForwardHemisphereClip = findViewById(R.id.seek_feedback_forward_hemisphere_clip)
        seekFeedbackBackIcon = findViewById(R.id.seek_feedback_back_icon)
        seekFeedbackForwardIcon = findViewById(R.id.seek_feedback_forward_icon)
        seekFeedbackBackSeconds = findViewById(R.id.seek_feedback_back_seconds)
        seekFeedbackForwardSeconds = findViewById(R.id.seek_feedback_forward_seconds)
        seekFeedbackBackContent = findViewById(R.id.seek_feedback_back_content)
        seekFeedbackForwardContent = findViewById(R.id.seek_feedback_forward_content)
        applyEdgeFeedbackStyle(fullscreen = false)
    }

    fun setFullscreenMode(fullscreen: Boolean) {
        if (isFullscreenMode == fullscreen) {
            return
        }
        isFullscreenMode = fullscreen
        applyEdgeFeedbackStyle(fullscreen)
    }

    private fun applyEdgeFeedbackStyle(fullscreen: Boolean) {
        val diameter = resources.getDimensionPixelSize(
            if (fullscreen) {
                R.dimen.kinescope_seek_feedback_circle_diameter_fullscreen
            } else {
                R.dimen.kinescope_seek_feedback_circle_diameter
            },
        )
        val radius = diameter / 2
        val contentOffset = (radius * CONTENT_OFFSET_IN_RADIUS).toInt()

        updateEdgePanelLayout(seekFeedbackBackHemisphereClip, radius)
        updateEdgePanelLayout(seekFeedbackForwardHemisphereClip, radius)
        layoutCircleHemisphere(seekFeedbackBackHemisphere, diameter, radius, isLeft = true)
        layoutCircleHemisphere(seekFeedbackForwardHemisphere, diameter, radius, isLeft = false)
        seekFeedbackBackHemisphere.setBackgroundResource(R.drawable.bg_seek_feedback_circle)
        seekFeedbackForwardHemisphere.setBackgroundResource(R.drawable.bg_seek_feedback_circle)
        updateEdgeFeedbackScalePivot(seekFeedbackBack, fromStart = true)
        updateEdgeFeedbackScalePivot(seekFeedbackForward, fromStart = false)

        (seekFeedbackBackContent.layoutParams as? MarginLayoutParams)?.let { params ->
            params.marginStart = contentOffset
            seekFeedbackBackContent.layoutParams = params
        }
        (seekFeedbackForwardContent.layoutParams as? MarginLayoutParams)?.let { params ->
            params.marginEnd = contentOffset
            seekFeedbackForwardContent.layoutParams = params
        }
    }

    private fun updateEdgePanelLayout(panel: View, widthPx: Int) {
        val params = panel.layoutParams ?: return
        params.width = widthPx
        panel.layoutParams = params
    }

    private fun updateEdgeFeedbackScalePivot(panel: View, fromStart: Boolean) {
        panel.post {
            panel.pivotX = if (fromStart) 0f else panel.width.toFloat()
            panel.pivotY = panel.height / 2f
        }
    }

    private fun layoutCircleHemisphere(view: View, diameter: Int, radius: Int, isLeft: Boolean) {
        val params = FrameLayout.LayoutParams(diameter, diameter).apply {
            gravity = if (isLeft) {
                Gravity.CENTER_VERTICAL or Gravity.START
            } else {
                Gravity.CENTER_VERTICAL or Gravity.END
            }
            if (isLeft) {
                marginStart = -radius
            } else {
                marginEnd = -radius
            }
        }
        view.layoutParams = params
    }

    fun showScrubOverlay() {
        if (scrubOverlay.isVisible) {
            return
        }
        scrubOverlay.alpha = 0f
        scrubTopBar.alpha = 0f
        scrubOverlay.isVisible = true
        scrubTopBar.isVisible = true
        scrubOverlay.animate()
            .alpha(1f)
            .setDuration(SCRUB_OVERLAY_FADE_MS)
            .start()
        scrubTopBar.animate()
            .alpha(1f)
            .setDuration(SCRUB_OVERLAY_FADE_MS)
            .start()
        startScrubHintIconAnimations()
    }

    fun hideScrubOverlay() {
        if (!scrubOverlay.isVisible) {
            return
        }
        stopScrubHintIconAnimations()
        scrubOverlay.animate()
            .alpha(0f)
            .setDuration(SCRUB_OVERLAY_FADE_MS)
            .start()
        scrubTopBar.animate()
            .alpha(0f)
            .setDuration(SCRUB_OVERLAY_FADE_MS)
            .withEndAction {
                scrubOverlay.isVisible = false
                scrubTopBar.isVisible = false
                scrubOverlay.alpha = 1f
                scrubTopBar.alpha = 1f
            }
            .start()
    }

    fun showSeekFeedback(forward: Boolean, totalSeconds: Int) {
        val container = if (forward) seekFeedbackForward else seekFeedbackBack
        val icon = if (forward) seekFeedbackForwardIcon else seekFeedbackBackIcon
        val secondsView = if (forward) seekFeedbackForwardSeconds else seekFeedbackBackSeconds
        val otherContainer = if (forward) seekFeedbackBack else seekFeedbackForward
        val alreadyVisible = container.isVisible && container.alpha > 0.5f && visibleEdgeForward == forward

        hideSeekFeedbackRunnable?.let { removeCallbacks(it) }
        otherContainer.animate().cancel()
        otherContainer.isVisible = false

        setEdgeSecondsVisible(true)
        secondsView.text = context.getString(R.string.player_seek_seconds, totalSeconds)
        secondsView.scaleX = 1f
        secondsView.scaleY = 1f

        visibleEdgeForward = forward

        if (alreadyVisible) {
            container.animate().cancel()
            container.alpha = 1f
            container.scaleX = 1f
            container.scaleY = 1f
            startFeedbackIconAnimation(icon, forward)
        } else {
            showEdgePanel(forward = forward, animated = true)
            startFeedbackIconAnimation(icon, forward)
        }

        hideSeekFeedbackRunnable = Runnable {
            hideEdgePanel(animated = true)
            visibleEdgeForward = null
        }.also { postDelayed(it, DOUBLE_TAP_FEEDBACK_VISIBLE_MS) }
    }

    private fun showEdgePanel(forward: Boolean, animated: Boolean) {
        val container = if (forward) seekFeedbackForward else seekFeedbackBack
        val otherContainer = if (forward) seekFeedbackBack else seekFeedbackForward
        val icon = if (forward) seekFeedbackForwardIcon else seekFeedbackBackIcon

        if (visibleEdgeForward == forward && container.isVisible && container.alpha >= 1f) {
            return
        }

        otherContainer.animate().cancel()
        otherContainer.isVisible = false
        visibleEdgeForward = forward

        container.animate().cancel()
        updateEdgeFeedbackScalePivot(container, fromStart = !forward)
        if (animated && !container.isVisible) {
            container.alpha = 0f
            container.scaleX = FEEDBACK_SCALE_COLLAPSED
            container.scaleY = FEEDBACK_SCALE_COLLAPSED
            container.isVisible = true
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(FEEDBACK_SHOW_MS)
                .start()
        } else {
            container.alpha = 1f
            container.scaleX = 1f
            container.scaleY = 1f
            container.isVisible = true
        }

        startFeedbackIconAnimation(icon, forward)
    }

    private fun hideEdgePanel(animated: Boolean) {
        val container = when (visibleEdgeForward) {
            true -> seekFeedbackForward
            false -> seekFeedbackBack
            null -> return
        }

        container.animate().cancel()
        stopFeedbackIconAnimation()

        if (!animated) {
            container.isVisible = false
            container.alpha = 1f
            container.scaleX = 1f
            container.scaleY = 1f
            return
        }

        container.animate()
            .alpha(0f)
            .setDuration(FEEDBACK_HIDE_MS)
            .withEndAction {
                container.isVisible = false
                container.alpha = 1f
                container.scaleX = 1f
                container.scaleY = 1f
            }
            .start()
    }

    private fun setEdgeSecondsVisible(visible: Boolean) {
        seekFeedbackBackSeconds.isVisible = visible
        seekFeedbackForwardSeconds.isVisible = visible
    }

    private fun startFeedbackIconAnimation(icon: ImageView, forward: Boolean) {
        stopFeedbackIconAnimation()
        val shiftPx = 8f * resources.displayMetrics.density
        val targetTranslation = if (forward) -shiftPx else shiftPx
        feedbackIconAnimator = ObjectAnimator.ofFloat(
            icon,
            View.TRANSLATION_X,
            0f,
            targetTranslation,
        ).apply {
            duration = SEEK_BUTTON_ANIMATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopFeedbackIconAnimation() {
        feedbackIconAnimator?.cancel()
        feedbackIconAnimator = null
        seekFeedbackBackIcon.translationX = 0f
        seekFeedbackForwardIcon.translationX = 0f
    }

    private fun startScrubHintIconAnimations() {
        stopScrubHintIconAnimations()
        val shiftPx = 8f * resources.displayMetrics.density

        backShiftAnimator = ObjectAnimator.ofFloat(
            moveBackButton,
            View.TRANSLATION_X,
            0f,
            -shiftPx,
        ).apply {
            duration = SEEK_BUTTON_ANIMATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        forwardShiftAnimator = ObjectAnimator.ofFloat(
            moveForwardButton,
            View.TRANSLATION_X,
            0f,
            shiftPx,
        ).apply {
            duration = SEEK_BUTTON_ANIMATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopScrubHintIconAnimations() {
        backShiftAnimator?.cancel()
        forwardShiftAnimator?.cancel()
        backShiftAnimator = null
        forwardShiftAnimator = null
        moveBackButton.translationX = 0f
        moveForwardButton.translationX = 0f
    }

    override fun onDetachedFromWindow() {
        hideSeekFeedbackRunnable?.let { removeCallbacks(it) }
        stopScrubHintIconAnimations()
        stopFeedbackIconAnimation()
        super.onDetachedFromWindow()
    }

    private companion object {
        private const val CONTENT_OFFSET_IN_RADIUS = 0.42f
        private const val SCRUB_OVERLAY_FADE_MS = 150L
        private const val SEEK_BUTTON_ANIMATION_MS = 450L
        private const val FEEDBACK_SHOW_MS = 180L
        private const val FEEDBACK_HIDE_MS = 150L
        private const val DOUBLE_TAP_FEEDBACK_VISIBLE_MS = 1500L
        private const val FEEDBACK_SCALE_COLLAPSED = 0.88f
    }
}
