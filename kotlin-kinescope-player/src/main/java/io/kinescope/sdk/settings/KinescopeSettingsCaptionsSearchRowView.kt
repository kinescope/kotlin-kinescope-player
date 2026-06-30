package io.kinescope.sdk.settings

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import io.kinescope.sdk.R

class KinescopeSettingsCaptionsSearchRowView(
    context: Context,
    attributes: AttributeSet? = null,
) : LinearLayout(context, attributes) {

    private val searchIcon: ImageView
    private val titleView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_settings_captions_search_row, this, true)
        searchIcon = findViewById(R.id.search_icon_iv)
        titleView = findViewById(R.id.title_tv)
        titleView.text = context.getString(R.string.settings_captions_search)
    }

    fun applyIconTint(@ColorInt color: Int) {
        searchIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}
