package io.kinescope.sdk.chapters



import android.content.Context

import android.graphics.Rect

import android.util.AttributeSet

import android.view.Gravity

import android.view.LayoutInflater

import android.view.MotionEvent

import android.view.View

import android.view.ViewGroup

import android.view.View.MeasureSpec

import android.widget.FrameLayout

import android.widget.LinearLayout

import androidx.core.view.ViewCompat

import androidx.core.view.isVisible

import io.kinescope.sdk.R

import io.kinescope.sdk.databinding.ViewChaptersBinding

import io.kinescope.sdk.models.videos.KinescopeVideoChapterItem

import io.kinescope.sdk.models.videos.startTimeMs

import io.kinescope.sdk.utils.formatPlayerTime

import java.lang.ref.WeakReference



class KinescopeChaptersView(

    context: Context,

    attributes: AttributeSet? = null,

) : FrameLayout(context, attributes) {



    private val binding = ViewChaptersBinding.inflate(LayoutInflater.from(context), this, true)

    private var anchorView: WeakReference<View>? = null

    private var isFullscreenMode = false

    private var isHidingPopup = false

    private var chapters: List<KinescopeVideoChapterItem> = emptyList()



    var onChapterSelected: ((KinescopeVideoChapterItem) -> Unit)? = null



    init {

        clipChildren = true

        clipToPadding = true

        isVisible = false

        binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)

    }



    fun setAnchorView(view: View?) {

        anchorView = view?.let { WeakReference(it) }

    }



    fun setFullscreenMode(fullscreen: Boolean) {

        if (isFullscreenMode == fullscreen) {

            return

        }

        isFullscreenMode = fullscreen

        if (binding.chaptersPopupContainer.isVisible) {

            positionPopupWithinBounds()

        }

    }



    fun setChapters(items: List<KinescopeVideoChapterItem>) {

        chapters = items

    }



    fun show() {

        if (chapters.isEmpty()) {

            return

        }

        isHidingPopup = false

        isVisible = true

        binding.chaptersPopupContainer.isVisible = true

        binding.chaptersPopupContainer.alpha = 1f

        post { showPopupWhenReady() }

    }



    private fun showPopupWhenReady() {

        if (!isVisible) {

            return

        }

        if (width == 0 || height == 0) {

            post { showPopupWhenReady() }

            return

        }

        rebuildList()

        positionPopupWithinBounds()

    }



    fun dismiss() {

        hide(animated = true)

    }



    fun isShowing(): Boolean = isVisible && binding.chaptersPopupContainer.isVisible



    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        if (!isVisible) {

            return super.dispatchTouchEvent(event)

        }

        if (event.action == MotionEvent.ACTION_DOWN && binding.chaptersPopupContainer.isVisible) {

            val popupRect = Rect().also { binding.chaptersPopupContainer.getGlobalVisibleRect(it) }

            val hitPopup = popupRect.width() > 0 &&

                popupRect.height() > 0 &&

                popupRect.contains(event.rawX.toInt(), event.rawY.toInt())

            if (!hitPopup) {

                hide(animated = true)

                return true

            }

        }

        return super.dispatchTouchEvent(event)

    }



    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {

        super.onLayout(changed, left, top, right, bottom)

        if (binding.chaptersPopupContainer.isVisible) {

            positionPopupWithinBounds()

        }

    }



    private fun hide(animated: Boolean) {

        if (!isVisible || isHidingPopup) {

            return

        }

        if (!animated || !binding.chaptersPopupContainer.isVisible) {

            finishHide()

            return

        }

        isHidingPopup = true

        binding.chaptersPopupContainer.animate()

            .alpha(0f)

            .setDuration(POPUP_ANIMATION_DURATION_MS)

            .withEndAction { finishHide() }

            .start()

    }



    private fun finishHide() {

        isHidingPopup = false

        binding.chaptersPopupContainer.animate().cancel()

        binding.chaptersPopupContainer.isVisible = false

        binding.chaptersPopupContainer.alpha = 1f

        resetPopupLayoutParams()

        isVisible = false

    }



    private fun resetPopupLayoutParams() {

        val popup = binding.chaptersPopupContainer

        val layoutParams = (popup.layoutParams as? LayoutParams)

            ?: LayoutParams(

                ViewGroup.LayoutParams.WRAP_CONTENT,

                ViewGroup.LayoutParams.WRAP_CONTENT,

            )

        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT

        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT

        layoutParams.gravity = Gravity.NO_GRAVITY

        layoutParams.setMargins(0, 0, 0, 0)

        popup.layoutParams = layoutParams

    }



    private fun rebuildList() {

        val list = binding.chaptersList

        list.removeAllViews()

        val rowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)

        chapters.forEach { chapter ->

            list.addView(

                KinescopeChapterRowView(context).apply {

                    layoutParams = LinearLayout.LayoutParams(

                        ViewGroup.LayoutParams.MATCH_PARENT,

                        rowHeight,

                    )

                    setTitle(chapter.title)

                    setTime(formatPlayerTime(chapter.startTimeMs()))

                    setOnClickListener {

                        onChapterSelected?.invoke(chapter)

                        hide(animated = true)

                    }

                },

            )

        }

        updateScrollHeight()

    }



    private fun updateScrollHeight() {

        val rowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)

        val optionsContentHeight = chapters.size.coerceAtLeast(1) * rowHeight

        val hardMax = resources.getDimensionPixelSize(R.dimen.kinescope_settings_options_max_height)

        val maxPopupHeight = calculateMaxPopupHeight().takeIf { it > 0 } ?: hardMax

        val verticalPadding = binding.chaptersPopupContainer.paddingTop +

            binding.chaptersPopupContainer.paddingBottom

        val headerHeight = rowHeight

        val availableInPopup = (maxPopupHeight - verticalPadding - headerHeight).coerceAtLeast(rowHeight)

        val scrollHeight = optionsContentHeight.coerceAtMost(hardMax).coerceAtMost(availableInPopup)

        binding.chaptersScrollView.layoutParams = binding.chaptersScrollView.layoutParams.apply {

            height = scrollHeight

        }

    }



    private fun calculateMaxPopupHeight(): Int {

        val endMargin = resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)

        val bottomOffset =

            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_bottom_offset)

        val hardMax = resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_max_height)

        if (height <= 0) {

            return hardMax

        }

        return (height - bottomOffset - endMargin).coerceAtMost(hardMax).coerceAtLeast(0)

    }



    private fun positionPopupWithinBounds() {

        if (width == 0 || height == 0) {

            return

        }

        val popup = binding.chaptersPopupContainer

        val popupWidth = popupWidthPx()

        if (popupWidth == 0) {

            return

        }



        updateScrollHeight()



        val widthSpec = MeasureSpec.makeMeasureSpec(popupWidth, MeasureSpec.EXACTLY)

        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        popup.measure(widthSpec, heightSpec)



        val verticalPadding = popup.paddingTop + popup.paddingBottom

        val measuredHeight = popup.measuredHeight.coerceAtLeast(0)

        val maxHeight = calculateMaxPopupHeight()

            .coerceAtLeast(resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height) + verticalPadding)

        val popupHeight = measuredHeight.coerceAtMost(maxHeight)



        val endMargin = resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)

        val bottomOffset =

            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_bottom_offset)

        val topMargin =

            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)

        val isRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL

        val left = if (isRtl) {

            endMargin

        } else {

            width - popupWidth - endMargin

        }

        val top = (height - popupHeight - bottomOffset).coerceAtLeast(topMargin)



        val layoutParams = (popup.layoutParams as? LayoutParams)

            ?: LayoutParams(popupWidth, popupHeight)

        layoutParams.width = popupWidth

        layoutParams.height = popupHeight

        layoutParams.gravity = Gravity.NO_GRAVITY

        layoutParams.setMargins(left, top, 0, 0)

        popup.layoutParams = layoutParams

    }



    private fun popupWidthPx(): Int {

        val endMargin = resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)

        val desiredWidth = resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_width)

        val maxWidth = (width - endMargin * 2).coerceAtLeast(0)

        return desiredWidth.coerceAtMost(maxWidth)

    }



    private companion object {

        private const val POPUP_ANIMATION_DURATION_MS = 150L

    }

}


