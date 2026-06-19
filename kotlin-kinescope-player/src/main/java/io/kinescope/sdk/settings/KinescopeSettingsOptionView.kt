package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
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

    fun setTitle(title: String, badge: String? = null) = with(binding.titleTv) {
        if (badge.isNullOrEmpty()) {
            text = title
            return@with
        }
        val spacer = "\u2009"
        val start = title.length + spacer.length
        val end = start + badge.length
        text = SpannableStringBuilder(title)
            .append(spacer)
            .append(badge)
            .apply {
                setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(0.71f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
    }

    fun setIsSelected(isSelected: Boolean) = with(binding.selectedIv) {
        isVisible = isSelected
    }

    fun applyIconTint(@ColorInt color: Int) {
        binding.selectedIv.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}
