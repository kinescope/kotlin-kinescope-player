package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import io.kinescope.sdk.databinding.ViewSettingsParameterBinding

class KinescopeSettingsParameterView(
    context: Context,
    attributes: AttributeSet? = null,
) : ConstraintLayout(context, attributes) {

    private val binding =
        ViewSettingsParameterBinding.inflate(LayoutInflater.from(context), this, true)

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

    fun setCurrentValue(value: String) = with(binding.currentValueTv) {
        isVisible = value.isNotEmpty()
        text = value
    }

    fun setExpandable(expandable: Boolean) {
        binding.forwardIv.isVisible = expandable
        binding.currentValueTv.isVisible = expandable && binding.currentValueTv.text.isNotEmpty()
    }
}