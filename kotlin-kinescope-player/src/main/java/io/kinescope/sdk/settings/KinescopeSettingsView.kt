package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import io.kinescope.sdk.R
import io.kinescope.sdk.databinding.ViewSettingsBinding
import java.lang.IllegalStateException
import java.lang.ref.WeakReference

class KinescopeSettingsView(
    context: Context,
    attributes: AttributeSet? = null,
) : FrameLayout(context, attributes) {

    private val parameters = mutableSetOf<Parameter>()
    private val actionParameters = mutableSetOf<Parameter>()
    private val parameterOptions = mutableMapOf<Parameter, List<KinescopeSettingsOption>>()
    private var expandedParameter: Parameter? = null
    private var anchorView: WeakReference<View>? = null
    private var isFullscreenMode = false
    private var isHidingPopup = false

    private val binding =
        ViewSettingsBinding.inflate(LayoutInflater.from(context), this, true)

    var onOptionSelected: ((parameter: Parameter, optionId: Int) -> Unit)? = null
    var onParameterAction: ((parameter: Parameter) -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        isVisible = false
        binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        binding.parameterOptionsTitleTv.setOnClickListener {
            hideOptions(animated = true)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isVisible) {
            return super.dispatchTouchEvent(event)
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            val popupRect = Rect().also { binding.settingsPopupContainer.getGlobalVisibleRect(it) }
            val hitPopup = binding.settingsPopupContainer.isVisible &&
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
        if (binding.settingsPopupContainer.isVisible) {
            positionPopupWithinBounds()
        }
    }

    fun setAnchorView(view: View?) {
        anchorView = view?.let { WeakReference(it) }
    }

    fun setFullscreenMode(fullscreen: Boolean) {
        if (isFullscreenMode == fullscreen) {
            return
        }
        isFullscreenMode = fullscreen
        if (binding.settingsPopupContainer.isVisible) {
            positionPopupWithinBounds()
        }
    }

    fun addParameter(
        parameter: Parameter,
        title: String,
        @DrawableRes icon: Int,
    ) {
        if (parameters.contains(parameter)) {
            throw IllegalStateException(ERROR_TEXT_PARAMETER_DUPLICATION.format(parameter))
        }

        parameters.add(parameter)
        binding.parametersLl
            .addView(KinescopeSettingsParameterView(context)
                .apply {
                    setTitle(title)
                    setIcon(icon)
                    setExpandable(expandable = !actionParameters.contains(parameter))
                    setOnClickListener {
                        onParameterClick(
                            title = title,
                            parameter = parameter,
                        )
                    }
                    tag = parameter
                })
    }

    fun setParameterAction(parameter: Parameter, isAction: Boolean) {
        checkParameterAddedOrException(parameter)
        if (isAction) {
            actionParameters.add(parameter)
        } else {
            actionParameters.remove(parameter)
        }
        binding.parametersLl.findViewWithTag<KinescopeSettingsParameterView>(parameter)
            ?.setExpandable(expandable = !isAction)
    }

    fun setParameterCurrentValue(parameter: Parameter, value: String) {
        checkParameterAddedOrException(parameter)
        with(binding.parametersLl) {
            findViewWithTag<KinescopeSettingsParameterView>(parameter)
                ?.setCurrentValue(value)
        }
    }

    fun setParameterOptions(parameter: Parameter, options: List<KinescopeSettingsOption>) {
        checkParameterAddedOrException(parameter)
        parameterOptions[parameter] = options
    }

    fun setParameterVisible(parameter: Parameter, visible: Boolean) {
        if (!parameters.contains(parameter)) {
            return
        }
        binding.parametersLl.findViewWithTag<View>(parameter)?.isVisible = visible
    }

    fun applyIconTint(@ColorInt color: Int) {
        binding.parametersLl.children
            .filterIsInstance<KinescopeSettingsParameterView>()
            .forEach { parameterView -> parameterView.applyIconTint(color) }

        binding.parameterOptionsTitleTv.compoundDrawablesRelative.forEach { drawable ->
            drawable?.mutate()?.setTint(color)
        }

        binding.parameterOptionsLl.children
            .filterIsInstance<KinescopeSettingsOptionView>()
            .forEach { optionView -> optionView.applyIconTint(color) }
    }

    fun show() {
        isHidingPopup = false
        expandedParameter = null
        collapseOptions(animated = false)
        isVisible = true
        post { positionPopupWithinBounds() }
        animatePopupIn(binding.settingsPopupContainer)
    }

    fun dismiss() {
        hide(animated = true)
    }

    private fun hide(animated: Boolean = true) {
        if (!isVisible || isHidingPopup) {
            return
        }
        if (!animated || !binding.settingsPopupContainer.isVisible) {
            finishHide()
            return
        }
        isHidingPopup = true
        expandedParameter = null
        collapseOptions(animated = false)
        animatePopupOut(binding.settingsPopupContainer)
    }

    private fun finishHide() {
        isHidingPopup = false
        binding.settingsPopupContainer.animate().cancel()
        binding.settingsPopupContainer.isVisible = false
        binding.settingsPopupContainer.alpha = 1f
        binding.settingsPopupContainer.scaleX = 1f
        binding.settingsPopupContainer.scaleY = 1f
        isVisible = false
    }

    private fun expandOptions() {
        binding.settingsMenuFl.isVisible = false
        binding.settingsSectionsDivider.isVisible = false
        binding.optionsPopupFl.isVisible = true
    }

    private fun collapseOptions(animated: Boolean) {
        expandedParameter = null
        binding.optionsPopupFl.isVisible = false
        binding.settingsSectionsDivider.isVisible = false
        binding.settingsMenuFl.isVisible = true
        if (!animated) {
            return
        }
    }

    private fun hideOptions(animated: Boolean = true) {
        if (!binding.optionsPopupFl.isVisible) {
            return
        }
        collapseOptions(animated = false)
        if (!animated) {
            return
        }
        binding.settingsPopupContainer.animate()
            .scaleX(POPUP_SCALE_COLLAPSED)
            .scaleY(POPUP_SCALE_COLLAPSED)
            .setDuration(POPUP_ANIMATION_DURATION_MS / 2)
            .withEndAction {
                binding.settingsPopupContainer.scaleX = 1f
                binding.settingsPopupContainer.scaleY = 1f
            }
            .start()
    }

    private fun onParameterClick(title: String, parameter: Parameter) {
        if (actionParameters.contains(parameter)) {
            hide(animated = true)
            onParameterAction?.invoke(parameter)
            return
        }

        if (binding.optionsPopupFl.isVisible && expandedParameter == parameter) {
            hideOptions(animated = true)
            return
        }

        expandedParameter = parameter
        bindOptionsContent(title = title, parameter = parameter)

        if (binding.optionsPopupFl.isVisible) {
            return
        }
        expandOptions()
        post { positionPopupWithinBounds() }
    }

    private fun bindOptionsContent(title: String, parameter: Parameter) {
        binding.parameterOptionsTitleTv.text = title
        binding.parameterOptionsLl.removeAllViews()
        parameterOptions[parameter]
            .orEmpty()
            .forEach { option ->
                binding.parameterOptionsLl.addView(
                    KinescopeSettingsOptionView(context).apply {
                        setTitle(option.title)
                        setIsSelected(option.isSelected)
                        setOnClickListener {
                            hide(animated = true)
                            onOptionSelected?.invoke(parameter, option.id)
                        }
                    }
                )
            }
    }

    private fun positionPopupWithinBounds() {
        if (width == 0 || height == 0) {
            return
        }

        val popup = binding.settingsPopupContainer
        val edgeMargin =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        val gapAboveAnchor =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_anchor_gap)
        val desiredWidth =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_width)
        val desiredHeight =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_height)

        val maxWidth = (width - edgeMargin * 2).coerceAtLeast(0)
        var popupWidth = desiredWidth.coerceAtMost(maxWidth)
        if (popupWidth == 0) {
            return
        }

        val isRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
        val anchor = anchorView?.get()?.takeIf { it.isShown && it.width > 0 }

        var left: Int
        var top: Int
        var popupHeight = desiredHeight

        if (anchor != null) {
            val anchorRect = getViewRectRelativeToSelf(anchor)
            val maxHeightAbove =
                (anchorRect.top - gapAboveAnchor - edgeMargin).coerceAtLeast(0)
            val maxHeightInPlayer = (height - edgeMargin * 2).coerceAtLeast(0)
            popupHeight = popupHeight
                .coerceAtMost(maxHeightAbove)
                .coerceAtMost(maxHeightInPlayer)
            left = if (isRtl) {
                anchorRect.left
            } else {
                anchorRect.right - popupWidth
            }
            top = anchorRect.top - popupHeight - gapAboveAnchor
        } else {
            popupHeight = popupHeight.coerceAtMost((height - edgeMargin * 2).coerceAtLeast(0))
            left = if (isRtl) {
                edgeMargin
            } else {
                width - popupWidth - edgeMargin
            }
            top = height - popupHeight - edgeMargin
        }

        if (popupHeight == 0) {
            return
        }

        if (!isFullscreenMode) {
            left += resources.getDimensionPixelSize(
                R.dimen.kinescope_settings_popup_embedded_offset,
            )
        }

        left = left.coerceIn(edgeMargin, (width - popupWidth - edgeMargin).coerceAtLeast(edgeMargin))
        top = top.coerceIn(edgeMargin, (height - popupHeight - edgeMargin).coerceAtLeast(edgeMargin))

        val layoutParams = (popup.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(popupWidth, popupHeight)
        layoutParams.width = popupWidth
        layoutParams.height = popupHeight
        layoutParams.gravity = Gravity.NO_GRAVITY
        layoutParams.setMargins(left, top, 0, 0)
        popup.layoutParams = layoutParams
    }

    private fun getViewRectRelativeToSelf(view: View): android.graphics.Rect {
        val viewLocation = IntArray(2)
        val selfLocation = IntArray(2)
        view.getLocationInWindow(viewLocation)
        getLocationInWindow(selfLocation)
        return android.graphics.Rect(
            viewLocation[0] - selfLocation[0],
            viewLocation[1] - selfLocation[1],
            viewLocation[0] - selfLocation[0] + view.width,
            viewLocation[1] - selfLocation[1] + view.height,
        )
    }

    private fun animatePopupIn(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.scaleX = POPUP_SCALE_COLLAPSED
        view.scaleY = POPUP_SCALE_COLLAPSED
        view.isVisible = true
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(POPUP_ANIMATION_DURATION_MS)
            .start()
    }

    private fun animatePopupOut(view: View) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .scaleX(POPUP_SCALE_COLLAPSED)
            .scaleY(POPUP_SCALE_COLLAPSED)
            .setDuration(POPUP_ANIMATION_DURATION_MS)
            .withEndAction { finishHide() }
            .start()
    }

    private fun checkParameterAddedOrException(parameter: Parameter) {
        if (!parameters.contains(parameter)) {
            throw IllegalStateException(ERROR_TEXT_NO_PARAMETER.format(parameter))
        }
    }

    sealed class Parameter {
        object PlaybackSpeed : Parameter()
        object VideoQuality : Parameter()
        object PictureInPicture : Parameter()
        object Subtitles : Parameter()
        object Attachments : Parameter()
    }

    private companion object {
        private const val ERROR_TEXT_PARAMETER_DUPLICATION =
            "Parameter duplication error. The %s parameter has already been added."
        private const val ERROR_TEXT_NO_PARAMETER =
            "The %s parameter has not been added. First add it to set options for it."

        private const val POPUP_ANIMATION_DURATION_MS = 180L
        private const val POPUP_SCALE_COLLAPSED = 0.98f
    }
}
