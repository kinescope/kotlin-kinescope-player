package io.kinescope.sdk.shorts.utils

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import io.kinescope.sdk.shorts.config.KinescopeUiConfig

/**
 * Shorts poster fallback — Figma Player stub (Type=Shorts / Video fill),
 * overridable via [KinescopeUiConfig.showPreloadImage] / [KinescopeUiConfig.preloadImageResId].
 */
object ThumbnailLoader {

    /** `0` when preload stub is disabled. */
    @DrawableRes
    fun placeholderRes(): Int = KinescopeUiConfig.resolvePreloadImageResId()

    fun getPlaceholder(context: Context): Drawable? {
        val res = placeholderRes()
        if (res == 0) return null
        return ContextCompat.getDrawable(context, res)
    }
}
