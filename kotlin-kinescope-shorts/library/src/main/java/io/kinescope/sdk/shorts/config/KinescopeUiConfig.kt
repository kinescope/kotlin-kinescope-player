package io.kinescope.sdk.shorts.config

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.DrawableCompat
import io.kinescope.sdk.shorts.R

object KinescopeUiConfig {
    @JvmField var showLikeButton: Boolean? = null
    @JvmField var showDislikeButton: Boolean? = null
    @JvmField var showCommentButton: Boolean? = null
    @JvmField var showShareButton: Boolean? = null
    @JvmField var showOfflineButton: Boolean? = null
    @JvmField var showSavedVideosButton: Boolean? = null

    /** Center play overlay on pause. `false` hides it; tap-to-toggle still works. */
    @JvmField var showPlayButton: Boolean? = null

    /** Bottom scrub / timeline. `false` hides bar + hit area + time chip. */
    @JvmField var showSeekBar: Boolean? = null

    /**
     * Shorts preload/stub image (shown while poster loads / when poster is missing).
     * `false` skips the stub — black frame until poster or first frame.
     */
    @JvmField var showPreloadImage: Boolean? = null

    @JvmField @DrawableRes var likeButtonIconResId: Int? = null
    @JvmField @DrawableRes var dislikeButtonIconResId: Int? = null
    @JvmField @DrawableRes var commentButtonIconResId: Int? = null
    @JvmField @DrawableRes var shareButtonIconResId: Int? = null
    @JvmField @DrawableRes var offlineButtonIconResId: Int? = null
    @JvmField @DrawableRes var savedVideosButtonIconResId: Int? = null
    @JvmField @DrawableRes var playButtonIconResId: Int? = null

    /** Custom stub drawable; ignored when [showPreloadImage] is `false`. */
    @JvmField @DrawableRes var preloadImageResId: Int? = null

    @JvmField @DrawableRes var downloadNotificationIconResId: Int? = null

    /** Side action buttons (like / share / …) size in dp. `null` = layout default (28). */
    @JvmField var actionButtonSizeDp: Int? = null

    /** Center play button size in dp. `null` = layout default (~46). */
    @JvmField var playButtonSizeDp: Int? = null

    @JvmField @ColorInt var seekBarProgressColor: Int? = null
    @JvmField @ColorInt var seekBarTrackColor: Int? = null

    @JvmStatic
    fun isPlayButtonEnabled(): Boolean = showPlayButton != false

    @JvmStatic
    fun isSeekBarEnabled(): Boolean = showSeekBar != false

    @JvmStatic
    fun isPreloadImageEnabled(): Boolean = showPreloadImage != false

    @JvmStatic
    @DrawableRes
    fun resolvePreloadImageResId(): Int {
        if (!isPreloadImageEnabled()) return 0
        val custom = preloadImageResId
        return if (custom != null && custom != 0) custom else R.drawable.bg_shorts_stub
    }

    @JvmStatic
    fun applyToVideoItem(root: View) {
        applyImageButton(root, R.id.btnLike, showLikeButton, likeButtonIconResId, actionButtonSizeDp)
        applyImageButton(root, R.id.btnDisLike, showDislikeButton, dislikeButtonIconResId, actionButtonSizeDp)
        applyImageButton(root, R.id.btnComment, showCommentButton, commentButtonIconResId, actionButtonSizeDp)
        applyImageButton(root, R.id.btnShare, showShareButton, shareButtonIconResId, actionButtonSizeDp)
        applyImageButton(root, R.id.btnOffline, showOfflineButton, offlineButtonIconResId, actionButtonSizeDp)
        applyImageButton(
            root,
            R.id.btnShowSavedVideos,
            showSavedVideosButton,
            savedVideosButtonIconResId,
            actionButtonSizeDp,
        )

        root.findViewById<android.widget.ImageView>(R.id.thumbnailView)?.let { thumb ->
            val preloadRes = resolvePreloadImageResId()
            if (preloadRes == 0) {
                thumb.setImageDrawable(null)
            } else {
                thumb.setImageResource(preloadRes)
            }
        }
    }

    /**
     * Applies play / scrub visibility and icons on the PlayerView control layout
     * (`shorts_player_control_view`).
     */
    @JvmStatic
    fun applyToPlayerControls(playerControlsRoot: View) {
        applyImageButton(
            playerControlsRoot,
            R.id.btnPlay,
            showPlayButton,
            playButtonIconResId,
            playButtonSizeDp,
        )

        val seekEnabled = isSeekBarEnabled()
        playerControlsRoot.findViewById<View>(R.id.seekBar)?.let { bar ->
            if (!seekEnabled) {
                bar.visibility = View.GONE
            }
        }
        playerControlsRoot.findViewById<View>(R.id.seekBarHitArea)?.let { hit ->
            hit.visibility = if (seekEnabled) View.VISIBLE else View.GONE
            hit.isClickable = seekEnabled
            hit.isEnabled = seekEnabled
        }
        if (!seekEnabled) {
            playerControlsRoot.findViewById<View>(R.id.scrubTimeText)?.visibility = View.GONE
        }
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
        return if (configured != null && configured != 0) configured else R.drawable.shorts_ic_save
    }

    private fun applyImageButton(
        root: View,
        viewId: Int,
        show: Boolean?,
        @DrawableRes iconResId: Int?,
        sizeDp: Int? = null,
    ) {
        val btn = root.findViewById<ImageButton>(viewId) ?: return
        show?.let { btn.visibility = if (it) View.VISIBLE else View.GONE }
        if (iconResId != null) {
            if (iconResId == 0) btn.setImageDrawable(null) else btn.setImageResource(iconResId)
        }
        applySizeDp(btn, sizeDp)
    }

    private fun applySizeDp(view: View, sizeDp: Int?) {
        if (sizeDp == null || sizeDp <= 0) return
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp.toFloat(),
            view.resources.displayMetrics,
        ).toInt()
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(px, px)
        lp.width = px
        lp.height = px
        view.layoutParams = lp
    }

    private fun tintDrawable(drawable: Drawable?, @ColorInt color: Int) {
        if (drawable == null) return
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrapped, color)
    }
}
