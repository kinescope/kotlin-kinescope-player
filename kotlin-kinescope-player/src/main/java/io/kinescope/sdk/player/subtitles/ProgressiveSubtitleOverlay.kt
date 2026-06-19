package io.kinescope.sdk.player.subtitles

import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
    private var lastRenderedTopLine = ""
    private var lastRenderedBottomLine = ""
    private var lastContainerContentWidthPx = 0
    private var isLineTransitionAnimating = false
    private var pendingWordCount = 0
    private var onEnsureUpdatesRunning: (() -> Unit)? = null
    private var displayedCueStartUs = 0L
    private var styledTextSizePx = 0f
    private var styledTypeface: Typeface = Typeface.DEFAULT
    private var textMaxWidthPx = 0
    private var lockedTextMaxWidthPx = 0

    private val measurePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
    private val fadeInterpolator = DecelerateInterpolator()

    init {
        container.layoutTransition = null
        linesContainer.layoutTransition = null
        linesContainer.orientation = LinearLayout.VERTICAL
        linesContainer.gravity = Gravity.START
        topView.gravity = Gravity.START
        bottomView.gravity = Gravity.START
        topView.ellipsize = null
        bottomView.ellipsize = null
    }

    fun applyStyle(style: SubtitleStyle, textSizePx: Float, bottomPaddingPx: Int) {
        val roboto = ResourcesCompat.getFont(container.context, R.font.roboto_regular)
        val textSizeChanged = styledTextSizePx <= 0f ||
            kotlin.math.abs(textSizePx - styledTextSizePx) > TEXT_SIZE_CHANGE_EPSILON_PX

        styledTextSizePx = textSizePx
        styledTypeface = roboto ?: Typeface.DEFAULT

        applyTextStyle(topView, style, textSizePx)
        applyTextStyle(bottomView, style, textSizePx)

        if (textSizeChanged) {
            lockedTextMaxWidthPx = 0
        }

        (container.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            val startMarginPx = container.resources.getDimensionPixelSize(
                R.dimen.kinescope_caption_margin_start,
            )
            params.width = FrameLayout.LayoutParams.WRAP_CONTENT
            params.height = FrameLayout.LayoutParams.WRAP_CONTENT
            params.bottomMargin = bottomPaddingPx
            params.marginStart = startMarginPx
            params.leftMargin = startMarginPx
            params.gravity = Gravity.BOTTOM or Gravity.START
            container.layoutParams = params
        }

        if (textSizeChanged && lastVisibleWordCount > 0 && isLayoutReady()) {
            updateTextMaxWidth()
            relayoutFromWordCount(lastVisibleWordCount)
            applyCachedLayout()
        }
    }

    fun update(state: ProgressiveSubtitleState, cueStartUs: Long = 0L) {
        if (shouldResetForCue(cueStartUs)) {
            resetForNewCue(state, cueStartUs)
        } else {
            syncCueWords(state.words)
        }

        val targetCount = state.visibleWordCount.coerceIn(0, cueWords.size)
        if (targetCount <= lastVisibleWordCount) {
            return
        }

        if (isLineTransitionAnimating) {
            pendingWordCount = pendingWordCount.coerceAtLeast(targetCount)
            return
        }

        showWord(lastVisibleWordCount + 1)
    }

    fun setOnEnsureUpdatesRunning(listener: (() -> Unit)?) {
        onEnsureUpdatesRunning = listener
    }

    fun ensureWordPumpRunning() {
        onEnsureUpdatesRunning?.invoke()
    }

    fun setAdvancementEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    fun clear() {
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

    fun isAnimating(): Boolean = isLineTransitionAnimating

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
        lastRenderedTopLine = ""
        lastRenderedBottomLine = ""
        lastContainerContentWidthPx = 0
        textMaxWidthPx = 0
        lockedTextMaxWidthPx = 0
        displayedCueStartUs = 0L
        pendingWordCount = 0
        isLineTransitionAnimating = false
    }

    private fun resetViewState() {
        cancelLineTransitionAnimation()
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

        if (shouldAnimateLineRollUp()) {
            animateLineRollUp()
            return
        }

        applyContainerSize(hasTop, hasBottom)
        applyLineViews(hasTop, hasBottom)
        lastRenderedTopLine = if (hasTop) cachedTopLine else ""
        lastRenderedBottomLine = if (hasBottom) cachedBottomLine else ""
    }

    private fun shouldAnimateLineRollUp(): Boolean {
        if (isLineTransitionAnimating || lastRenderedBottomLine.isBlank()) {
            return false
        }
        val hasTop = cachedTopLine.isNotBlank()
        val hasBottom = cachedBottomLine.isNotBlank()
        if (!hasTop || !hasBottom) {
            return false
        }
        return cachedTopLine != lastRenderedTopLine &&
            cachedBottomLine != lastRenderedBottomLine &&
            !lastRenderedBottomLine.startsWith(cachedBottomLine)
    }

    private fun animateLineRollUp() {
        topView.animate().cancel()
        bottomView.animate().cancel()
        topView.translationY = 0f
        bottomView.translationY = 0f
        topView.alpha = 1f
        bottomView.alpha = 1f
        isLineTransitionAnimating = true

        applyContainerSize(hasTop = true, hasBottom = true)
        bottomView.text = lastRenderedBottomLine
        bottomView.visibility = View.VISIBLE

        val shiftPx = measureLineHeightPx().toFloat()
        val shouldFadeOldTop = lastRenderedTopLine.isNotBlank() &&
            topView.visibility == View.VISIBLE &&
            cachedTopLine != lastRenderedTopLine

        val startSlide = {
            reserveTopLineSlot()
            bottomView.translationY = 0f
            bottomView.animate()
                .translationY(-shiftPx)
                .setDuration(ROLL_UP_MS)
                .setInterpolator(fadeInterpolator)
                .withEndAction { commitLineRollUp() }
                .start()
        }

        if (shouldFadeOldTop) {
            topView.animate()
                .alpha(0f)
                .setDuration(TOP_LINE_FADE_MS)
                .setInterpolator(fadeInterpolator)
                .withEndAction {
                    topView.alpha = 1f
                    startSlide()
                }
                .start()
        } else {
            startSlide()
        }
    }

    private fun commitLineRollUp() {
        topView.animate().cancel()
        bottomView.animate().cancel()

        releaseTopLineSlot()
        bottomView.visibility = View.INVISIBLE
        bottomView.translationY = 0f
        applyLineViews(hasTop = true, hasBottom = true)
        bottomView.visibility = View.VISIBLE

        isLineTransitionAnimating = false
        lastRenderedTopLine = cachedTopLine
        lastRenderedBottomLine = cachedBottomLine

        val pending = pendingWordCount
        pendingWordCount = 0
        if (pending > lastVisibleWordCount) {
            showWord(lastVisibleWordCount + 1)
        }
    }

    private fun reserveTopLineSlot() {
        val params = topView.layoutParams as LinearLayout.LayoutParams
        params.height = measureLineHeightPx()
        params.width = lastContainerContentWidthPx.coerceAtLeast(1)
        topView.layoutParams = params
        topView.text = ""
        topView.visibility = View.INVISIBLE
        topView.alpha = 0f
        topView.translationY = 0f
        linesContainer.requestLayout()
    }

    private fun releaseTopLineSlot() {
        val params = topView.layoutParams as LinearLayout.LayoutParams
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT
        params.width = LinearLayout.LayoutParams.WRAP_CONTENT
        topView.layoutParams = params
    }

    private fun cancelLineTransitionAnimation() {
        topView.animate().cancel()
        bottomView.animate().cancel()
        topView.translationY = 0f
        bottomView.translationY = 0f
        topView.alpha = 1f
        bottomView.alpha = 1f
        isLineTransitionAnimating = false
        pendingWordCount = 0
    }

    private fun applyLineViews(hasTop: Boolean, hasBottom: Boolean) {
        if (hasTop) {
            topView.maxWidth = textMaxWidthPx
            topView.ellipsize = null
            topView.text = cachedTopLine
            topView.visibility = View.VISIBLE
            topView.alpha = 1f
            topView.translationY = 0f
        } else {
            topView.text = ""
            topView.visibility = View.GONE
        }

        applyBottomLine(hasBottom)
    }

    private fun applyBottomLine(hasBottom: Boolean) {
        bottomView.maxWidth = textMaxWidthPx
        bottomView.ellipsize = null
        if (hasBottom) {
            bottomView.text = cachedBottomLine
            bottomView.visibility = View.VISIBLE
        } else {
            bottomView.text = ""
            bottomView.visibility = View.GONE
        }
    }

    private fun applyContainerSize(hasTop: Boolean, hasBottom: Boolean) {
        val topWidth = if (hasTop) measureLineWidth(cachedTopLine) else 0
        val bottomWidth = if (hasBottom) measureLineWidth(cachedBottomLine) else 0
        val contentWidth = maxOf(topWidth, bottomWidth, 1)
        lastContainerContentWidthPx = contentWidth

        if (hasTop) {
            setLineViewWidth(topView, contentWidth)
        }
        if (hasBottom) {
            setLineViewWidth(bottomView, contentWidth)
        }

        val paddingHorizontal = linesContainer.paddingLeft + linesContainer.paddingRight
        val paddingVertical = linesContainer.paddingTop + linesContainer.paddingBottom
        val lineHeight = measureLineHeightPx()
        val lineCount = (if (hasTop) 1 else 0) + (if (hasBottom) 1 else 0)

        val linesParams = linesContainer.layoutParams
        linesParams.width = contentWidth + paddingHorizontal
        linesParams.height = lineCount * lineHeight + paddingVertical
        linesContainer.layoutParams = linesParams

        syncLayoutNow()
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

    private fun setLineViewWidth(view: TextView, widthPx: Int) {
        val params = view.layoutParams as LinearLayout.LayoutParams
        if (params.width != widthPx) {
            params.width = widthPx
            view.layoutParams = params
        }
    }

    private fun syncTextMetricsBeforeLayout() {
        topView.typeface = styledTypeface
        bottomView.typeface = styledTypeface
        topView.setTextSize(TypedValue.COMPLEX_UNIT_PX, styledTextSizePx)
        bottomView.setTextSize(TypedValue.COMPLEX_UNIT_PX, styledTextSizePx)
    }

    private fun updateTextMaxWidth() {
        val resolved = resolveTextMaxWidth()
        if (resolved <= 0) {
            return
        }
        if (lockedTextMaxWidthPx <= 0) {
            lockedTextMaxWidthPx = resolved
        }
        textMaxWidthPx = lockedTextMaxWidthPx
    }

    private fun resolveTextMaxWidth(): Int {
        val parent = container.parent as? View ?: return 0
        if (parent.width <= 0) {
            return 0
        }

        val layoutParams = container.layoutParams as? FrameLayout.LayoutParams
        val startMarginPx = layoutParams?.marginStart ?: layoutParams?.leftMargin ?: 0
        val endMarginPx = layoutParams?.marginEnd ?: layoutParams?.rightMargin ?: 0
        val maxBlockWidth = parent.width - startMarginPx - endMarginPx
        val paddingHorizontal = linesContainer.paddingLeft + linesContainer.paddingRight
        return (maxBlockWidth - paddingHorizontal).coerceAtLeast(1)
    }

    private fun isLayoutReady(): Boolean {
        val parent = container.parent as? View
        return (parent?.width ?: 0) > 0
    }

    private companion object {
        private const val CAPTION_LINE_HEIGHT_MULTIPLIER = 28f / 24f
        private const val TOP_LINE_FADE_MS = 280L
        private const val ROLL_UP_MS = 280L
        private const val TEXT_SIZE_CHANGE_EPSILON_PX = 0.5f
    }
}
