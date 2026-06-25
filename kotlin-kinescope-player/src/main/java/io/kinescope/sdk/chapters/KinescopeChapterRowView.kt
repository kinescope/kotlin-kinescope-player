package io.kinescope.sdk.chapters

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import io.kinescope.sdk.databinding.ViewChaptersRowBinding

class KinescopeChapterRowView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes) {

    private val binding = ViewChaptersRowBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun setTitle(title: String) {
        binding.titleTv.text = title
    }

    fun setTime(timeLabel: String) {
        binding.timeTv.text = timeLabel
    }
}
