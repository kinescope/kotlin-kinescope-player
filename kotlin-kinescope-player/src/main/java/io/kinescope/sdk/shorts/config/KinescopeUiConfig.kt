package io.kinescope.sdk.shorts.config

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.DrawableCompat
import io.kinescope.sdk.R

/**
 * Public UI configuration for consumers of the library.
 *
 * How to use (example):
 * - Hide offline button:
 *   KinescopeUiConfig.showOfflineButton = false
 *
 * - Replace offline icon:
 *   KinescopeUiConfig.offlineButtonIconResId = R.drawable.my_save_icon
 *
 * - Change seekbar colors:
 *   KinescopeUiConfig.seekBarProgressColor = ContextCompat.getColor(ctx, R.color.my_progress)
 *   KinescopeUiConfig.seekBarTrackColor = ContextCompat.getColor(ctx, R.color.my_track)
 */
object KinescopeUiConfig {
    // Buttons visibility (null = do not override default XML visibility)
    @JvmField var showLikeButton: Boolean? = null
    @JvmField var showDislikeButton: Boolean? = null
    @JvmField var showCommentButton: Boolean? = null
    @JvmField var showShareButton: Boolean? = null
    @JvmField var showOfflineButton: Boolean? = null
    @JvmField var showSavedVideosButton: Boolean? = null

    // Buttons icons (null = do not override XML src; 0 = clear icon)
    @JvmField @DrawableRes var likeButtonIconResId: Int? = null
    @JvmField @DrawableRes var dislikeButtonIconResId: Int? = null
    @JvmField @DrawableRes var commentButtonIconResId: Int? = null
    @JvmField @DrawableRes var shareButtonIconResId: Int? = null
    @JvmField @DrawableRes var offlineButtonIconResId: Int? = null // was @drawable/ic_save
    @JvmField @DrawableRes var savedVideosButtonIconResId: Int? = null

    // Notifications icons (null = default library icon)
    @JvmField @DrawableRes var downloadNotificationIconResId: Int? = null // default: R.drawable.ic_save

    // SeekBar colors
    @JvmField @ColorInt var seekBarProgressColor: Int? = null
    @JvmField @ColorInt var seekBarTrackColor: Int? = null

    /**
     * Apply configured icons/visibility to the "video item" root view (where buttons live).
     */
    @JvmStatic
    fun applyToVideoItem(root: View) {
        applyImageButton(root, R.id.btnLike, showLikeButton, likeButtonIconResId)
        applyImageButton(root, R.id.btnDisLike, showDislikeButton, dislikeButtonIconResId)
        applyImageButton(root, R.id.btnComment, showCommentButton, commentButtonIconResId)
        applyImageButton(root, R.id.btnShare, showShareButton, shareButtonIconResId)
        applyImageButton(root, R.id.btnOffline, showOfflineButton, offlineButtonIconResId)
        applyImageButton(root, R.id.btnShowSavedVideos, showSavedVideosButton, savedVideosButtonIconResId)
    }

    /**
     * Apply seekbar colors (progress + track) if configured.
     */
    @JvmStatic
    fun applyToSeekBar(seekBar: SeekBar) {
        val progressColor = seekBarProgressColor
        val trackColor = seekBarTrackColor
        if (progressColor == null && trackColor == null) return

        // Prefer tinting individual layers of our custom progress drawable.
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

        // Fallback to platform tints.
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




