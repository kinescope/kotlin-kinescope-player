package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import io.kinescope.sdk.R
import io.kinescope.sdk.databinding.ViewSettingsOptionBinding

class KinescopeSettingsOptionView(
    context: Context,
    attributes: AttributeSet? = null,
) : FrameLayout(context, attributes) {

    private val binding =
        ViewSettingsOptionBinding.inflate(LayoutInflater.from(context), this, true)

    private val badgeRisePx =
        resources.getDimension(R.dimen.kinescope_settings_quality_badge_rise)

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun setTitle(title: String, badge: String? = null) = with(binding) {
        titleTv.text = title
        if (badge.isNullOrEmpty()) {
            badgeTv.isVisible = false
            badgeTv.translationY = 0f
            return@with
        }
        badgeTv.text = badge
        badgeTv.isVisible = true
        badgeTv.translationY = -badgeRisePx
    }

    fun setIsSelected(isSelected: Boolean) = with(binding.selectedIv) {
        isVisible = isSelected
    }

    fun applyIconTint(@ColorInt color: Int) {
        binding.selectedIv.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}
