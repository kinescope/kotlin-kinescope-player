package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import io.kinescope.sdk.databinding.ViewSettingsNavigationRowBinding

class KinescopeSettingsNavigationRowView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes) {

    private val binding: ViewSettingsNavigationRowBinding

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        binding = ViewSettingsNavigationRowBinding.inflate(LayoutInflater.from(context), this)
    }

    fun setTitle(title: String) {
        binding.titleTv.text = title
    }

    fun setValue(value: String) {
        binding.valueTv.text = value
    }

    fun applyIconTint(@ColorInt color: Int) {
        binding.forwardIv.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}
