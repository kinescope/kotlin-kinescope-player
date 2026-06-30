package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import io.kinescope.sdk.databinding.ViewSettingsParameterBinding

class KinescopeSettingsParameterView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes) {

    private val binding: ViewSettingsParameterBinding

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        binding = ViewSettingsParameterBinding.inflate(LayoutInflater.from(context), this)
    }

    fun setIcon(@DrawableRes iconRes: Int) = with(binding.iconIv) {
        setImageResource(iconRes)
    }

    fun applyIconTint(@ColorInt color: Int) {
        binding.iconIv.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        binding.forwardIv.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun setTitle(title: String) = with(binding.titleTv) {
        text = title
    }

    fun setCurrentValue(value: String) {
        binding.currentValueTv.text = value
        updateValueVisibility()
    }

    fun setExpandable(expandable: Boolean) {
        binding.forwardIv.isVisible = expandable
        updateValueVisibility()
    }

    private fun updateValueVisibility() {
        binding.currentValueTv.isVisible =
            binding.forwardIv.isVisible &&
            binding.currentValueTv.text.isNotEmpty()
    }
}
