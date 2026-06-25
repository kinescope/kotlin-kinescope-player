package io.kinescope.sdk.player.subtitles

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import io.kinescope.sdk.R
import io.kinescope.sdk.settings.SubtitleStyle

internal class ProgressiveSubtitleOverlay(
    private val container: FrameLayout,
    private val linesContainer: LinearLayout,
    private val topView: TextView,
    private val bottomView: TextView,
) {
    private var lastVisibleWordCount = 0
    private var cueWords: List<String> = emptyList()
    private var cachedTopLine = ""
    private var cachedBottomLine = ""
    private var onEnsureUpdatesRunning: (() -> Unit)? = null
    private var displayedCueStartUs = 0L
    private var styledTextSizePx = 0f
    private var styledTypeface: Typeface = Typeface.DEFAULT
    private var textMaxWidthPx = 0
    private var lastResolvedParentWidthPx = 0
    private var lastBottomPaddingPx = -1
    private var bottomMarginAnimator: ValueAnimator? = null
    private var appliedStyle = SubtitleStyle()

    private val measurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    init {
        container.layoutTransition = null
        linesContainer.layoutTransition = null
        linesContainer.orientation = LinearLayout.VERTICAL
        applyTextAlignment(hasTop = false, hasBottom = false)
        topView.ellipsize = null
        bottomView.ellipsize = null
    }

    fun applyStyle(style: SubtitleStyle, textSizePx: Float, bottomPaddingPx: Int) {
        appliedStyle = style
        val roboto = ResourcesCompat.getFont(container.context, R.font.roboto_regular)
        val textSizeChanged = styledTextSizePx <= 0f ||
            kotlin.math.abs(textSizePx - styledTextSizePx) > TEXT_SIZE_CHANGE_EPSILON_PX

        styledTextSizePx = textSizePx
        styledTypeface = roboto ?: Typeface.DEFAULT

        linesContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(captionBackgroundColor(style))
        }

        applyTextStyle(topView, style, textSizePx)
        applyTextStyle(bottomView, style, textSizePx)

        if (textSizeChanged) {
            textMaxWidthPx = 0
            lastResolvedParentWidthPx = 0
        }

        val resources = container.resources
        val startMarginPx = resources.getDimensionPixelSize(R.dimen.kinescope_caption_margin_start)
        val endMarginPx = resources.getDimensionPixelSize(R.dimen.kinescope_caption_margin_end)

        (container.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = FrameLayout.LayoutParams.WRAP_CONTENT
            params.height = FrameLayout.LayoutParams.WRAP_CONTENT
            params.marginStart = startMarginPx
            params.leftMargin = startMarginPx
            params.marginEnd = endMarginPx
            params.rightMargin = endMarginPx
            params.gravity = Gravity.BOTTOM or Gravity.START
            container.layoutParams = params
        }

        val animateBottomMargin = lastBottomPaddingPx >= 0 && lastBottomPaddingPx != bottomPaddingPx
        lastBottomPaddingPx = bottomPaddingPx
        setBottomMargin(bottomPaddingPx, animate = animateBottomMargin)

        if (textSizeChanged && lastVisibleWordCount > 0 && isLayoutReady()) {
            updateTextMaxWidth()
            relayoutFromWordCount(lastVisibleWordCount)
            applyCachedLayout()
        } else if (hasVisibleContent()) {
            applyCachedLayout()
        }
    }

    private fun captionBackgroundColor(style: SubtitleStyle): Int {
        return (style.bgColor and 0x00FFFFFF) or
            ((style.bgOpacityPercent * 255 / 100) shl 24)
    }

    fun update(state: ProgressiveSubtitleState, cueStartUs: Long = 0L) {
        if (shouldResetForCue(cueStartUs)) {
            resetForNewCue(state, cueStartUs)
        } else {
            syncCueWords(state.words)
        }

        val rawTargetCount = state.visibleWordCount.coerceIn(0, cueWords.size)
        val targetCount = if (textMaxWidthPx > 0) {
            ProgressiveSubtitleLineLayout.snapToLineRevealWordCount(
                rawWordCount = rawTargetCount,
                words = cueWords,
                lineFits = ::lineFits,
            )
        } else {
            rawTargetCount
        }
        if (targetCount <= lastVisibleWordCount) {
            return
        }

        showWord(targetCount)
    }

    fun setOnEnsureUpdatesRunning(listener: (() -> Unit)?) {
        onEnsureUpdatesRunning = listener
    }

    fun ensureWordPumpRunning() {
        onEnsureUpdatesRunning?.invoke()
    }

    fun setAdvancementEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    fun clear() {
        bottomMarginAnimator?.cancel()
        bottomMarginAnimator = null
        resetCueState()
        resetViewState()
    }

    fun hasVisibleContent(): Boolean = cachedTopLine.isNotBlank() || cachedBottomLine.isNotBlank()

    fun isDisplayComplete(): Boolean = cueWords.isEmpty() || lastVisibleWordCount >= cueWords.size

    fun shouldKeepVisible(positionUs: Long): Boolean {
        if (!hasVisibleContent()) {
            return false
        }
        if (!isDisplayComplete()) {
            return true
        }
        if (displayedCueStartUs <= 0L || cueWords.isEmpty()) {
            return false
        }
        return positionUs <= ProgressiveSubtitleCues.cueVisibleUntilUs(
            cueStartUs = displayedCueStartUs,
            wordCount = cueWords.size,
        )
    }

    fun isAnimating(): Boolean = bottomMarginAnimator?.isRunning == true

    private fun resetForNewCue(state: ProgressiveSubtitleState, cueStartUs: Long) {
        resetCueState()
        cueWords = state.words
        displayedCueStartUs = cueStartUs
        resetViewState()
    }

    private fun resetCueState() {
        lastVisibleWordCount = 0
        cueWords = emptyList()
        cachedTopLine = ""
        cachedBottomLine = ""
        textMaxWidthPx = 0
        lastResolvedParentWidthPx = 0
        displayedCueStartUs = 0L
    }

    private fun resetViewState() {
        topView.text = ""
        bottomView.text = ""
        topView.visibility = View.GONE
        bottomView.visibility = View.GONE
        linesContainer.visibility = View.GONE
        container.visibility = View.GONE
    }

    private fun showWord(count: Int) {
        if (!isLayoutReady() || styledTextSizePx <= 0f) {
            container.post { showWord(count) }
            return
        }

        updateTextMaxWidth()
        if (textMaxWidthPx <= 0) {
            container.post { showWord(count) }
            return
        }

        relayoutFromWordCount(count)
        applyCachedLayout()
        lastVisibleWordCount = count
    }

    private fun relayoutFromWordCount(count: Int) {
        val (topLine, bottomLine) = ProgressiveSubtitleLineLayout.buildLines(
            words = cueWords,
            visibleCount = count,
            lineFits = ::lineFits,
        )
        cachedTopLine = topLine
        cachedBottomLine = bottomLine
    }

    private fun applyCachedLayout() {
        val hasTop = cachedTopLine.isNotBlank()
        val hasBottom = cachedBottomLine.isNotBlank()
        if (!hasTop && !hasBottom) {
            resetViewState()
            return
        }

        linesContainer.visibility = View.VISIBLE
        container.visibility = View.VISIBLE

        applyTextAlignment(hasTop, hasBottom)
        applyContainerSize(hasTop, hasBottom)
        applyLineViews(hasTop, hasBottom)
    }

    private fun applyTextAlignment(hasTop: Boolean, hasBottom: Boolean) {
        val twoLines = hasTop && hasBottom
        val lineGravity = if (twoLines) Gravity.CENTER_HORIZONTAL else Gravity.START
        val textAlignment = if (twoLines) {
            View.TEXT_ALIGNMENT_CENTER
        } else {
            View.TEXT_ALIGNMENT_VIEW_START
        }
        linesContainer.gravity = lineGravity
        topView.gravity = lineGravity
        bottomView.gravity = lineGravity
        topView.textAlignment = textAlignment
        bottomView.textAlignment = textAlignment
    }

    private fun applyLineViews(hasTop: Boolean, hasBottom: Boolean) {
        if (hasTop) {
            topView.maxWidth = textMaxWidthPx
            topView.ellipsize = null
            topView.text = cachedTopLine
            applyTextStyle(topView, appliedStyle, styledTextSizePx)
            topView.visibility = View.VISIBLE
        } else {
            topView.text = ""
            topView.visibility = View.GONE
        }

        bottomView.maxWidth = textMaxWidthPx
        bottomView.ellipsize = null
        if (hasBottom) {
            bottomView.text = cachedBottomLine
            applyTextStyle(bottomView, appliedStyle, styledTextSizePx)
            bottomView.visibility = View.VISIBLE
        } else {
            bottomView.text = ""
            bottomView.visibility = View.GONE
        }
    }

    private fun applyContainerSize(hasTop: Boolean, hasBottom: Boolean) {
        val parent = container.parent as? View
        val parentWidth = parent?.width ?: 0
        val backgroundWidthPx = if (parentWidth > 0) {
            resolveBackgroundWidthPx(parentWidth)
        } else {
            0
        }

        val paddingVertical = linesContainer.paddingTop + linesContainer.paddingBottom
        val lineHeight = measureLineHeightPx()
        val lineCount = (if (hasTop) 1 else 0) + (if (hasBottom) 1 else 0)

        val linesParams = linesContainer.layoutParams
        linesParams.width = if (backgroundWidthPx > 0) {
            backgroundWidthPx
        } else {
            val topWidth = if (hasTop) measureLineWidth(cachedTopLine) else 0
            val bottomWidth = if (hasBottom) measureLineWidth(cachedBottomLine) else 0
            val contentWidth = maxOf(topWidth, bottomWidth, 1)
            contentWidth + linesContainer.paddingLeft + linesContainer.paddingRight
        }
        linesParams.height = lineCount * lineHeight + paddingVertical
        linesContainer.layoutParams = linesParams

        if (backgroundWidthPx > 0) {
            (container.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (params.width != backgroundWidthPx) {
                    params.width = backgroundWidthPx
                    container.layoutParams = params
                }
            }
        }

        syncLayoutNow()
    }

    private fun setBottomMargin(targetPx: Int, animate: Boolean) {
        val params = container.layoutParams as? FrameLayout.LayoutParams ?: return
        val currentPx = params.bottomMargin
        if (currentPx == targetPx) {
            return
        }
        bottomMarginAnimator?.cancel()
        if (!animate) {
            params.bottomMargin = targetPx
            container.layoutParams = params
            return
        }
        bottomMarginAnimator = ValueAnimator.ofInt(currentPx, targetPx).apply {
            duration = BOTTOM_MARGIN_ANIM_MS
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                params.bottomMargin = value
                container.layoutParams = params
            }
            start()
        }
    }

    private fun syncLayoutNow() {
        val parent = container.parent as? View ?: return
        if (parent.width <= 0) {
            linesContainer.requestLayout()
            container.requestLayout()
            return
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        container.measure(widthSpec, heightSpec)
        container.layout(
            container.left,
            container.top,
            container.left + container.measuredWidth,
            container.top + container.measuredHeight,
        )
    }

    private fun applyTextStyle(view: TextView, style: SubtitleStyle, textSizePx: Float) {
        view.setTextColor(style.fontColor)
        view.typeface = styledTypeface
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        view.setLineSpacing(0f, CAPTION_LINE_HEIGHT_MULTIPLIER)
    }

    private fun shouldResetForCue(cueStartUs: Long): Boolean {
        if (cueWords.isEmpty()) {
            return true
        }
        if (cueStartUs <= 0L) {
            return false
        }
        return displayedCueStartUs != cueStartUs
    }

    private fun syncCueWords(words: List<String>) {
        if (words.isNotEmpty() && words.size >= cueWords.size) {
            cueWords = words
        }
    }

    private fun measureLineWidth(text: String): Int {
        if (text.isBlank() || textMaxWidthPx <= 0) {
            return 0
        }
        return measurePaintForLine().measureText(text).toInt().coerceIn(1, textMaxWidthPx)
    }

    private fun measureLineHeightPx(): Int {
        val fontMetrics = measurePaintForLine().fontMetrics
        return (fontMetrics.descent - fontMetrics.ascent).toInt()
            .coerceAtLeast((styledTextSizePx * CAPTION_LINE_HEIGHT_MULTIPLIER).toInt())
    }

    private fun lineFits(text: String): Boolean {
        if (text.isEmpty() || textMaxWidthPx <= 0) {
            return text.isEmpty()
        }
        return measurePaintForLine().measureText(text) <= textMaxWidthPx
    }

    private fun measurePaintForLine(): TextPaint {
        syncTextMetricsBeforeLayout()
        measurePaint.set(bottomView.paint)
        measurePaint.typeface = styledTypeface
        measurePaint.textSize = styledTextSizePx
        measurePaint.letterSpacing = bottomView.letterSpacing
        measurePaint.isAntiAlias = true
        return measurePaint
    }

    private fun syncTextMetricsBeforeLayout() {
        topView.typeface = styledTypeface
        bottomView.typeface = styledTypeface
        topView.setTextSize(TypedValue.COMPLEX_UNIT_PX, styledTextSizePx)
        bottomView.setTextSize(TypedValue.COMPLEX_UNIT_PX, styledTextSizePx)
    }

    private fun updateTextMaxWidth() {
        val parent = container.parent as? View ?: return
        val parentWidth = parent.width
        if (parentWidth <= 0) {
            return
        }
        if (parentWidth != lastResolvedParentWidthPx) {
            lastResolvedParentWidthPx = parentWidth
            textMaxWidthPx = 0
        }
        if (textMaxWidthPx <= 0) {
            textMaxWidthPx = resolveTextMaxWidth(parentWidth)
        }
    }

    private fun resolveBackgroundWidthPx(parentWidthPx: Int): Int {
        val layoutParams = container.layoutParams as? FrameLayout.LayoutParams
        val startMarginPx = layoutParams?.marginStart ?: layoutParams?.leftMargin ?: 0
        val endMarginPx = layoutParams?.marginEnd ?: layoutParams?.rightMargin ?: 0
        return (parentWidthPx - startMarginPx - endMarginPx).coerceAtLeast(1)
    }

    private fun resolveTextMaxWidth(parentWidthPx: Int): Int {
        val paddingHorizontal = linesContainer.paddingLeft + linesContainer.paddingRight
        return (resolveBackgroundWidthPx(parentWidthPx) - paddingHorizontal).coerceAtLeast(1)
    }

    private fun isLayoutReady(): Boolean {
        val parent = container.parent as? View
        return (parent?.width ?: 0) > 0
    }

    private companion object {
        private const val CAPTION_LINE_HEIGHT_MULTIPLIER = 28f / 24f
        private const val TEXT_SIZE_CHANGE_EPSILON_PX = 0.5f
        private const val BOTTOM_MARGIN_ANIM_MS = 200L
    }
}
