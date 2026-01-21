package com.kotlin.kinescope.shorts.config

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.DrawableCompat
import com.kotlin.kinescope.shorts.R

object KinescopeUiConfig {
    @JvmField var showLikeButton: Boolean? = null
    @JvmField var showDislikeButton: Boolean? = null
    @JvmField var showCommentButton: Boolean? = null
    @JvmField var showShareButton: Boolean? = null
    @JvmField var showOfflineButton: Boolean? = null
    @JvmField var showSavedVideosButton: Boolean? = null

    @JvmField @DrawableRes var likeButtonIconResId: Int? = null
    @JvmField @DrawableRes var dislikeButtonIconResId: Int? = null
    @JvmField @DrawableRes var commentButtonIconResId: Int? = null
    @JvmField @DrawableRes var shareButtonIconResId: Int? = null
    @JvmField @DrawableRes var offlineButtonIconResId: Int? = null
    @JvmField @DrawableRes var savedVideosButtonIconResId: Int? = null

    @JvmField @DrawableRes var downloadNotificationIconResId: Int? = null

    @JvmField @ColorInt var seekBarProgressColor: Int? = null
    @JvmField @ColorInt var seekBarTrackColor: Int? = null

    @JvmStatic
    fun applyToVideoItem(root: View) {
        applyImageButton(root, R.id.btnLike, showLikeButton, likeButtonIconResId)
        applyImageButton(root, R.id.btnDisLike, showDislikeButton, dislikeButtonIconResId)
        applyImageButton(root, R.id.btnComment, showCommentButton, commentButtonIconResId)
        applyImageButton(root, R.id.btnShare, showShareButton, shareButtonIconResId)
        applyImageButton(root, R.id.btnOffline, showOfflineButton, offlineButtonIconResId)
        applyImageButton(root, R.id.btnShowSavedVideos, showSavedVideosButton, savedVideosButtonIconResId)
    }

    @JvmStatic
    fun applyToSeekBar(seekBar: SeekBar) {
        val progressColor = seekBarProgressColor
        val trackColor = seekBarTrackColor
        if (progressColor == null && trackColor == null) return

        val d = seekBar.progressDrawable?.mutate()
        if (d is LayerDrawable) {
            if (trackColor != null) {
                tintDrawable(d.findDrawableByLayerId(android.R.id.background), trackColor)
            }
            if (progressColor != null) {
                tintDrawable(d.findDrawableByLayerId(android.R.id.progress), progressColor)
            }
            seekBar.progressDrawable = d
            return
        }

        if (progressColor != null) {
            seekBar.progressTintList = ColorStateList.valueOf(progressColor)
            seekBar.secondaryProgressTintList = ColorStateList.valueOf(progressColor)
        }
        if (trackColor != null) {
            seekBar.progressBackgroundTintList = ColorStateList.valueOf(trackColor)
        }
    }

    @DrawableRes
    fun resolveDownloadNotificationIconResId(): Int {
        val configured = downloadNotificationIconResId
        return if (configured != null && configured != 0) configured else R.drawable.ic_save
    }

    private fun applyImageButton(
        root: View,
        viewId: Int,
        show: Boolean?,
        @DrawableRes iconResId: Int?
    ) {
        val btn = root.findViewById<ImageButton>(viewId) ?: return
        show?.let { btn.visibility = if (it) View.VISIBLE else View.GONE }
        if (iconResId != null) {
            if (iconResId == 0) btn.setImageDrawable(null) else btn.setImageResource(iconResId)
        }
    }

    private fun tintDrawable(drawable: Drawable?, @ColorInt color: Int) {
        if (drawable == null) return
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrapped, color)
    }
}




