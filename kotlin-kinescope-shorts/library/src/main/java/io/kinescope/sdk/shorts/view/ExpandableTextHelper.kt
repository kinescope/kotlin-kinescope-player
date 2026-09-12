package io.kinescope.sdk.shorts.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import kotlin.math.abs

/**
 * Collapsed (1 line + ellipsis) ↔ expanded with a height animation.
 *
 * Always consumes taps so they do not fall through to the scrub hit-area
 * (which would show the timeline and look like the title “jumped” up).
 * Expand runs only when the collapsed text is actually ellipsized.
 */
class ExpandableTextHelper(
    private val textView: TextView,
    private val collapsedMaxLines: Int = 1,
    private val expandedMaxLines: Int = Int.MAX_VALUE,
    private val durationMs: Long = 250L,
) {
    private var expanded = false
    private var animator: ValueAnimator? = null
    private var canExpand = false

    init {
        textView.maxLines = collapsedMaxLines
        textView.ellipsize = TextUtils.TruncateAt.END
        // Always consume clicks; expand only when truncated.
        textView.isClickable = true
        textView.isFocusable = true
        textView.setOnClickListener {
            if (!expanded && !canExpand) return@setOnClickListener
            if (!expanded && !hasCollapsedEllipsis()) {
                canExpand = false
                return@setOnClickListener
            }
            setExpanded(!expanded, animate = true)
        }
    }

    fun bind(text: CharSequence?) {
        cancelAnimation()
        expanded = false
        textView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        textView.maxLines = collapsedMaxLines
        textView.ellipsize = TextUtils.TruncateAt.END
        textView.text = text?.trimEnd()
        canExpand = false
        textView.isClickable = true
        textView.isFocusable = true
        textView.post { refreshExpandability() }
    }

    fun collapse(animate: Boolean = false) {
        if (!expanded) {
            refreshExpandability()
            return
        }
        setExpanded(false, animate)
    }

    fun onDetached() {
        cancelAnimation()
    }

    private fun refreshExpandability() {
        canExpand = expanded || hasCollapsedEllipsis()
    }

    private fun hasCollapsedEllipsis(): Boolean {
        if (textView.text.isNullOrEmpty()) return false
        if (textView.width <= 0) {
            textView.post { refreshExpandability() }
            return false
        }
        val layout = textView.layout ?: return false
        if (textView.lineCount <= 0) return false
        if (textView.lineCount > collapsedMaxLines) return true
        for (i in 0 until textView.lineCount) {
            if (layout.getEllipsisCount(i) > 0) return true
        }
        return false
    }

    private fun setExpanded(expand: Boolean, animate: Boolean) {
        if (expand == expanded && animator == null) return

        if (expand && !hasCollapsedEllipsis()) {
            canExpand = false
            return
        }

        cancelAnimation()

        val startHeight = textView.height.coerceAtLeast(1)

        textView.maxLines = if (expand) expandedMaxLines else collapsedMaxLines
        textView.ellipsize = if (expand) null else TextUtils.TruncateAt.END

        textView.measure(
            View.MeasureSpec.makeMeasureSpec(textView.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val endHeight = textView.measuredHeight.coerceAtLeast(1)
        val minDelta = textView.textSize.toInt().coerceAtLeast(8)
        if (expand && abs(endHeight - startHeight) < minDelta) {
            textView.maxLines = collapsedMaxLines
            textView.ellipsize = TextUtils.TruncateAt.END
            textView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            textView.requestLayout()
            expanded = false
            canExpand = false
            return
        }

        expanded = expand

        if (!animate || abs(endHeight - startHeight) < 2) {
            textView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            textView.requestLayout()
            refreshExpandability()
            return
        }

        textView.layoutParams.height = startHeight
        textView.requestLayout()

        animator = ValueAnimator.ofInt(startHeight, endHeight).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                textView.layoutParams.height = anim.animatedValue as Int
                textView.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    textView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    textView.requestLayout()
                    animator = null
                    refreshExpandability()
                }

                override fun onAnimationCancel(animation: Animator) {
                    animator = null
                }
            })
            start()
        }
    }

    private fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }
}
