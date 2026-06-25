package io.kinescope.sdk.settings

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import io.kinescope.sdk.R
import io.kinescope.sdk.databinding.ViewSettingsBinding
import io.kinescope.sdk.databinding.ViewSettingsSubmenuHeaderBinding
import java.lang.IllegalStateException
import java.lang.ref.WeakReference

class KinescopeSettingsView(
    context: Context,
    attributes: AttributeSet? = null,
) : FrameLayout(context, attributes) {

    private val parameters = mutableSetOf<Parameter>()
    private val actionParameters = mutableSetOf<Parameter>()
    private val parameterOptions = mutableMapOf<Parameter, List<KinescopeSettingsOption>>()
    private val navigationStack = ArrayDeque<NavScreen>()
    private var anchorView: WeakReference<View>? = null
    private var isFullscreenMode = false
    private var isHidingPopup = false
    private var currentScreenView: View? = null
    private var popupHeightAnimator: ValueAnimator? = null
    private var screenTransitionAnimator: AnimatorSet? = null
    private var isScreenTransitionActive = false
    private var iconTintColor: Int = 0xFFFFFFFF.toInt()
    private var subtitleStyle = SubtitleStyle()
    private var screenRefreshSuppressed = 0
    private var pendingMainScreenRefresh = false

    private val binding =
        ViewSettingsBinding.inflate(LayoutInflater.from(context), this, true)

    var onOptionSelected: ((parameter: Parameter, optionId: Int) -> Unit)? = null
    var onParameterAction: ((parameter: Parameter) -> Unit)? = null
    var onSubtitleStyleChanged: ((SubtitleStyle) -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        isVisible = false
        binding.root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        subtitleStyle = style
        if (navigationStack.lastOrNull() is NavScreen.SubtitleAppearance ||
            navigationStack.lastOrNull() is NavScreen.SubtitleAppearanceDetail
        ) {
            refreshCurrentScreen(animated = false)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isVisible) {
            return super.dispatchTouchEvent(event)
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!binding.settingsPopupContainer.isVisible) {
                return super.dispatchTouchEvent(event)
            }
            val popupRect = Rect().also { binding.settingsPopupContainer.getGlobalVisibleRect(it) }
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
        if (binding.settingsPopupContainer.isVisible && !isScreenTransitionActive) {
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
        parameterMeta[parameter] = ParameterMeta(title, icon)
    }

    fun setParameterAction(parameter: Parameter, isAction: Boolean) {
        checkParameterAddedOrException(parameter)
        if (isAction) {
            actionParameters.add(parameter)
        } else {
            actionParameters.remove(parameter)
        }
    }

    fun setParameterCurrentValue(parameter: Parameter, value: String) {
        checkParameterAddedOrException(parameter)
        parameterCurrentValues[parameter] = value
        requestMainScreenRefresh()
    }

    fun setParameterOptions(parameter: Parameter, options: List<KinescopeSettingsOption>) {
        checkParameterAddedOrException(parameter)
        parameterOptions[parameter] = options
    }

    fun setParameterVisible(parameter: Parameter, visible: Boolean) {
        if (!parameters.contains(parameter)) {
            return
        }
        parameterVisibility[parameter] = visible
        requestMainScreenRefresh()
    }

    /**
     * Applies multiple parameter mutations and rebuilds the main screen at most once.
     */
    fun runBatchUpdate(block: () -> Unit) {
        screenRefreshSuppressed++
        try {
            block()
        } finally {
            screenRefreshSuppressed--
            if (screenRefreshSuppressed == 0) {
                flushPendingMainScreenRefresh()
            }
        }
    }

    private fun requestMainScreenRefresh() {
        if (navigationStack.lastOrNull() != NavScreen.Main) {
            return
        }
        if (screenRefreshSuppressed > 0 || isScreenTransitionActive) {
            pendingMainScreenRefresh = true
            return
        }
        pendingMainScreenRefresh = false
        refreshCurrentScreen(animated = false)
    }

    private fun flushPendingMainScreenRefresh() {
        if (!pendingMainScreenRefresh) {
            return
        }
        if (navigationStack.lastOrNull() != NavScreen.Main || isScreenTransitionActive) {
            return
        }
        pendingMainScreenRefresh = false
        refreshCurrentScreen(animated = false)
    }

    private fun cancelScreenTransition() {
        screenTransitionAnimator?.cancel()
        screenTransitionAnimator = null
        popupHeightAnimator?.cancel()
        popupHeightAnimator = null
        isScreenTransitionActive = false
        binding.settingsScreenContainer.animate().cancel()
        currentScreenView?.animate()?.cancel()
    }

    fun applyIconTint(@ColorInt color: Int) {
        iconTintColor = color
        tintScreenView(currentScreenView, color)
    }

    fun show() {
        isHidingPopup = false
        navigationStack.clear()
        navigationStack.add(NavScreen.Main)
        isVisible = true
        ensurePopupVisible()
        post {
            showScreen(NavScreen.Main, forward = true, animatePopup = false)
            positionPopupWithinBounds()
        }
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
        navigationStack.clear()
        animatePopupOut(binding.settingsPopupContainer)
    }

    private fun finishHide() {
        isHidingPopup = false
        screenTransitionAnimator?.cancel()
        screenTransitionAnimator = null
        isScreenTransitionActive = false
        popupHeightAnimator?.cancel()
        popupHeightAnimator = null
        binding.settingsPopupContainer.animate().cancel()
        binding.settingsScreenContainer.removeAllViews()
        currentScreenView = null
        navigationStack.clear()
        binding.settingsPopupContainer.isVisible = false
        binding.settingsPopupContainer.alpha = 1f
        binding.settingsPopupContainer.scaleX = 1f
        binding.settingsPopupContainer.scaleY = 1f
        isVisible = false
    }

    private fun onParameterClick(title: String, parameter: Parameter) {
        if (actionParameters.contains(parameter)) {
            hide(animated = true)
            onParameterAction?.invoke(parameter)
            return
        }

        val screen = NavScreen.ParameterOptions(parameter, title)
        if (navigationStack.lastOrNull() == screen) {
            navigateBack()
            return
        }
        navigateTo(screen)
    }

    private fun navigateTo(screen: NavScreen) {
        val from = navigationStack.lastOrNull() ?: NavScreen.Main
        navigationStack.add(screen)
        showScreen(
            screen = screen,
            forward = isForwardNavigation(from = from, to = screen),
            animatePopup = false,
        )
    }

    private fun navigateBack() {
        if (navigationStack.size <= 1) {
            return
        }
        val from = navigationStack.last()
        navigationStack.removeLast()
        val to = navigationStack.last()
        showScreen(
            screen = to,
            forward = isForwardNavigation(from = from, to = to),
            animatePopup = false,
        )
    }

    private fun isForwardNavigation(from: NavScreen, to: NavScreen): Boolean {
        return to.depth() > from.depth()
    }

    private fun measureScreenHeight(view: View, widthPx: Int): Int {
        val widthSpec = MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight.coerceAtLeast(0)
    }

    private fun popupVerticalPaddingPx(): Int {
        val popup = binding.settingsPopupContainer
        return popup.paddingTop + popup.paddingBottom
    }

    private fun popupHeightForContentHeight(contentHeightPx: Int): Int {
        val verticalPadding = popupVerticalPaddingPx()
        val rowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)
        val maxHeight = calculateMaxPopupHeight()
            .coerceAtLeast(rowHeight + verticalPadding)
        return (contentHeightPx + verticalPadding).coerceAtMost(maxHeight)
    }

    private fun popupTopForHeight(popupHeightPx: Int): Int {
        val bottomOffset =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_bottom_offset)
        val topMargin =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        return (height - popupHeightPx - bottomOffset).coerceAtLeast(topMargin)
    }

    private fun popupLeftMargin(): Int {
        val endMargin =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        val desiredWidth =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_width)
        val maxWidth = (width - endMargin * 2).coerceAtLeast(0)
        val popupWidth = desiredWidth.coerceAtMost(maxWidth)
        val isRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
        return if (isRtl) {
            endMargin
        } else {
            width - popupWidth - endMargin
        }
    }

    private fun popupWidthPx(): Int {
        val endMargin =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        val desiredWidth =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_width)
        val maxWidth = (width - endMargin * 2).coerceAtLeast(0)
        return desiredWidth.coerceAtMost(maxWidth)
    }

    private fun applyPopupLayout(contentHeightPx: Int) {
        val popup = binding.settingsPopupContainer
        val popupWidth = popupWidthPx()
        if (popupWidth == 0) {
            return
        }
        val popupHeight = popupHeightForContentHeight(contentHeightPx)
        val layoutParams = (popup.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(popupWidth, popupHeight)
        layoutParams.width = popupWidth
        layoutParams.height = popupHeight
        layoutParams.gravity = Gravity.NO_GRAVITY
        layoutParams.setMargins(popupLeftMargin(), popupTopForHeight(popupHeight), 0, 0)
        popup.layoutParams = layoutParams
    }

    private fun refreshCurrentScreen(animated: Boolean) {
        val screen = navigationStack.lastOrNull() ?: return
        showScreen(
            screen = screen,
            forward = true,
            animatePopup = false,
            forceRebuild = true,
            animated = animated,
        )
    }

    private fun replaceScreenInstantly(
        container: ViewGroup,
        incoming: View,
        screenLayoutParams: FrameLayout.LayoutParams,
        animateHeight: Boolean,
    ) {
        cancelScreenTransition()
        container.removeAllViews()
        container.addView(incoming, screenLayoutParams)
        incoming.translationX = 0f
        incoming.alpha = 1f
        currentScreenView = incoming
        tintScreenView(incoming, iconTintColor)
        val containerLayoutParams = container.layoutParams
        containerLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        container.layoutParams = containerLayoutParams
        post { positionPopupWithinBounds(animateHeight = animateHeight) }
    }

    private fun navigationSlideDistancePx(): Float {
        val container = binding.settingsScreenContainer
        if (container.width > 0) {
            return container.width.toFloat()
        }
        val popup = binding.settingsPopupContainer
        if (popup.width > 0) {
            return popup.width.toFloat()
        }
        return resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_width).toFloat()
    }

    private fun animateScreenTransition(
        container: ViewGroup,
        incoming: View,
        outgoing: View,
        screenLayoutParams: FrameLayout.LayoutParams,
        forward: Boolean,
    ) {
        popupHeightAnimator?.cancel()
        popupHeightAnimator = null
        screenTransitionAnimator?.cancel()
        incoming.animate().cancel()
        outgoing.animate().cancel()

        val widthPx = navigationSlideDistancePx().toInt().coerceAtLeast(1)
        val slideDistance = widthPx.toFloat()
        val outgoingContentHeight = outgoing.height.takeIf { it > 0 }
            ?: measureScreenHeight(outgoing, widthPx)
        val incomingContentHeight = measureScreenHeight(incoming, widthPx)

        val fromPopupHeight = popupHeightForContentHeight(outgoingContentHeight)
        val toPopupHeight = popupHeightForContentHeight(incomingContentHeight)
        val fromPopupTop = popupTopForHeight(fromPopupHeight)
        val toPopupTop = popupTopForHeight(toPopupHeight)

        val containerLayoutParams = container.layoutParams
        containerLayoutParams.height = outgoingContentHeight
        container.layoutParams = containerLayoutParams

        incoming.layoutParams = screenLayoutParams
        incoming.translationX = if (forward) slideDistance else -slideDistance
        incoming.alpha = 0f
        container.addView(incoming)

        currentScreenView = incoming
        tintScreenView(incoming, iconTintColor)
        applyPopupLayout(outgoingContentHeight)

        incoming.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        outgoing.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isScreenTransitionActive = true

        val interpolator = NAV_INTERPOLATOR
        val sizeTransform = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NAV_ANIMATION_DURATION_MS
            this.interpolator = interpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val contentHeight = (outgoingContentHeight +
                    (incomingContentHeight - outgoingContentHeight) * fraction).toInt()
                containerLayoutParams.height = contentHeight
                container.layoutParams = containerLayoutParams

                val popup = binding.settingsPopupContainer
                val popupLayoutParams = popup.layoutParams as FrameLayout.LayoutParams
                popupLayoutParams.height =
                    (fromPopupHeight + (toPopupHeight - fromPopupHeight) * fraction).toInt()
                popupLayoutParams.topMargin =
                    (fromPopupTop + (toPopupTop - fromPopupTop) * fraction).toInt()
                popup.layoutParams = popupLayoutParams
            }
        }
        val slideInX = ObjectAnimator.ofFloat(
            incoming,
            View.TRANSLATION_X,
            if (forward) slideDistance else -slideDistance,
            0f,
        ).apply {
            duration = NAV_ANIMATION_DURATION_MS
            this.interpolator = interpolator
        }
        val fadeIn = ObjectAnimator.ofFloat(incoming, View.ALPHA, 0f, 1f).apply {
            duration = FADE_ANIMATION_DURATION_MS
            this.interpolator = interpolator
        }
        val slideOutX = ObjectAnimator.ofFloat(
            outgoing,
            View.TRANSLATION_X,
            0f,
            if (forward) -slideDistance else slideDistance,
        ).apply {
            duration = NAV_ANIMATION_DURATION_MS
            this.interpolator = interpolator
        }
        val fadeOut = ObjectAnimator.ofFloat(outgoing, View.ALPHA, 1f, 0f).apply {
            duration = FADE_ANIMATION_DURATION_MS
            this.interpolator = interpolator
        }

        screenTransitionAnimator = AnimatorSet().apply {
            playTogether(sizeTransform, slideInX, fadeIn, slideOutX, fadeOut)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finishScreenTransition(container, incoming, outgoing, containerLayoutParams)
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    incoming.setLayerType(View.LAYER_TYPE_NONE, null)
                    outgoing.setLayerType(View.LAYER_TYPE_NONE, null)
                    isScreenTransitionActive = false
                    screenTransitionAnimator = null
                }
            })
            start()
        }
    }

    private fun finishScreenTransition(
        container: ViewGroup,
        incoming: View,
        outgoing: View,
        containerLayoutParams: ViewGroup.LayoutParams,
    ) {
        incoming.translationX = 0f
        incoming.alpha = 1f
        incoming.setLayerType(View.LAYER_TYPE_NONE, null)
        outgoing.setLayerType(View.LAYER_TYPE_NONE, null)
        container.removeView(outgoing)
        containerLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        container.layoutParams = containerLayoutParams
        isScreenTransitionActive = false
        screenTransitionAnimator = null
        positionPopupWithinBounds(animateHeight = false)
        flushPendingMainScreenRefresh()
    }

    private fun showScreen(
        screen: NavScreen,
        forward: Boolean,
        animatePopup: Boolean,
        forceRebuild: Boolean = false,
        animated: Boolean = true,
    ) {
        val container = binding.settingsScreenContainer
        val incoming = buildScreen(screen)
        ensurePopupVisible()
        val screenLayoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        if (animatePopup) {
            container.removeAllViews()
            container.addView(incoming, screenLayoutParams)
            currentScreenView = incoming
            tintScreenView(incoming, iconTintColor)
            post { positionPopupWithinBounds(animateHeight = false) }
            return
        }

        val outgoing = currentScreenView
        if (outgoing == null || !animated) {
            replaceScreenInstantly(container, incoming, screenLayoutParams, animateHeight = false)
            return
        }

        if (forceRebuild) {
            replaceScreenInstantly(container, incoming, screenLayoutParams, animateHeight = animated)
            return
        }

        val startTransition = {
            animateScreenTransition(
                container = container,
                incoming = incoming,
                outgoing = outgoing,
                screenLayoutParams = screenLayoutParams,
                forward = forward,
            )
        }

        if (container.width > 0 || binding.settingsPopupContainer.width > 0) {
            startTransition()
        } else {
            positionPopupWithinBounds()
            container.doOnLayout { startTransition() }
        }
    }

    private fun buildScreen(screen: NavScreen): View = when (screen) {
        NavScreen.Main -> buildMainScreen()
        is NavScreen.ParameterOptions -> buildParameterOptionsScreen(screen)
        NavScreen.SubtitleAppearance -> buildSubtitleAppearanceScreen()
        is NavScreen.SubtitleAppearanceDetail -> buildSubtitleAppearanceDetailScreen(screen.type)
    }

    private fun buildMainScreen(): View {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val orderedParameters = PARAMETER_DISPLAY_ORDER.filter { parameters.contains(it) } +
            parameters.filterNot { it in PARAMETER_DISPLAY_ORDER }
        orderedParameters.forEach { parameter ->
            if (parameterVisibility[parameter] == false) {
                return@forEach
            }
            val meta = parameterMeta[parameter] ?: return@forEach
            list.addView(
                KinescopeSettingsParameterView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height),
                    )
                    setTitle(meta.title)
                    setIcon(meta.icon)
                    setCurrentValue(parameterCurrentValues[parameter].orEmpty())
                    setExpandable(expandable = !actionParameters.contains(parameter))
                    setOnClickListener {
                        onParameterClick(title = meta.title, parameter = parameter)
                    }
                },
            )
        }
        return list
    }

    private fun buildParameterOptionsScreen(screen: NavScreen.ParameterOptions): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val headerBinding = ViewSettingsSubmenuHeaderBinding.inflate(LayoutInflater.from(context))
        headerBinding.titleTv.text = screen.title
        val navigateBackFromHeader = View.OnClickListener { navigateBack() }
        headerBinding.backIv.setOnClickListener(navigateBackFromHeader)
        headerBinding.titleTv.setOnClickListener(navigateBackFromHeader)
        tintHeader(headerBinding)

        val showSubtitleSettings = screen.parameter == Parameter.Subtitles &&
            parameterOptions[Parameter.Subtitles].orEmpty().size > 1
        if (showSubtitleSettings) {
            headerBinding.actionTv.isVisible = true
            headerBinding.actionTv.text = context.getString(R.string.settings_subtitles_appearance)
            headerBinding.actionTv.setOnClickListener {
                navigateTo(NavScreen.SubtitleAppearance)
            }
        }

        root.addView(
            headerBinding.root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height),
            ),
        )

        val options = parameterOptions[screen.parameter].orEmpty()
        val selectedIndex = options.indexOfFirst { it.isSelected }
        val scrollView = ScrollView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                optionsScrollMaxHeight(options.size),
            )
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        options.forEach { option ->
            list.addView(
                KinescopeSettingsOptionView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height),
                    )
                    setTitle(option.title, option.badge)
                    setIsSelected(option.isSelected)
                    setOnClickListener {
                        onOptionClicked(screen.parameter, option.id)
                    }
                },
            )
        }
        scrollView.addView(list)
        root.addView(scrollView)

        if (selectedIndex > 0) {
            scrollView.post {
                val rowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)
                scrollView.scrollTo(0, selectedIndex * rowHeight)
            }
        }

        return root
    }

    private fun buildSubtitleAppearanceScreen(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val headerBinding = ViewSettingsSubmenuHeaderBinding.inflate(LayoutInflater.from(context))
        headerBinding.titleTv.text = context.getString(R.string.settings_subtitles_appearance_title)
        val navigateBackFromHeader = View.OnClickListener { navigateBack() }
        headerBinding.backIv.setOnClickListener(navigateBackFromHeader)
        headerBinding.titleTv.setOnClickListener(navigateBackFromHeader)
        headerBinding.actionTv.isVisible = true
        headerBinding.actionTv.text = context.getString(R.string.settings_subtitles_reset)
        headerBinding.actionTv.setOnClickListener {
            subtitleStyle = SubtitleStyle()
            onSubtitleStyleChanged?.invoke(subtitleStyle)
            refreshCurrentScreen(animated = false)
        }
        tintHeader(headerBinding)

        root.addView(
            headerBinding.root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height),
            ),
        )

        val scrollView = ScrollView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                optionsScrollMaxHeight(optionCount = 4),
            )
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        addAppearanceRow(
            list = list,
            title = context.getString(R.string.settings_subtitles_font_color),
            value = localizedColorLabel(subtitleStyle.fontColor),
            onClick = { navigateTo(NavScreen.SubtitleAppearanceDetail(SubtitleAppearanceType.FontColor)) },
        )
        addAppearanceRow(
            list = list,
            title = context.getString(R.string.settings_subtitles_font_size),
            value = "${subtitleStyle.fontSizePercent}%",
            onClick = { navigateTo(NavScreen.SubtitleAppearanceDetail(SubtitleAppearanceType.FontSize)) },
        )
        addAppearanceRow(
            list = list,
            title = context.getString(R.string.settings_subtitles_bg_color),
            value = localizedColorLabel(subtitleStyle.bgColor),
            onClick = { navigateTo(NavScreen.SubtitleAppearanceDetail(SubtitleAppearanceType.BgColor)) },
        )
        addAppearanceRow(
            list = list,
            title = context.getString(R.string.settings_subtitles_bg_opacity),
            value = "${subtitleStyle.bgOpacityPercent}%",
            onClick = { navigateTo(NavScreen.SubtitleAppearanceDetail(SubtitleAppearanceType.BgOpacity)) },
        )

        scrollView.addView(list)
        root.addView(scrollView)
        scrollView.post { scrollView.scrollTo(0, 0) }
        return root
    }

    private fun buildSubtitleAppearanceDetailScreen(type: SubtitleAppearanceType): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val titleRes = when (type) {
            SubtitleAppearanceType.FontColor -> R.string.settings_subtitles_font_color
            SubtitleAppearanceType.FontSize -> R.string.settings_subtitles_font_size
            SubtitleAppearanceType.BgColor -> R.string.settings_subtitles_bg_color
            SubtitleAppearanceType.BgOpacity -> R.string.settings_subtitles_bg_opacity
        }

        val headerBinding = ViewSettingsSubmenuHeaderBinding.inflate(LayoutInflater.from(context))
        headerBinding.titleTv.text = context.getString(titleRes)
        val navigateBackFromHeader = View.OnClickListener { navigateBack() }
        headerBinding.backIv.setOnClickListener(navigateBackFromHeader)
        headerBinding.titleTv.setOnClickListener(navigateBackFromHeader)
        tintHeader(headerBinding)

        root.addView(
            headerBinding.root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height),
            ),
        )

        val optionCount = when (type) {
            SubtitleAppearanceType.FontColor,
            SubtitleAppearanceType.BgColor,
            -> SubtitleStyleDefaults.colors.size
            SubtitleAppearanceType.FontSize -> SubtitleStyleDefaults.fontSizes.size
            SubtitleAppearanceType.BgOpacity -> SubtitleStyleDefaults.bgOpacities.size
        }

        val scrollView = ScrollView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                optionsScrollMaxHeight(optionCount),
            )
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        when (type) {
            SubtitleAppearanceType.FontColor,
            SubtitleAppearanceType.BgColor,
            -> {
                val selectedColor = if (type == SubtitleAppearanceType.FontColor) {
                    subtitleStyle.fontColor
                } else {
                    subtitleStyle.bgColor
                }
                localizedColors().forEach { colorOption ->
                    list.addView(
                        createCheckRow(
                            title = colorOption.label,
                            checked = colorOption.color == selectedColor,
                            onClick = {
                                subtitleStyle = if (type == SubtitleAppearanceType.FontColor) {
                                    subtitleStyle.copy(fontColor = colorOption.color)
                                } else {
                                    subtitleStyle.copy(bgColor = colorOption.color)
                                }
                                onSubtitleStyleChanged?.invoke(subtitleStyle)
                                navigateBack()
                            },
                        ),
                    )
                }
            }

            SubtitleAppearanceType.FontSize -> {
                SubtitleStyleDefaults.fontSizes.forEach { size ->
                    list.addView(
                        createCheckRow(
                            title = "$size%",
                            checked = size == subtitleStyle.fontSizePercent,
                            onClick = {
                                subtitleStyle = subtitleStyle.copy(fontSizePercent = size)
                                onSubtitleStyleChanged?.invoke(subtitleStyle)
                                navigateBack()
                            },
                        ),
                    )
                }
            }

            SubtitleAppearanceType.BgOpacity -> {
                SubtitleStyleDefaults.bgOpacities.forEach { opacity ->
                    list.addView(
                        createCheckRow(
                            title = "$opacity%",
                            checked = opacity == subtitleStyle.bgOpacityPercent,
                            onClick = {
                                subtitleStyle = subtitleStyle.copy(bgOpacityPercent = opacity)
                                onSubtitleStyleChanged?.invoke(subtitleStyle)
                                navigateBack()
                            },
                        ),
                    )
                }
            }
        }

        scrollView.addView(list)
        root.addView(scrollView)
        scrollView.post {
            val selectedIndex = when (type) {
                SubtitleAppearanceType.FontColor,
                SubtitleAppearanceType.BgColor,
                -> SubtitleStyleDefaults.colors.indexOfFirst {
                    it.color == if (type == SubtitleAppearanceType.FontColor) {
                        subtitleStyle.fontColor
                    } else {
                        subtitleStyle.bgColor
                    }
                }

                SubtitleAppearanceType.FontSize ->
                    SubtitleStyleDefaults.fontSizes.indexOf(subtitleStyle.fontSizePercent)

                SubtitleAppearanceType.BgOpacity ->
                    SubtitleStyleDefaults.bgOpacities.indexOf(subtitleStyle.bgOpacityPercent)
            }
            val rowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)
            scrollView.scrollTo(0, selectedIndex.coerceAtLeast(0) * rowHeight)
        }
        return root
    }

    private fun addAppearanceRow(
        list: LinearLayout,
        title: String,
        value: String,
        onClick: () -> Unit,
    ) {
        list.addView(
            KinescopeSettingsNavigationRowView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height),
                )
                setTitle(title)
                setValue(value)
                applyIconTint(iconTintColor)
                setOnClickListener { onClick() }
            },
        )
    }

    private fun createCheckRow(
        title: String,
        checked: Boolean,
        onClick: () -> Unit,
    ): KinescopeSettingsOptionView = KinescopeSettingsOptionView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height),
        )
        setTitle(title)
        setIsSelected(checked)
        setOnClickListener { onClick() }
    }

    private fun onOptionClicked(parameter: Parameter, optionId: Int) {
        onOptionSelected?.invoke(parameter, optionId)
        when (parameter) {
            Parameter.Subtitles,
            Parameter.Attachments,
            -> hide(animated = true)

            else -> navigateToMain(animated = true)
        }
    }

    private fun navigateToMain(animated: Boolean) {
        if (navigationStack.size <= 1) {
            return
        }
        val from = navigationStack.last()
        while (navigationStack.size > 1) {
            navigationStack.removeLast()
        }
        showScreen(
            screen = NavScreen.Main,
            forward = isForwardNavigation(from = from, to = NavScreen.Main),
            animatePopup = false,
            animated = animated,
        )
    }

    private fun localizedColors(): List<SubtitleColorOption> = listOf(
        SubtitleColorOption(context.getString(R.string.settings_subtitles_color_white), 0xFFFFFFFF.toInt()),
        SubtitleColorOption(context.getString(R.string.settings_subtitles_color_green), 0xFF00FF00.toInt()),
        SubtitleColorOption(context.getString(R.string.settings_subtitles_color_cyan), 0xFF00FFFF.toInt()),
        SubtitleColorOption(context.getString(R.string.settings_subtitles_color_blue), 0xFF0000FF.toInt()),
        SubtitleColorOption(context.getString(R.string.settings_subtitles_color_magenta), 0xFFFF00FF.toInt()),
        SubtitleColorOption(context.getString(R.string.settings_subtitles_color_red), 0xFFFF0000.toInt()),
        SubtitleColorOption(context.getString(R.string.settings_subtitles_color_black), 0xFF000000.toInt()),
    )

    private fun localizedColorLabel(color: Int): String =
        localizedColors().firstOrNull { it.color == color }?.label
            ?: context.getString(R.string.settings_subtitles_color_custom)

    private fun ensurePopupVisible() {
        binding.settingsPopupContainer.isVisible = true
        binding.settingsPopupContainer.alpha = 1f
        binding.settingsPopupContainer.scaleX = 1f
        binding.settingsPopupContainer.scaleY = 1f
    }

    private fun optionsScrollMaxHeight(optionCount: Int): Int {
        val rowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)
        val optionsContentHeight = optionCount.coerceAtLeast(1) * rowHeight
        val hardMax = resources.getDimensionPixelSize(R.dimen.kinescope_settings_options_max_height)
        val maxPopupHeight = calculateMaxPopupHeight()
        val verticalPadding = binding.settingsPopupContainer.paddingTop +
            binding.settingsPopupContainer.paddingBottom
        val headerHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)
        val availableInPopup = (maxPopupHeight - verticalPadding - headerHeight).coerceAtLeast(rowHeight)
        return optionsContentHeight.coerceAtMost(hardMax).coerceAtMost(availableInPopup)
    }

    private fun calculateMaxPopupHeight(): Int {
        val endMargin = resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        val bottomOffset =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_bottom_offset)
        val hardMax = resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_max_height)
        return (height - bottomOffset - endMargin).coerceAtMost(hardMax).coerceAtLeast(0)
    }

    private fun visibleParameterCount(): Int =
        parameters.count { parameterVisibility[it] != false }

    private fun tintHeader(headerBinding: ViewSettingsSubmenuHeaderBinding) {
        headerBinding.backIv.setColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN)
    }

    private fun tintScreenView(view: View?, @ColorInt color: Int) {
        if (view == null) {
            return
        }
        if (view is ScrollView) {
            (view.getChildAt(0) as? ViewGroup)?.let { tintScreenView(it, color) }
            return
        }
        if (view is LinearLayout) {
            view.children.forEach { child ->
                when (child) {
                    is KinescopeSettingsParameterView -> child.applyIconTint(color)
                    is KinescopeSettingsOptionView -> child.applyIconTint(color)
                    is KinescopeSettingsNavigationRowView -> child.applyIconTint(color)
                    is ViewGroup -> {
                        child.children.filterIsInstance<ImageView>().forEach { imageView ->
                            imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                        }
                    }
                }
            }
        }
    }

    private fun positionPopupWithinBounds(animateHeight: Boolean = false) {
        if (width == 0 || height == 0) {
            return
        }

        val popup = binding.settingsPopupContainer
        val endMargin =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        val bottomOffset =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_bottom_offset)
        val topMargin =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_edge_margin)
        val desiredWidth =
            resources.getDimensionPixelSize(R.dimen.kinescope_settings_popup_width)

        val maxWidth = (width - endMargin * 2).coerceAtLeast(0)
        val popupWidth = desiredWidth.coerceAtMost(maxWidth)
        if (popupWidth == 0) {
            return
        }

        val widthSpec = MeasureSpec.makeMeasureSpec(popupWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        currentScreenView?.measure(widthSpec, heightSpec)

        val verticalPadding = popup.paddingTop + popup.paddingBottom
        val rowHeight = resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height)
        val measuredContentHeight = currentScreenView?.measuredHeight?.takeIf { it > 0 }
            ?: (visibleParameterCount().coerceAtLeast(1) * rowHeight)
        val contentHeight = measuredContentHeight + verticalPadding
        val maxHeight = calculateMaxPopupHeight()
            .coerceAtLeast(resources.getDimensionPixelSize(R.dimen.kinescope_settings_row_height) + verticalPadding)
        val popupHeight = contentHeight.coerceAtMost(maxHeight)

        val isRtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
        val left = if (isRtl) {
            endMargin
        } else {
            width - popupWidth - endMargin
        }
        val top = (height - popupHeight - bottomOffset).coerceAtLeast(topMargin)

        val layoutParams = (popup.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(popupWidth, popupHeight)
        layoutParams.width = popupWidth
        layoutParams.gravity = Gravity.NO_GRAVITY
        layoutParams.setMargins(left, top, 0, 0)

        val previousHeight = popup.height.takeIf { it > 0 } ?: popupHeight
        val previousTop = (popup.layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: top

        if (animateHeight && previousHeight != popupHeight && popup.isVisible) {
            popupHeightAnimator?.cancel()
            popupHeightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = NAV_ANIMATION_DURATION_MS
                interpolator = NAV_INTERPOLATOR
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    layoutParams.height =
                        (previousHeight + (popupHeight - previousHeight) * fraction).toInt()
                    layoutParams.topMargin =
                        (previousTop + (top - previousTop) * fraction).toInt()
                    popup.layoutParams = layoutParams
                }
                start()
            }
        } else {
            popupHeightAnimator?.cancel()
            layoutParams.height = popupHeight
            layoutParams.topMargin = top
            popup.layoutParams = layoutParams
        }
    }

    private fun animatePopupOut(view: View) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(POPUP_ANIMATION_DURATION_MS)
            .withEndAction { finishHide() }
            .start()
    }

    private fun checkParameterAddedOrException(parameter: Parameter) {
        if (!parameters.contains(parameter)) {
            throw IllegalStateException(ERROR_TEXT_NO_PARAMETER.format(parameter))
        }
    }

    private data class ParameterMeta(
        val title: String,
        @DrawableRes val icon: Int,
    )

    private sealed class NavScreen {
        object Main : NavScreen()
        data class ParameterOptions(val parameter: Parameter, val title: String) : NavScreen()
        object SubtitleAppearance : NavScreen()
        data class SubtitleAppearanceDetail(val type: SubtitleAppearanceType) : NavScreen()

        fun depth(): Int = when (this) {
            Main -> 0
            is ParameterOptions -> when (parameter) {
                Parameter.PlaybackSpeed -> 1
                Parameter.VideoQuality -> 2
                Parameter.Subtitles -> 3
                Parameter.AudioTracks -> 4
                else -> 1
            }
            SubtitleAppearance -> 4
            is SubtitleAppearanceDetail -> 5 + type.ordinal
        }
    }

    private enum class SubtitleAppearanceType {
        FontColor,
        FontSize,
        BgColor,
        BgOpacity,
    }

    private val parameterMeta = mutableMapOf<Parameter, ParameterMeta>()
    private val parameterCurrentValues = mutableMapOf<Parameter, String>()
    private val parameterVisibility = mutableMapOf<Parameter, Boolean>()

    sealed class Parameter {
        object PlaybackSpeed : Parameter()
        object VideoQuality : Parameter()
        object PictureInPicture : Parameter()
        object Subtitles : Parameter()
        object AudioTracks : Parameter()
        object Attachments : Parameter()
    }

    private companion object {
        private val PARAMETER_DISPLAY_ORDER = listOf(
            Parameter.Subtitles,
            Parameter.AudioTracks,
            Parameter.PlaybackSpeed,
            Parameter.VideoQuality,
            Parameter.Attachments,
            Parameter.PictureInPicture,
        )

        private const val ERROR_TEXT_PARAMETER_DUPLICATION =
            "Parameter duplication error. The %s parameter has already been added."
        private const val ERROR_TEXT_NO_PARAMETER =
            "The %s parameter has not been added. First add it to set options for it."

        private const val POPUP_ANIMATION_DURATION_MS = 140L
        private const val FADE_ANIMATION_DURATION_MS = 140L
        private const val NAV_ANIMATION_DURATION_MS = 200L
        private val NAV_INTERPOLATOR = FastOutSlowInInterpolator()
    }
}
