package io.kinescope.sdk.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
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

    private lateinit var scrubTopBar: ViewGroup
    private lateinit var moveBackButton: SeekDirectionIconView
    private lateinit var moveForwardButton: SeekDirectionIconView

    private val seekFeedbackBack: FrameLayout
    private val seekFeedbackForward: FrameLayout
    private val seekFeedbackBackHemisphere: View
    private val seekFeedbackForwardHemisphere: View
    private val seekFeedbackBackHemisphereClip: FrameLayout
    private val seekFeedbackForwardHemisphereClip: FrameLayout
    private val seekFeedbackBackIcon: SeekDirectionIconView
    private val seekFeedbackForwardIcon: SeekDirectionIconView
    private val seekFeedbackBackSeconds: TextView
    private val seekFeedbackForwardSeconds: TextView
    private val seekFeedbackBackContent: View
    private val seekFeedbackForwardContent: View


    private var hideSeekFeedbackRunnable: Runnable? = null
    private var seekFeedbackHiddenListener: (() -> Unit)? = null
    private var scrubOverlayHiddenListener: (() -> Unit)? = null
    private var visibleEdgeForward: Boolean? = null
    private var isFullscreenMode = false
    private var isMobilePlayerChrome = false
    private var isPortraitContent = false

    init {
        inflate(context, R.layout.view_kinescope_seek_view, this)

        seekFeedbackBack = findViewById(R.id.seek_feedback_back)
        seekFeedbackForward = findViewById(R.id.seek_feedback_forward)
        seekFeedbackBackHemisphere = findViewById(R.id.seek_feedback_back_hemisphere)
        seekFeedbackForwardHemisphere = findViewById(R.id.seek_feedback_forward_hemisphere)
        seekFeedbackBackHemisphereClip = findViewById(R.id.seek_feedback_back_hemisphere_clip)
        seekFeedbackForwardHemisphereClip = findViewById(R.id.seek_feedback_forward_hemisphere_clip)
        seekFeedbackBackIcon = findViewById(R.id.seek_feedback_back_icon)
        seekFeedbackForwardIcon = findViewById(R.id.seek_feedback_forward_icon)
        seekFeedbackBackIcon.forward = false
        seekFeedbackForwardIcon.forward = true
        seekFeedbackBackSeconds = findViewById(R.id.seek_feedback_back_seconds)
        seekFeedbackForwardSeconds = findViewById(R.id.seek_feedback_forward_seconds)
        seekFeedbackBackContent = findViewById(R.id.seek_feedback_back_content)
        seekFeedbackForwardContent = findViewById(R.id.seek_feedback_forward_content)
        applyEdgeFeedbackStyle(fullscreen = false)
    }

    fun attachScrubHintBar(topBar: ViewGroup) {
        scrubTopBar = topBar
        moveBackButton = topBar.findViewById(R.id.btn_move_back)
        moveForwardButton = topBar.findViewById(R.id.btn_move_forward)
        moveBackButton.forward = false
        moveForwardButton.forward = true
    }

    private fun ensureScrubHintBarAttached() {
        check(::scrubTopBar.isInitialized) {
            "Scrub hint bar is not attached. Call attachScrubHintBar() from KinescopePlayerView."
        }
    }

    fun setFullscreenMode(fullscreen: Boolean) {
        if (isFullscreenMode == fullscreen) {
            return
        }
        isFullscreenMode = fullscreen
        applyEdgeFeedbackStyle(fullscreen)
    }

    fun setMobilePlayerChrome(mobile: Boolean) {
        if (isMobilePlayerChrome == mobile) {
            return
        }
        isMobilePlayerChrome = mobile
        applyEdgeFeedbackStyle(isFullscreenMode)
    }

    fun setPortraitContent(portrait: Boolean) {
        if (isPortraitContent == portrait) {
            return
        }
        isPortraitContent = portrait
        applyEdgeFeedbackStyle(isFullscreenMode)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val sizeChanged = w != oldw || h != oldh
        if (sizeChanged && w > 0) {
            applyEdgeFeedbackStyle(isFullscreenMode)
        }
    }

    private fun applyEdgeFeedbackStyle(fullscreen: Boolean) {
        if (isPortraitContent && width > 0 && height > 0) {
            applyPortraitOvalHemisphereStyle(fullscreen)
            return
        }

        val fixedDiameter = resources.getDimensionPixelSize(
            R.dimen.kinescope_seek_feedback_circle_diameter,
        )
        val diameter = when {
            // Landscape fullscreen — hemispheres sized to screen height.
            fullscreen && height > 0 ->
                (height * FULLSCREEN_HEMISPHERE_SCALE).toInt()
            // Landscape inline — fixed-size hemispheres.
            else -> fixedDiameter
        }
        applyCircleHemisphereStyle(diameter)
    }

    /** Tall half-ovals along left/right edges for vertical video. */
    private fun applyPortraitOvalHemisphereStyle(fullscreen: Boolean) {
        val fixedDiameter = resources.getDimensionPixelSize(
            R.dimen.kinescope_seek_feedback_circle_diameter,
        )
        val ovalWidth = if (fullscreen) {
            (width * FULLSCREEN_PORTRAIT_OVAL_WIDTH_SCALE).toInt()
        } else {
            minOf(fixedDiameter, (width * INLINE_PORTRAIT_OVAL_WIDTH_SCALE).toInt())
        }
        val ovalHeight = maxOf(
            (height * PORTRAIT_OVAL_HEIGHT_STRETCH).toInt(),
            (ovalWidth * PORTRAIT_OVAL_MIN_ASPECT).toInt(),
        )
        applyOvalHemisphereStyle(ovalWidth, ovalHeight)
    }

    private fun applyCircleHemisphereStyle(diameter: Int) {
        applyOvalHemisphereStyle(diameter, diameter)
    }

    private fun applyOvalHemisphereStyle(ovalWidth: Int, ovalHeight: Int) {
        val halfWidth = ovalWidth / 2
        val contentOffset = (halfWidth * CONTENT_OFFSET_IN_RADIUS).toInt()

        updateEdgePanelLayout(seekFeedbackBackHemisphereClip, halfWidth)
        updateEdgePanelLayout(seekFeedbackForwardHemisphereClip, halfWidth)
        layoutOvalHemisphere(seekFeedbackBackHemisphere, ovalWidth, ovalHeight, isLeft = true)
        layoutOvalHemisphere(seekFeedbackForwardHemisphere, ovalWidth, ovalHeight, isLeft = false)
        seekFeedbackBackHemisphere.setBackgroundResource(R.drawable.bg_seek_feedback_circle)
        seekFeedbackForwardHemisphere.setBackgroundResource(R.drawable.bg_seek_feedback_circle)
        updateEdgeFeedbackScalePivot(seekFeedbackBack, fromStart = true)
        updateEdgeFeedbackScalePivot(seekFeedbackForward, fromStart = false)
        applyEdgeContentOffsets(contentOffset)
    }

    private fun applyEdgeContentOffsets(contentOffset: Int) {
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

    private fun layoutOvalHemisphere(view: View, ovalWidth: Int, ovalHeight: Int, isLeft: Boolean) {
        val halfWidth = ovalWidth / 2
        val params = FrameLayout.LayoutParams(ovalWidth, ovalHeight).apply {
            gravity = if (isLeft) {
                Gravity.CENTER_VERTICAL or Gravity.START
            } else {
                Gravity.CENTER_VERTICAL or Gravity.END
            }
            if (isLeft) {
                marginStart = -halfWidth
            } else {
                marginEnd = -halfWidth
            }
        }
        view.layoutParams = params
    }

    fun showScrubOverlay() {
        ensureScrubHintBarAttached()
        cancelScrubOverlayHiddenListener()
        if (scrubTopBar.isVisible && scrubTopBar.alpha >= 1f) {
            startScrubHintIconAnimations()
            return
        }
        scrubTopBar.alpha = 0f
        scrubTopBar.isVisible = true
        scrubTopBar.animate()
            .alpha(1f)
            .setDuration(SCRUB_OVERLAY_FADE_MS)
            .start()
        startScrubHintIconAnimations()
    }

    fun hideScrubOverlay(onHidden: (() -> Unit)? = null) {
        scrubOverlayHiddenListener = onHidden
        if (!::scrubTopBar.isInitialized || !scrubTopBar.isVisible) {
            notifyScrubOverlayHidden()
            return
        }
        stopScrubHintIconAnimations()
        scrubTopBar.animate().cancel()
        scrubTopBar.animate()
            .alpha(0f)
            .setDuration(SCRUB_OVERLAY_FADE_MS)
            .withEndAction {
                scrubTopBar.isVisible = false
                scrubTopBar.alpha = 1f
                notifyScrubOverlayHidden()
            }
            .start()
    }

    private fun cancelScrubOverlayHiddenListener() {
        scrubOverlayHiddenListener = null
    }

    private fun notifyScrubOverlayHidden() {
        val listener = scrubOverlayHiddenListener
        scrubOverlayHiddenListener = null
        listener?.invoke()
    }

    fun hideSeekFeedback() {
        hideSeekFeedbackRunnable?.let { removeCallbacks(it) }
        hideSeekFeedbackRunnable = null
        seekFeedbackHiddenListener = null
        hideEdgePanel(animated = false)
        visibleEdgeForward = null
    }

    fun showSeekFeedback(
        forward: Boolean,
        totalSeconds: Int,
        onHidden: (() -> Unit)? = null,
    ) {
        val container = if (forward) seekFeedbackForward else seekFeedbackBack
        val icon = if (forward) seekFeedbackForwardIcon else seekFeedbackBackIcon
        val secondsView = if (forward) seekFeedbackForwardSeconds else seekFeedbackBackSeconds
        val otherContainer = if (forward) seekFeedbackBack else seekFeedbackForward
        val alreadyVisible = container.isVisible && container.alpha > 0.5f && visibleEdgeForward == forward

        hideSeekFeedbackRunnable?.let { removeCallbacks(it) }
        seekFeedbackHiddenListener = onHidden
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
        } else {
            showEdgePanel(forward = forward, animated = true)
        }

        hideSeekFeedbackRunnable = Runnable {
            val listener = seekFeedbackHiddenListener
            seekFeedbackHiddenListener = null
            if (listener != null) {
                listener.invoke()
            } else {
                hideEdgePanel(animated = true)
                visibleEdgeForward = null
            }
        }.also { postDelayed(it, SEEK_FEEDBACK_VISIBLE_MS) }
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

    private fun startFeedbackIconAnimation(icon: SeekDirectionIconView, forward: Boolean) {
        if (forward) {
            seekFeedbackBackIcon.stopRippleAnimation()
        } else {
            seekFeedbackForwardIcon.stopRippleAnimation()
        }
        icon.forward = forward
        icon.startRippleAnimation()
    }

    private fun stopFeedbackIconAnimation() {
        seekFeedbackBackIcon.stopRippleAnimation()
        seekFeedbackForwardIcon.stopRippleAnimation()
    }

    private fun startScrubHintIconAnimations() {
        if (!::moveBackButton.isInitialized) {
            return
        }
        stopScrubHintIconAnimations()
        moveBackButton.startRippleAnimation()
        moveForwardButton.startRippleAnimation()
    }

    private fun stopScrubHintIconAnimations() {
        moveBackButton.stopRippleAnimation()
        moveForwardButton.stopRippleAnimation()
    }

    override fun onDetachedFromWindow() {
        hideSeekFeedbackRunnable?.let { removeCallbacks(it) }
        seekFeedbackHiddenListener = null
        cancelScrubOverlayHiddenListener()
        stopScrubHintIconAnimations()
        stopFeedbackIconAnimation()
        super.onDetachedFromWindow()
    }

    private companion object {
        private const val CONTENT_OFFSET_IN_RADIUS = 0.42f
        /** Landscape fullscreen hemisphere diameter vs screen height. */
        private const val FULLSCREEN_HEMISPHERE_SCALE = 1.45f
        /** Portrait fullscreen oval width vs player width. */
        private const val FULLSCREEN_PORTRAIT_OVAL_WIDTH_SCALE = 1.2f
        /** Portrait inline: compact oval width vs player width. */
        private const val INLINE_PORTRAIT_OVAL_WIDTH_SCALE = 0.58f
        /** Stretch oval past player height so the clipped edge looks elongated. */
        private const val PORTRAIT_OVAL_HEIGHT_STRETCH = 1.45f
        /** Minimum oval height / width for portrait half-ovals. */
        private const val PORTRAIT_OVAL_MIN_ASPECT = 2.6f
        private const val SCRUB_OVERLAY_FADE_MS = 150L
        private const val FEEDBACK_SHOW_MS = 180L
        private const val FEEDBACK_HIDE_MS = 150L
        const val SEEK_FEEDBACK_VISIBLE_MS = 500L
        private const val FEEDBACK_SCALE_COLLAPSED = 0.88f
    }
}
