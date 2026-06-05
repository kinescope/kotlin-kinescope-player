package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import io.kinescope.sdk.databinding.ViewSettingsOptionBinding

class KinescopeSettingsOptionView(
    context: Context,
    attributes: AttributeSet? = null,
) : FrameLayout(context, attributes) {

    private val binding =
        ViewSettingsOptionBinding.inflate(LayoutInflater.from(context), this, true)

    fun setTitle(title: String) = with(binding.titleTv) {
        text = title
    }

    fun setIsSelected(isSelected: Boolean) = with(binding.selectedIv) {
        isVisible = isSelected
    }

    fun applyIconTint(@ColorInt color: Int) {
        binding.selectedIv.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}