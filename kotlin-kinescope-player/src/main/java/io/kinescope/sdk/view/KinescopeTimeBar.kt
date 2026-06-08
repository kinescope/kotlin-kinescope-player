package io.kinescope.sdk.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.TimeBar
import io.kinescope.sdk.R
import java.util.Formatter
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

/**
 * A time bar that shows a current position, buffered position, duration and ad markers.
 *
 *
 * A DefaultTimeBar can be customized by setting attributes, as outlined below.
 *
 * <h2>Attributes</h2>
 *
 * The following attributes can be set on a DefaultTimeBar when used in a layout XML file:
 *
 *
 *  * **`bar_height`** - Dimension for the height of the time bar.
 *
 *  * Default: [.DEFAULT_BAR_HEIGHT_DP]
 *
 *  * **`touch_target_height`** - Dimension for the height of the area in which touch
 * interactions with the time bar are handled. If no height is specified, this also determines
 * the height of the view.
 *
 *  * Default: [.DEFAULT_TOUCH_TARGET_HEIGHT_DP]
 *
 *  * **`ad_marker_width`** - Dimension for the width of any ad markers shown on the
 * bar. Ad markers are superimposed on the time bar to show the times at which ads will play.
 *
 *  * Default: [.DEFAULT_AD_MARKER_WIDTH_DP]
 *
 *  * **`scrubber_enabled_size`** - Dimension for the diameter of the circular scrubber
 * handle when scrubbing is enabled but not in progress. Set to zero if no scrubber handle
 * should be shown.
 *
 *  * Default: [.DEFAULT_SCRUBBER_ENABLED_SIZE_DP]
 *
 *  * **`scrubber_disabled_size`** - Dimension for the diameter of the circular scrubber
 * handle when scrubbing isn't enabled. Set to zero if no scrubber handle should be shown.
 *
 *  * Default: [.DEFAULT_SCRUBBER_DISABLED_SIZE_DP]
 *
 *  * **`scrubber_dragged_size`** - Dimension for the diameter of the circular scrubber
 * handle when scrubbing is in progress. Set to zero if no scrubber handle should be shown.
 *
 *  * Default: [.DEFAULT_SCRUBBER_DRAGGED_SIZE_DP]
 *
 *  * **`scrubber_drawable`** - Optional reference to a drawable to draw for the
 * scrubber handle. If set, this overrides the default behavior, which is to draw a circle for
 * the scrubber handle.
 *  * **`played_color`** - Color for the portion of the time bar representing media
 * before the current playback position.
 *
 *  * Corresponding method: [.setPlayedColor]
 *  * Default: [.DEFAULT_PLAYED_COLOR]
 *
 *  * **`scrubber_color`** - Color for the scrubber handle.
 *
 *  * Corresponding method: [.setScrubberColor]
 *  * Default: [.DEFAULT_SCRUBBER_COLOR]
 *
 *  * **`buffered_color`** - Color for the portion of the time bar after the current
 * played position up to the current buffered position.
 *
 *  * Corresponding method: [.setBufferedColor]
 *  * Default: [.DEFAULT_BUFFERED_COLOR]
 *
 *  * **`unplayed_color`** - Color for the portion of the time bar after the current
 * buffered position.
 *
 *  * Corresponding method: [.setUnplayedColor]
 *  * Default: [.DEFAULT_UNPLAYED_COLOR]
 *
 *  * **`ad_marker_color`** - Color for unplayed ad markers.
 *
 *  * Corresponding method: [.setAdMarkerColor]
 *  * Default: [.DEFAULT_AD_MARKER_COLOR]
 *
 *  * **`played_ad_marker_color`** - Color for played ad markers.
 *
 *  * Corresponding method: [.setPlayedAdMarkerColor]
 *  * Default: [.DEFAULT_PLAYED_AD_MARKER_COLOR]
 *
 *
 */

@UnstableApi
class KinescopeTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    timebarAttrs: AttributeSet? = attrs,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr), TimeBar {
    private val seekBounds: Rect
    private val progressBar: Rect
    private val bufferedBar: Rect
    private val scrubberBar: Rect
    private val playedPaint: Paint
    private val bufferedPaint: Paint
    private val unplayedPaint: Paint
    private val playedAdMarkerPaint: Paint
    private val scrubberPaint: Paint
    private var scrubberDrawable: Drawable? = null
    private var barHeight = 0
    private var baseBarHeight = 0
    private var barHeightAnimator: ValueAnimator? = null
    private var touchTargetHeight = 0
    private var barGravity = 0
    private var scrubberEnabledSize = 0
    private var scrubberDisabledSize = 0
    private var scrubberDraggedSize = 0
    private var scrubberPadding = 0
    private val fineScrubYThreshold: Int
    private val formatBuilder: StringBuilder
    private val formatter: Formatter
    private val stopScrubbingRunnable: Runnable
    private val listeners: CopyOnWriteArraySet<TimeBar.OnScrubListener>
    private val touchPosition: Point
    private val density: Float
    private var keyCountIncrement: Int
    private var keyTimeIncrement: Long
    private var lastCoarseScrubXPosition = 0

    private var lastExclusionRectangle: Rect? = null
    private val scrubberScalingAnimator: ValueAnimator
    private var scrubberScale: Float
    private var scrubberPaddingDisabled = false
    private var scrubbing = false
    private var scrubPosition: Long = 0
    private var duration: Long
    private var position: Long = 0
    private var bufferedPosition: Long = 0
    private var scrubVisualExpanded = false
    private val scrubBarCornerRadiusPx: Float

    //private var adGroupCount = 0
    //private var adGroupTimesMs: LongArray? = null
    //private var playedAdGroups: BooleanArray? = null

    init {
        seekBounds = Rect()
        progressBar = Rect()
        bufferedBar = Rect()
        scrubberBar = Rect()
        playedPaint = Paint()
        bufferedPaint = Paint()
        unplayedPaint = Paint()
        //adMarkerPaint = Paint()
        playedAdMarkerPaint = Paint()
        scrubberPaint = Paint()
        scrubberPaint.isAntiAlias = true
        listeners = CopyOnWriteArraySet()
        touchPosition = Point()

        val primaryColor =
            ContextCompat.getColor(context, R.color.kinescope_primary_color)
        val bufferedColorDefault =
            ContextCompat.getColor(context, R.color.kinescope_progressbar_buffered_color)
        val unplayedColorDefault =
            ContextCompat.getColor(context, R.color.kinescope_progressbar_unplayed_color)

        // Calculate the dimensions and paints for drawn elements.
        val res = context.resources
        val displayMetrics = res.displayMetrics
        density = displayMetrics.density
        scrubBarCornerRadiusPx = dpToPx(density, SCRUB_BAR_CORNER_RADIUS_DP).toFloat()
        fineScrubYThreshold = dpToPx(
            density,
            FINE_SCRUB_Y_THRESHOLD_DP
        )
        val defaultBarHeight: Int =
            dpToPx(density, DEFAULT_BAR_HEIGHT_DP)
        var defaultTouchTargetHeight: Int = dpToPx(
            density,
            DEFAULT_TOUCH_TARGET_HEIGHT_DP
        )
        val defaultScrubberEnabledSize: Int = dpToPx(
            density,
            DEFAULT_SCRUBBER_ENABLED_SIZE_DP
        )
        val defaultScrubberDisabledSize: Int = dpToPx(
            density,
            DEFAULT_SCRUBBER_DISABLED_SIZE_DP
        )
        val defaultScrubberDraggedSize: Int = dpToPx(
            density,
            DEFAULT_SCRUBBER_DRAGGED_SIZE_DP
        )
        if (timebarAttrs != null) {
            val a = context
                .theme
                .obtainStyledAttributes(
                    timebarAttrs, R.styleable.KinescopeTimeBar, defStyleAttr, defStyleRes
                )
            try {
                scrubberDrawable = a.getDrawable(R.styleable.KinescopeTimeBar_scrubber_drawable)
                scrubberDrawable?.let {
                    setDrawableLayoutDirection(it)
                    defaultTouchTargetHeight =
                        Math.max(it.minimumHeight, defaultTouchTargetHeight)
                }
                barHeight =
                    a.getDimensionPixelSize(
                        R.styleable.KinescopeTimeBar_bar_height,
                        defaultBarHeight
                    )
                touchTargetHeight = a.getDimensionPixelSize(
                    R.styleable.KinescopeTimeBar_touch_target_height, defaultTouchTargetHeight
                )
                barGravity = a.getInt(
                    R.styleable.KinescopeTimeBar_bar_gravity,
                    BAR_GRAVITY_CENTER
                )
                /*adMarkerWidth = a.getDimensionPixelSize(
                    R.styleable.DefaultTimeBar_ad_marker_width, defaultAdMarkerWidth
                )*/
                scrubberEnabledSize = a.getDimensionPixelSize(
                    R.styleable.KinescopeTimeBar_scrubber_enabled_size, defaultScrubberEnabledSize
                )
                scrubberDisabledSize = a.getDimensionPixelSize(
                    R.styleable.KinescopeTimeBar_scrubber_disabled_size, defaultScrubberDisabledSize
                )
                scrubberDraggedSize = a.getDimensionPixelSize(
                    R.styleable.KinescopeTimeBar_scrubber_dragged_size, defaultScrubberDraggedSize
                )
                val playedColor = a.getInt(
                    R.styleable.KinescopeTimeBar_played_color,
                    primaryColor
                )
                val scrubberColor = a.getInt(
                    R.styleable.KinescopeTimeBar_scrubber_color,
                    primaryColor
                )
                val bufferedColor = a.getInt(
                    R.styleable.KinescopeTimeBar_buffered_color,
                    bufferedColorDefault
                )
                val unplayedColor = a.getInt(
                    R.styleable.KinescopeTimeBar_unplayed_color,
                    unplayedColorDefault
                )
                playedPaint.color = playedColor
                scrubberPaint.color = scrubberColor
                bufferedPaint.color = bufferedColor
                unplayedPaint.color = unplayedColor
            } finally {
                a.recycle()
            }
        } else {
            barHeight = defaultBarHeight
            touchTargetHeight = defaultTouchTargetHeight
            barGravity = BAR_GRAVITY_CENTER
            scrubberEnabledSize = defaultScrubberEnabledSize
            scrubberDisabledSize = defaultScrubberDisabledSize
            scrubberDraggedSize = defaultScrubberDraggedSize

            playedPaint.color = primaryColor
            scrubberPaint.color = primaryColor
            bufferedPaint.color = bufferedColorDefault
            unplayedPaint.color = unplayedColorDefault

            playedAdMarkerPaint.color = DEFAULT_PLAYED_AD_MARKER_COLOR
            scrubberDrawable = null
        }
        baseBarHeight = barHeight
        formatBuilder = StringBuilder()
        formatter = Formatter(formatBuilder, Locale.getDefault())
        stopScrubbingRunnable = Runnable { stopScrubbing( /* canceled= */false) }
        scrubberPadding = if (scrubberDrawable != null) {
            (scrubberDrawable!!.minimumWidth + 1) / 2
        } else {
            ((Math.max(
                scrubberDisabledSize,
                Math.max(scrubberEnabledSize, scrubberDraggedSize)
            ) + 1)
                    / 2)
        }
        scrubberScale = 1.0f
        scrubberScalingAnimator = ValueAnimator()
        scrubberScalingAnimator.addUpdateListener { animation: ValueAnimator ->
            scrubberScale = animation.animatedValue as Float
            invalidate(seekBounds)
        }
        duration = C.TIME_UNSET
        keyTimeIncrement = C.TIME_UNSET
        keyCountIncrement = DEFAULT_INCREMENT_COUNT
        isFocusable = true
        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    /** Shows the scrubber handle.  */
    fun showScrubber() {
        if (scrubberScalingAnimator.isStarted) {
            scrubberScalingAnimator.cancel()
        }
        scrubberPaddingDisabled = false
        scrubberScale = 1f
        invalidate(seekBounds)
    }

    /**
     * Shows the scrubber handle with animation.
     *
     * @param showAnimationDurationMs The duration for scrubber showing animation.
     */
    fun showScrubber(showAnimationDurationMs: Long) {
        if (scrubberScalingAnimator.isStarted) {
            scrubberScalingAnimator.cancel()
        }
        scrubberPaddingDisabled = false
        scrubberScalingAnimator.setFloatValues(
            scrubberScale,
            SHOWN_SCRUBBER_SCALE
        )
        scrubberScalingAnimator.duration = showAnimationDurationMs
        scrubberScalingAnimator.start()
    }

    /** Hides the scrubber handle.  */
    fun hideScrubber(disableScrubberPadding: Boolean) {
        if (scrubberScalingAnimator.isStarted) {
            scrubberScalingAnimator.cancel()
        }
        scrubberPaddingDisabled = disableScrubberPadding
        scrubberScale = 0f
        invalidate(seekBounds)
    }

    /**
     * Hides the scrubber handle with animation.
     *
     * @param hideAnimationDurationMs The duration for scrubber hiding animation.
     */
    fun hideScrubber(hideAnimationDurationMs: Long) {
        if (scrubberScalingAnimator.isStarted) {
            scrubberScalingAnimator.cancel()
        }
        scrubberScalingAnimator.setFloatValues(
            scrubberScale,
            HIDDEN_SCRUBBER_SCALE
        )
        scrubberScalingAnimator.duration = hideAnimationDurationMs
        scrubberScalingAnimator.start()
    }

    /**
     * Sets the color for the portion of the time bar representing media before the playback position.
     *
     * @param playedColor The color for the portion of the time bar representing media before the
     * playback position.
     */
    fun setPlayedColor(@ColorInt playedColor: Int) {
        playedPaint.color = playedColor
        invalidate(seekBounds)
    }

    /**
     * Sets the color for the scrubber handle.
     *
     * @param scrubberColor The color for the scrubber handle.
     */
    fun setScrubberColor(@ColorInt scrubberColor: Int) {
        scrubberPaint.color = scrubberColor
        invalidate(seekBounds)

        scrubberDrawable?.let {
            if (it is VectorDrawable) {
                it.setTint(scrubberColor)
            }
        }
    }

    /**
     * Sets the color for the portion of the time bar after the current played position up to the
     * current buffered position.
     *
     * @param bufferedColor The color for the portion of the time bar after the current played
     * position up to the current buffered position.
     */
    fun setBufferedColor(@ColorInt bufferedColor: Int) {
        bufferedPaint.color = bufferedColor
        invalidate(seekBounds)
    }

    /**
     * Sets the color for the portion of the time bar after the current played position.
     *
     * @param unplayedColor The color for the portion of the time bar after the current played
     * position.
     */
    fun setUnplayedColor(@ColorInt unplayedColor: Int) {
        unplayedPaint.color = unplayedColor
        invalidate(seekBounds)
    }

    fun setScrubVisualExpanded(expanded: Boolean, animate: Boolean = true) {
        scrubVisualExpanded = expanded
        val targetHeight = if (expanded) {
            (baseBarHeight * SCRUB_BAR_HEIGHT_SCALE).toInt().coerceAtLeast(baseBarHeight + 1)
        } else {
            baseBarHeight
        }
        if (barHeight == targetHeight) {
            return
        }
        barHeightAnimator?.cancel()
        if (!animate) {
            barHeight = targetHeight
            requestLayout()
            return
        }
        barHeightAnimator = ValueAnimator.ofInt(barHeight, targetHeight).apply {
            duration = SCRUB_BAR_HEIGHT_ANIMATION_MS
            addUpdateListener { animation ->
                barHeight = animation.animatedValue as Int
                requestLayout()
            }
            start()
        }
    }

    /**
     * Sets the color for unplayed ad markers.
     *
     * @param adMarkerColor The color for unplayed ad markers.
     */
    fun setAdMarkerColor(@ColorInt adMarkerColor: Int) {
        //adMarkerPaint.color = adMarkerColor
        invalidate(seekBounds)
    }

    /**
     * Sets the color for played ad markers.
     *
     * @param playedAdMarkerColor The color for played ad markers.
     */
    fun setPlayedAdMarkerColor(@ColorInt playedAdMarkerColor: Int) {
        playedAdMarkerPaint.color = playedAdMarkerColor
        invalidate(seekBounds)
    }

    // TimeBar implementation.
    override fun addListener(listener: TimeBar.OnScrubListener) {
        Assertions.checkNotNull(listener)
        listeners.add(listener)
    }

    override fun removeListener(listener: TimeBar.OnScrubListener) {
        listeners.remove(listener)
    }

    override fun setKeyTimeIncrement(time: Long) {
        Assertions.checkArgument(time > 0)
        keyCountIncrement = C.INDEX_UNSET
        keyTimeIncrement = time
    }

    override fun setKeyCountIncrement(count: Int) {
        Assertions.checkArgument(count > 0)
        keyCountIncrement = count
        keyTimeIncrement = C.TIME_UNSET
    }

    override fun setPosition(position: Long) {
        if (this.position == position) {
            return
        }
        this.position = position
        contentDescription = progressText
        update()
    }

    override fun setBufferedPosition(bufferedPosition: Long) {
        if (this.bufferedPosition == bufferedPosition) {
            return
        }
        this.bufferedPosition = bufferedPosition
        update()
    }

    override fun setDuration(duration: Long) {
        if (this.duration == duration) {
            return
        }
        this.duration = duration
        if (scrubbing && duration == C.TIME_UNSET) {
            stopScrubbing( /* canceled= */true)
        }
        update()
    }

    override fun getPreferredUpdateDelay(): Long {
        val timeBarWidthDp: Int = pxToDp(density, progressBar.width())
        return if (timeBarWidthDp == 0 || duration == 0L || duration == C.TIME_UNSET) Long.MAX_VALUE else duration / timeBarWidthDp
    }

    override fun setAdGroupTimesMs(
        adGroupTimesMs: LongArray?, playedAdGroups: BooleanArray?, adGroupCount: Int
    ) {
    }

    // View methods.
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (scrubbing && !enabled) {
            stopScrubbing( /* canceled= */true)
        }
    }

    public override fun onDraw(canvas: Canvas) {
        canvas.save()
        drawTimeBar(canvas)
        drawPlayHead(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || duration <= 0) {
            return false
        }
        val touchPosition = resolveRelativeTouchPosition(event)
        val x = touchPosition.x
        val y = touchPosition.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> if (isInSeekBar(x.toFloat(), y.toFloat())) {
                positionScrubber(x.toFloat())
                startScrubbing(scrubberPosition)
                update()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> if (scrubbing) {
                if (y < fineScrubYThreshold) {
                    val relativeX = x - lastCoarseScrubXPosition
                    positionScrubber((lastCoarseScrubXPosition + relativeX / FINE_SCRUB_RATIO).toFloat())
                } else {
                    lastCoarseScrubXPosition = x
                    positionScrubber(x.toFloat())
                }
                updateScrubbing(scrubberPosition)
                update()
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (scrubbing) {
                stopScrubbing( /* canceled= */event.action == MotionEvent.ACTION_CANCEL)
                return true
            }

            else -> {}
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {
            var positionIncrement = positionIncrement
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    positionIncrement = -positionIncrement
                    if (scrubIncrementally(positionIncrement)) {
                        removeCallbacks(stopScrubbingRunnable)
                        postDelayed(
                            stopScrubbingRunnable,
                            STOP_SCRUBBING_TIMEOUT_MS
                        )
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> if (scrubIncrementally(positionIncrement)) {
                    removeCallbacks(stopScrubbingRunnable)
                    postDelayed(
                        stopScrubbingRunnable,
                        STOP_SCRUBBING_TIMEOUT_MS
                    )
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (scrubbing) {
                    stopScrubbing( /* canceled= */false)
                    return true
                }

                else -> {}
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(
        gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (scrubbing && !gainFocus) {
            stopScrubbing( /* canceled= */false)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateDrawableState()
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        scrubberDrawable?.jumpToCurrentState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height =
            if (heightMode == MeasureSpec.UNSPECIFIED) touchTargetHeight else if (heightMode == MeasureSpec.EXACTLY) heightSize else Math.min(
                touchTargetHeight,
                heightSize
            )
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
        updateDrawableState()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val seekLeft = paddingLeft
        val seekRight = width - paddingRight
        val contentHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val effectiveTouchHeight = Math.min(touchTargetHeight, contentHeight)
        val seekBoundsY: Int
        val progressBarY: Int
        val scrubberPadding = if (scrubberPaddingDisabled) 0 else scrubberPadding
        if (barGravity == BAR_GRAVITY_BOTTOM) {
            seekBoundsY = height - paddingBottom - effectiveTouchHeight
            progressBarY = seekBoundsY + (effectiveTouchHeight - barHeight) / 2
        } else {
            seekBoundsY = paddingTop + (contentHeight - effectiveTouchHeight) / 2
            progressBarY = seekBoundsY + (effectiveTouchHeight - barHeight) / 2
        }
        seekBounds[seekLeft, seekBoundsY, seekRight] = seekBoundsY + effectiveTouchHeight
        progressBar[seekBounds.left + scrubberPadding, progressBarY, seekBounds.right - scrubberPadding] =
            progressBarY + barHeight
        if (Util.SDK_INT >= 29) {
            setSystemGestureExclusionRectsV29(width, height)
        }
        update()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        if (scrubberDrawable != null && setDrawableLayoutDirection(
                scrubberDrawable!!,
                layoutDirection
            )
        ) {
            invalidate()
        }
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            event.text.add(progressText)
        }
        event.className = ACCESSIBILITY_CLASS_NAME
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = ACCESSIBILITY_CLASS_NAME
        info.contentDescription = progressText
        if (duration <= 0) {
            return
        }
        if (Util.SDK_INT >= 21) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        } else {
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, args: Bundle?): Boolean {
        if (super.performAccessibilityAction(action, args)) {
            return true
        }
        if (duration <= 0) {
            return false
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            if (scrubIncrementally(-positionIncrement)) {
                stopScrubbing( /* canceled= */false)
            }
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            if (scrubIncrementally(positionIncrement)) {
                stopScrubbing( /* canceled= */false)
            }
        } else {
            return false
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        return true
    }

    // Internal methods.
    private fun startScrubbing(scrubPosition: Long) {
        this.scrubPosition = scrubPosition
        scrubbing = true
        isPressed = true
        val parent = parent
        parent?.requestDisallowInterceptTouchEvent(true)
        for (listener in listeners) {
            listener.onScrubStart(this, scrubPosition)
        }
    }

    private fun updateScrubbing(scrubPosition: Long) {
        if (this.scrubPosition == scrubPosition) {
            return
        }
        this.scrubPosition = scrubPosition
        for (listener in listeners) {
            listener.onScrubMove(this, scrubPosition)
        }
    }

    private fun stopScrubbing(canceled: Boolean) {
        removeCallbacks(stopScrubbingRunnable)
        scrubbing = false
        isPressed = false
        val parent = parent
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
        for (listener in listeners) {
            listener.onScrubStop(this, scrubPosition, canceled)
        }
    }

    /**
     * Incrementally scrubs the position by `positionChange`.
     *
     * @param positionChange The change in the scrubber position, in milliseconds. May be negative.
     * @return Returns whether the scrubber position changed.
     */
    private fun scrubIncrementally(positionChange: Long): Boolean {
        if (duration <= 0) {
            return false
        }
        val previousPosition = if (scrubbing) scrubPosition else position
        val scrubPosition = Util.constrainValue(previousPosition + positionChange, 0, duration)
        if (scrubPosition == previousPosition) {
            return false
        }
        if (!scrubbing) {
            startScrubbing(scrubPosition)
        } else {
            updateScrubbing(scrubPosition)
        }
        update()
        return true
    }

    private fun update() {
        bufferedBar.set(progressBar)
        scrubberBar.set(progressBar)
        val newScrubberTime = if (scrubbing) scrubPosition else position
        if (duration > 0) {
            val bufferedPixelWidth = (progressBar.width() * bufferedPosition / duration).toInt()
            bufferedBar.right = Math.min(progressBar.left + bufferedPixelWidth, progressBar.right)
            val scrubberPixelPosition = (progressBar.width() * newScrubberTime / duration).toInt()
            scrubberBar.right =
                Math.min(progressBar.left + scrubberPixelPosition, progressBar.right)
        } else {
            bufferedBar.right = progressBar.left
            scrubberBar.right = progressBar.left
        }
        invalidate(seekBounds)
    }

    private fun positionScrubber(xPosition: Float) {
        scrubberBar.right =
            Util.constrainValue(xPosition.toInt(), progressBar.left, progressBar.right)
    }

    private fun resolveRelativeTouchPosition(motionEvent: MotionEvent): Point {
        touchPosition[motionEvent.x.toInt()] = motionEvent.y.toInt()
        return touchPosition
    }

    private val scrubberPosition: Long
        private get() = if (progressBar.width() <= 0 || duration == C.TIME_UNSET) {
            0
        } else (scrubberBar.width() * duration) / progressBar.width()

    private fun isInSeekBar(x: Float, y: Float): Boolean {
        return seekBounds.contains(x.toInt(), y.toInt())
    }

    private fun drawTimeBar(canvas: Canvas) {
        val progressBarHeight = progressBar.height()
        val barTop = progressBar.centerY() - progressBarHeight / 2f
        val barBottom = barTop + progressBarHeight
        val left = progressBar.left.toFloat()
        val right = progressBar.right.toFloat()
        val cornerRadius = currentBarCornerRadius(progressBarHeight)

        drawBarSegment(canvas, left, right, barTop, barBottom, cornerRadius, unplayedPaint)

        if (duration <= 0) {
            return
        }

        val playedEnd = scrubberBar.right.toFloat().coerceIn(left, right)
        val bufferedEnd = bufferedBar.right.toFloat().coerceIn(left, right)

        if (bufferedEnd > playedEnd) {
            drawBarSegment(canvas, playedEnd, bufferedEnd, barTop, barBottom, cornerRadius, bufferedPaint)
        }
        if (playedEnd > left) {
            drawBarSegment(canvas, left, playedEnd, barTop, barBottom, cornerRadius, playedPaint)
        }
    }

    private fun currentBarCornerRadius(barHeightPx: Int): Float {
        if (baseBarHeight <= 0) {
            return 0f
        }

        val expandedHeight = (baseBarHeight * SCRUB_BAR_HEIGHT_SCALE)
            .toInt()
            .coerceAtLeast(baseBarHeight + 1)
        val heightExpansion = if (barHeight > baseBarHeight) {
            ((barHeight - baseBarHeight).toFloat() / (expandedHeight - baseBarHeight)).coerceIn(0f, 1f)
        } else {
            0f
        }
        val scaleExpansion = if (scaleY > 1f) {
            ((scaleY - 1f) / (SCRUB_BAR_HEIGHT_SCALE - 1f)).coerceIn(0f, 1f)
        } else {
            0f
        }
        val expansion = maxOf(heightExpansion, scaleExpansion)
        if (!scrubVisualExpanded && expansion <= 0f) {
            return 0f
        }
        return minOf(scrubBarCornerRadiusPx * expansion, barHeightPx / 2f)
    }

    private fun drawBarSegment(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        cornerRadius: Float,
        paint: Paint,
    ) {
        if (right <= left) {
            return
        }
        if (cornerRadius <= 0f) {
            canvas.drawRect(left, top, right, bottom, paint)
        } else {
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
        }
    }

    private fun drawPlayHead(canvas: Canvas) {
        if (duration <= 0) {
            return
        }
        val playheadX = Util.constrainValue(scrubberBar.right, scrubberBar.left, progressBar.right)
        val playheadY = scrubberBar.centerY()
        if (scrubberDrawable == null) {
            val scrubberSize =
                if (scrubbing || isFocused) scrubberDraggedSize else if (isEnabled) scrubberEnabledSize else scrubberDisabledSize
            if (scrubberSize <= 0) {
                return
            }
            val playheadRadius = (scrubberSize * scrubberScale / 2).toInt()
            if (playheadRadius <= 0) {
                return
            }
            canvas.drawCircle(
                playheadX.toFloat(),
                playheadY.toFloat(),
                playheadRadius.toFloat(),
                scrubberPaint
            )
        } else {
            val scrubberDrawableWidth =
                (scrubberDrawable!!.getIntrinsicWidth() * scrubberScale).toInt()
            val scrubberDrawableHeight =
                (scrubberDrawable!!.getIntrinsicHeight() * scrubberScale).toInt()
            scrubberDrawable!!.setBounds(
                playheadX - scrubberDrawableWidth / 2,
                playheadY - scrubberDrawableHeight / 2,
                playheadX + scrubberDrawableWidth / 2,
                playheadY + scrubberDrawableHeight / 2
            )
            scrubberDrawable!!.draw(canvas)
        }
    }

    private fun updateDrawableState() {
        if (scrubberDrawable != null && scrubberDrawable!!.isStateful()
            && scrubberDrawable!!.setState(drawableState)
        ) {
            invalidate()
        }
    }

    @RequiresApi(29)
    private fun setSystemGestureExclusionRectsV29(width: Int, height: Int) {
        if (lastExclusionRectangle != null && lastExclusionRectangle!!.width() == width && lastExclusionRectangle!!.height() == height) {
            // Allocating inside onLayout is considered a DrawAllocation lint error, so avoid if possible.
            return
        }
        lastExclusionRectangle = Rect( /* left= */0,  /* top= */0, width, height)
        systemGestureExclusionRects = listOf(lastExclusionRectangle!!)
    }

    private val progressText: String
        private get() = Util.getStringForTime(formatBuilder, formatter, position)
    private val positionIncrement: Long
        private get() = if (keyTimeIncrement == C.TIME_UNSET) (if (duration == C.TIME_UNSET) 0 else (duration / keyCountIncrement)) else keyTimeIncrement

    private fun setDrawableLayoutDirection(drawable: Drawable): Boolean {
        return Util.SDK_INT >= 23 && setDrawableLayoutDirection(
            drawable,
            layoutDirection
        )
    }

    companion object {
        /** Default height for the time bar, in dp.  */
        const val DEFAULT_BAR_HEIGHT_DP = 4

        /** Default height for the touch target, in dp.  */
        const val DEFAULT_TOUCH_TARGET_HEIGHT_DP = 26

        /** Default diameter for the scrubber when enabled, in dp.  */
        const val DEFAULT_SCRUBBER_ENABLED_SIZE_DP = 12

        /** Default diameter for the scrubber when disabled, in dp.  */
        const val DEFAULT_SCRUBBER_DISABLED_SIZE_DP = 0

        /** Default diameter for the scrubber when dragged, in dp.  */
        const val DEFAULT_SCRUBBER_DRAGGED_SIZE_DP = 16

        private const val SCRUB_BAR_HEIGHT_SCALE = 1.85f
        private const val SCRUB_BAR_HEIGHT_ANIMATION_MS = 150L
        private const val SCRUB_BAR_CORNER_RADIUS_DP = 2

//        /** Default color for the played portion of the time bar.  */
//        const val DEFAULT_PLAYED_COLOR = -0x1
//
//        /** Default color for the unplayed portion of the time bar.  */
//        const val DEFAULT_UNPLAYED_COLOR = 0x33FFFFFF
//
//        /** Default color for the buffered portion of the time bar.  */
//        const val DEFAULT_BUFFERED_COLOR = -0x33000001
//
//        /** Default color for the scrubber handle.  */
//        const val DEFAULT_SCRUBBER_COLOR = -0x1

        /** Default color for ad markers.  */
        const val DEFAULT_AD_MARKER_COLOR = -0x4d000100

        /** Default color for played ad markers.  */
        const val DEFAULT_PLAYED_AD_MARKER_COLOR = 0x33FFFF00

        /** Vertical gravity for progress bar to be located at the center in the view.  */
        const val BAR_GRAVITY_CENTER = 0

        /** Vertical gravity for progress bar to be located at the bottom in the view.  */
        const val BAR_GRAVITY_BOTTOM = 1

        /** The threshold in dps above the bar at which touch events trigger fine scrub mode.  */
        private const val FINE_SCRUB_Y_THRESHOLD_DP = -50

        /** The ratio by which times are reduced in fine scrub mode.  */
        private const val FINE_SCRUB_RATIO = 3

        /**
         * The time after which the scrubbing listener is notified that scrubbing has stopped after
         * performing an incremental scrub using key input.
         */
        private const val STOP_SCRUBBING_TIMEOUT_MS: Long = 1000
        private const val DEFAULT_INCREMENT_COUNT = 20
        private const val SHOWN_SCRUBBER_SCALE = 1.0f
        private const val HIDDEN_SCRUBBER_SCALE = 0.0f

        /**
         * The name of the Android SDK view that most closely resembles this custom view. Used as the
         * class name for accessibility.
         */
        private const val ACCESSIBILITY_CLASS_NAME = "android.widget.SeekBar"
        private fun setDrawableLayoutDirection(drawable: Drawable, layoutDirection: Int): Boolean {
            return Util.SDK_INT >= 23 && drawable.setLayoutDirection(layoutDirection)
        }

        private fun dpToPx(density: Float, dps: Int): Int {
            return (dps * density + 0.5f).toInt()
        }

        private fun pxToDp(density: Float, px: Int): Int {
            return (px / density).toInt()
        }
    }
}